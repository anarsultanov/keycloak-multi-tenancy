package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.RealmModel;

public class TenantResource extends AbstractAdminResource<TenantAdminAuth> {

    private final TenantModel tenant;

    public TenantResource(RealmModel realm, TenantModel tenant) {
        super(realm);
        this.tenant = tenant;
    }

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTenant() {
        return Response.ok().entity(ModelMapper.toRepresentation(tenant)).build();
    }

    @DELETE
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteTenant() {

        if (tenantProvider.deleteTenant(realm, tenant.getId())) {
            adminEvent.operation(OperationType.DELETE)
                    .resourcePath(session.getContext().getUri())
                    .success();
        }
        return Response.status(204).build();
    }

    @Path("invitations")
    public TenantInvitationsResource invitations() {
        TenantInvitationsResource resource = new TenantInvitationsResource(realm, tenant);
        ResteasyProviderFactory.getInstance().injectProperties(resource);
        resource.setup();
        return resource;
    }

    @Path("memberships")
    public TenantMembershipsResource memberships() {
        TenantMembershipsResource resource = new TenantMembershipsResource(realm, tenant);
        ResteasyProviderFactory.getInstance().injectProperties(resource);
        resource.setup();
        return resource;
    }
}
