package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantMembershipRepresentation;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URLDecoder;
import java.nio.charset.Charset;
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
import org.keycloak.utils.StringUtil;

public class TenantMembershipsResource extends AbstractAdminResource<TenantAdminAuth> {

    private final TenantModel tenant;

    public TenantMembershipsResource(AbstractAdminResource<TenantAdminAuth> parent, TenantModel tenant) {
        super(parent);
        this.tenant = tenant;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listMemberships", summary = "List tenant memberships")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TenantMembershipRepresentation.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden")
    })
    public Stream<TenantMembershipRepresentation> listMemberships(
            @Parameter(description = "Member email") @QueryParam("search") String search,
            @Parameter(description = "Pagination offset") @QueryParam("first") Integer firstResult,
            @Parameter(description = "Maximum results size (defaults to 100)") @QueryParam("max") Integer maxResults) {

        firstResult = firstResult != null ? firstResult : 0;
        maxResults = maxResults != null ? maxResults : Constants.DEFAULT_MAX_RESULTS;

        if (StringUtil.isNotBlank(search)) {
            search = URLDecoder.decode(search, Charset.defaultCharset()).trim().toLowerCase();
            return tenant.getMembershipsStream(search, firstResult, maxResults)
                    .map(ModelMapper::toRepresentation);
        } else {
            return tenant.getMembershipsStream(firstResult, maxResults)
                    .map(ModelMapper::toRepresentation);
        }
    }

    @PATCH
    @Path("{membershipId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateMembership", summary = "Update tenant membership")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "No Content"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "404", description = "Not Found")
    })
    public Response update(@PathParam("membershipId") String membershipId, @RequestBody(required = true) TenantMembershipRepresentation request) {
        var optionalMembership = tenant.getMembershipById(membershipId);
        if (optionalMembership.isEmpty()) {
            throw new NotFoundException("Membership not found");
        }

        optionalMembership.get().updateRoles(request.getRoles());
        adminEvent.operation(OperationType.UPDATE)
                .resourcePath(session.getContext().getUri())
                .representation(ModelMapper.toRepresentation(optionalMembership.get()))
                .success();

        return Response.noContent().build();
    }

    @DELETE
    @Path("{membershipId}")
    @Operation(operationId = "revokeMembership", summary = "Revoke tenant membership")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "No Content"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "404", description = "Not Found")
    })
    public Response revokeMembership(@PathParam("membershipId") String membershipId) {
        var revoked = tenant.revokeMembership(membershipId);
        if (revoked) {
            adminEvent.operation(OperationType.DELETE)
                    .resourcePath(session.getContext().getUri())
                    .success();
            return Response.noContent().build();
        } else {
            throw new NotFoundException(String.format("No membership with id %s", membershipId));
        }
    }
}
