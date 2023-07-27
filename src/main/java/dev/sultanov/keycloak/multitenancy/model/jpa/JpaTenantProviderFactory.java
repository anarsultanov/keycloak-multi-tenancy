package dev.sultanov.keycloak.multitenancy.model.jpa;

import dev.sultanov.keycloak.multitenancy.model.TenantProviderFactory;
import jakarta.persistence.EntityManager;
import org.keycloak.Config.Scope;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class JpaTenantProviderFactory implements TenantProviderFactory {

    public static final String ID = "jpa-tenant-provider";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JpaTenantProvider create(KeycloakSession session) {
        EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        return new JpaTenantProvider(session, em);
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
