package dev.sultanov.keycloak.multitenancy.resource;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resource.RealmResourceProvider;

public class TenantsResourceProvider implements RealmResourceProvider {

    protected final KeycloakSession session;

    public TenantsResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        RealmModel realm = session.getContext().getRealm();
        TenantsResource resource = new TenantsResource(realm);
        ResteasyProviderFactory.getInstance().injectProperties(resource);
        resource.setup();
        return resource;
    }

    @Override
    public void close() {

    }
}
