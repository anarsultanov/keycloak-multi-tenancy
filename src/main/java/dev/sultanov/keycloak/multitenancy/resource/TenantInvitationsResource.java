package dev.sultanov.keycloak.multitenancy.resource;

import static org.keycloak.locale.LocaleSelectorProvider.USER_REQUEST_LOCALE;

import dev.sultanov.keycloak.multitenancy.email.EmailRecipient;
import dev.sultanov.keycloak.multitenancy.email.EmailSender;
import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.Constants;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;

public class TenantInvitationsResource extends AbstractAdminResource<TenantAdminAuth> {

    private final TenantModel tenant;

    public TenantInvitationsResource(AbstractAdminResource<TenantAdminAuth> parent, TenantModel tenant) {
        super(parent);
        this.tenant = tenant;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createInvitation", summary = "Create invitation")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Created"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden")
    })
    public Response createInvitation(@RequestBody(required = true) TenantInvitationRepresentation request) {
        String email = request.getEmail();
        if (!isValidEmail(email)) {
            throw new BadRequestException("Invalid email: " + email);
        }
        email = email.toLowerCase();

        if (tenant.getInvitationsByEmail(email).findAny().isPresent()) {
            throw new ClientErrorException(String.format("Invitation for %s already exists.", email), Response.Status.CONFLICT);
        }

        UserModel user = session.users().getUserByEmail(realm, email);
        if (user != null && tenant.hasMembership(user)) {
            throw new ClientErrorException(String.format("%s is already a member of this organization.", email), Response.Status.CONFLICT);
        }

        try {
            TenantInvitationModel invitation = tenant.addInvitation(email, auth.getUser(), request.getRoles());
            TenantInvitationRepresentation representation = ModelMapper.toRepresentation(invitation);

            session.setAttribute(USER_REQUEST_LOCALE, request.getLocale());
            var invitee = Optional.ofNullable(session.users().getUserByEmail(realm, email))
                    .orElse(new EmailRecipient(email));
            EmailSender.sendInvitationEmail(session, invitee, tenant.getName());

            adminEvent.operation(OperationType.CREATE)
                    .resourcePath(session.getContext().getUri(), representation.getId())
                    .representation(representation)
                    .success();

            URI location = session.getContext().getUri().getAbsolutePathBuilder().path(representation.getId()).build();
            return Response.created(location).build();
        } catch (Exception e) {
            throw new InternalServerErrorException(e);
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listInvitations", summary = "List invitations")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TenantInvitationRepresentation.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden")
    })
    public Stream<TenantInvitationRepresentation> listInvitations(
            @Parameter(description = "Invitee email") @QueryParam("search") String searchQuery,
            @Parameter(description = "Pagination offset") @QueryParam("first") Integer firstResult,
            @Parameter(description = "Maximum results size (defaults to 100)") @QueryParam("max") Integer maxResults) {
        Optional<String> search = Optional.ofNullable(searchQuery);
        firstResult = firstResult != null ? firstResult : 0;
        maxResults = maxResults != null ? maxResults : Constants.DEFAULT_MAX_RESULTS;

        return tenant.getInvitationsStream()
                .filter(i -> search.isEmpty() || i.getEmail().contains(search.get()))
                .skip(firstResult)
                .limit(maxResults)
                .map(ModelMapper::toRepresentation);
    }

    @DELETE
    @Operation(operationId = "revokeInvitation", summary = "Revoke invitation")
    @Path("{invitationId}")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "No Content"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "404", description = "Not Found")
    })
    public Response revokeInvitation(@PathParam("invitationId") String invitationId) {
        var revoked = tenant.revokeInvitation(invitationId);
        if (revoked) {
            adminEvent.operation(OperationType.DELETE)
                    .resourcePath(session.getContext().getUri())
                    .success();
            return Response.noContent().build();
        } else {
            throw new NotFoundException(String.format("No invitation with id %s", invitationId));
        }
    }

    private static boolean isValidEmail(String email) {
        if (email != null) {
            try {
                if (email.startsWith("mailto:")) {
                    email = email.substring(7);
                }
                InternetAddress emailAddr = new InternetAddress(email);
                emailAddr.validate();
                return true;
            } catch (AddressException e) {
                // ignore
            }
        }
        return false;
    }
}
