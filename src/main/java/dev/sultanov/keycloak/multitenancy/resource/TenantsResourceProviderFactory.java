package dev.sultanov.keycloak.multitenancy.resource;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class TenantsResourceProviderFactory implements RealmResourceProviderFactory {

    private static final String ID = "tenants";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void close() {
    }

    @Override
    public TenantsResourceProvider create(KeycloakSession session) {
        return new TenantsResourceProvider(session);
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // TODO: Any event to handle?
        // factory.register((ProviderEvent event) -> {});
    }
}
