package dev.sultanov.keycloak.multitenancy.resource;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class MultitenancyResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public MultitenancyResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new TenantController(session); // Your custom REST controller
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
