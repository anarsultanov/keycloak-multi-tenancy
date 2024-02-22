package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.keycloak.events.admin.OperationType;

public class TenantResource extends AbstractAdminResource<TenantAdminAuth> {

    private final TenantModel tenant;

    public TenantResource(AbstractAdminResource<TenantAdminAuth> parent, TenantModel tenant) {
        super(parent);
        this.tenant = tenant;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getTenant", summary = "Get tenant")
    public TenantRepresentation getTenant() {
        return ModelMapper.toRepresentation(tenant);
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateTenant", summary = "Update tenant")
    public Response updateTenant(TenantRepresentation request) {

        tenant.setName(request.getName());

        adminEvent.operation(OperationType.UPDATE)
                .resourcePath(session.getContext().getUri())
                .representation(ModelMapper.toRepresentation(tenant))
                .success();

        return Response.noContent().build();
    }

    @DELETE
    @Operation(operationId = "deleteTenant", summary = "Delete tenant")
    public void deleteTenant() {
        if (tenantProvider.deleteTenant(realm, tenant.getId())) {
            adminEvent.operation(OperationType.DELETE)
                    .resourcePath(session.getContext().getUri())
                    .success();
        }
    }

    @Path("invitations")
    public TenantInvitationsResource invitations() {
        return new TenantInvitationsResource(this, tenant);
    }

    @Path("memberships")
    public TenantMembershipsResource memberships() {
        return new TenantMembershipsResource(this, tenant);
    }
}
