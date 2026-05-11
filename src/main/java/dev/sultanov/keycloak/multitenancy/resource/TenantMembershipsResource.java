package dev.sultanov.keycloak.multitenancy.resource;

import static dev.sultanov.keycloak.multitenancy.util.Constants.TENANT_ADMIN_ROLE;
import static dev.sultanov.keycloak.multitenancy.util.Constants.TENANT_USER_ROLE;

import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantMembershipRepresentation;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
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
import java.util.HashSet;
import java.util.Set;
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
            @APIResponse(responseCode = "400", description = "Bad Request — attempt to grant admin role to a non-admin"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "404", description = "Not Found")
    })
    public Response update(@PathParam("membershipId") String membershipId, @RequestBody(required = true) TenantMembershipRepresentation request) {
        var optionalMembership = tenant.getMembershipById(membershipId);
        if (optionalMembership.isEmpty()) {
            throw new NotFoundException("Membership not found");
        }

        // Admin role is implicit (creator-only) and cannot be changed via this
        // endpoint. We strip any attempt to add or remove it from the payload
        // and preserve the membership's current admin status.
        TenantMembershipModel membership = optionalMembership.get();
        Set<String> nextRoles = normalizeUpdatableRoles(membership.getRoles(), request.getRoles());
        membership.updateRoles(nextRoles);
        adminEvent.operation(OperationType.UPDATE)
                .resourcePath(session.getContext().getUri())
                .representation(ModelMapper.toRepresentation(membership))
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
            @APIResponse(responseCode = "404", description = "Not Found"),
            @APIResponse(responseCode = "409", description = "Conflict — cannot revoke the last workspace admin")
    })
    public Response revokeMembership(@PathParam("membershipId") String membershipId) {
        var optionalMembership = tenant.getMembershipById(membershipId);
        if (optionalMembership.isEmpty()) {
            throw new NotFoundException(String.format("No membership with id %s", membershipId));
        }

        // Refuse to revoke the last tenant-admin. The only way to remove the
        // sole admin is to delete the workspace itself.
        if (optionalMembership.get().getRoles().contains(TENANT_ADMIN_ROLE)
                && !hasOtherAdmin(membershipId)) {
            throw new ClientErrorException(
                    "Cannot revoke the last workspace admin. Delete the workspace instead.",
                    Response.Status.CONFLICT);
        }

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

    private boolean hasOtherAdmin(String excludedMembershipId) {
        return tenant.getMembershipsStream(0, Integer.MAX_VALUE)
                .filter(m -> !m.getId().equals(excludedMembershipId))
                .anyMatch(m -> m.getRoles().contains(TENANT_ADMIN_ROLE));
    }

    private static Set<String> normalizeUpdatableRoles(Set<String> currentRoles, Set<String> requestedRoles) {
        Set<String> next = requestedRoles == null ? new HashSet<>() : new HashSet<>(requestedRoles);
        boolean wasAdmin = currentRoles != null && currentRoles.contains(TENANT_ADMIN_ROLE);

        if (next.contains(TENANT_ADMIN_ROLE) && !wasAdmin) {
            throw new BadRequestException("Role '" + TENANT_ADMIN_ROLE + "' cannot be granted via this endpoint");
        }

        // Preserve admin status independently of the request: existing admins
        // keep their role, non-admins cannot become admins.
        next.remove(TENANT_ADMIN_ROLE);
        if (wasAdmin) {
            next.add(TENANT_ADMIN_ROLE);
        } else {
            // Non-admins always carry the workspace-member baseline.
            next.add(TENANT_USER_ROLE);
        }
        return next;
    }
}
