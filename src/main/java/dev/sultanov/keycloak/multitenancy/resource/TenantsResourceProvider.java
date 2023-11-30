package dev.sultanov.keycloak.multitenancy.resource;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class TenantsResourceProvider implements RealmResourceProvider {

    protected final KeycloakSession session;

    public TenantsResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new TenantsResource(session);
    }

    @Override
    public void close() {

    }
}
