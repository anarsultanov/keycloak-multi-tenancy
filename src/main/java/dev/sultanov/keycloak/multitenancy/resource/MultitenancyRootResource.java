package dev.sultanov.keycloak.multitenancy.resource;

import org.keycloak.models.KeycloakSession;

import jakarta.ws.rs.Path;

@Path("/")
public class MultitenancyRootResource {

    private final KeycloakSession session;

    public MultitenancyRootResource(KeycloakSession session) {
        this.session = session;
    }

    @Path("switch")
    public Object switchTenant() {
        return new SwitchActiveTenant(session);
    }

    @Path("user-tenants")
    public Object userTenants() {
        return new GetUserTenants(session);
    }

    // You can keep expanding this with other tenant sub-resources
}
