package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.KeycloakSession;

public class TenantResource extends AbstractAdminResource<TenantAdminAuth> {

    private final TenantModel tenant;

    public TenantResource(KeycloakSession session, TenantModel tenant) {
        super(session);
        this.tenant = tenant;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getTenant", summary = "Get tenant")
    public TenantRepresentation getTenant() {
        return ModelMapper.toRepresentation(tenant);
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
        return new TenantInvitationsResource(session, tenant);
    }

    @Path("memberships")
    public TenantMembershipsResource memberships() {
        return new TenantMembershipsResource(session, tenant);
    }
}
