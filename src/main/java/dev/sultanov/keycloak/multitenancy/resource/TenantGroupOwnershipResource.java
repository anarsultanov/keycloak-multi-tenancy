package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantGroupOwnershipRepresentation;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.Constants;
import org.keycloak.utils.StringUtil;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.stream.Stream;

public class TenantGroupOwnershipResource extends AbstractAdminResource<TenantAdminAuth> {

    private final TenantModel tenant;

    public TenantGroupOwnershipResource(AbstractAdminResource<TenantAdminAuth> parent, TenantModel tenant) {
        super(parent);
        this.tenant = tenant;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listGroupOwnership", summary = "List group ownerships")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TenantGroupOwnershipRepresentation.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden")
    })
    public Stream<TenantGroupOwnershipRepresentation> listGroupOwnership(
            @Parameter(description = "Member email") @QueryParam("search") String search,
            @Parameter(description = "Pagination offset") @QueryParam("first") Integer firstResult,
            @Parameter(description = "Maximum results size (defaults to 100)") @QueryParam("max") Integer maxResults) {

        firstResult = firstResult != null ? firstResult : 0;
        maxResults = maxResults != null ? maxResults : Constants.DEFAULT_MAX_RESULTS;

        if (StringUtil.isNotBlank(search)) {
            search = URLDecoder.decode(search, Charset.defaultCharset()).trim().toLowerCase();
            return tenant.getGroupOwnershipsStream(search, firstResult, maxResults)
                    .map(ModelMapper::toRepresentation);
        } else {
            return tenant.getGroupOwnershipsStream(firstResult, maxResults)
                    .map(ModelMapper::toRepresentation);
        }
    }

    @DELETE
    @Path("{groupOwnershipId}")
    @Operation(operationId = "revokeGroupOwnership", summary = "Revoke tenant group ownership")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "No Content"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "404", description = "Not Found")
    })
    public Response revokeMembership(@PathParam("groupOwnershipId") String groupOwnershipId) {
        var revoked = tenant.revokeMembership(groupOwnershipId);
        if (revoked) {
            adminEvent.operation(OperationType.DELETE)
                    .resourcePath(session.getContext().getUri())
                    .success();
            return Response.noContent().build();
        } else {
            throw new NotFoundException(String.format("No group ownership with id %s", groupOwnershipId));
        }
    }
}
