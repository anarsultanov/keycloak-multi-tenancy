package dev.sultanov.keycloak.multitenancy.resource;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class SwitchTenantResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public SwitchTenantResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new SwitchActiveTenant(session);
    }

    @Override
    public void close() {
        // No resources to close
    }
}