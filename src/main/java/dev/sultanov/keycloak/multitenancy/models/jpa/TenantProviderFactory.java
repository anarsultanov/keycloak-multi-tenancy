package dev.sultanov.keycloak.multitenancy.models.jpa;

import javax.persistence.EntityManager;
import org.keycloak.Config.Scope;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderFactory;

public class TenantProviderFactory implements ProviderFactory<TenantProvider> {

    public static final String ID = "tenant-jpa-provider";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public TenantProvider create(KeycloakSession session) {
        EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        return new TenantProvider(session, em);
    }

    @Override
    public void init(Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
