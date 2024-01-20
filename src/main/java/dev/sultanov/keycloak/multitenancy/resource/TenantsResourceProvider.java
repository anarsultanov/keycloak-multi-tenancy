package dev.sultanov.keycloak.multitenancy.resource;

import org.keycloak.http.HttpRequest;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class TenantsResourceProvider implements RealmResourceProvider {

    protected final KeycloakSession session;

    public TenantsResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        HttpRequest request = session.getContext().getHttpRequest();
        if (request != null && "OPTIONS".equals(request.getHttpMethod())) {
            return new CorsResource(request);
        } else {
            TenantsResource resource = new TenantsResource(session);
            resource.setup();
            return resource;
        }
    }

    @Override
    public void close() {

    }
}
