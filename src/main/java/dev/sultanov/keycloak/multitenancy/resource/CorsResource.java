package dev.sultanov.keycloak.multitenancy.resource;

import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resources.Cors;

public class CorsResource {

    private final KeycloakSession session;
    private final HttpRequest request;

    public CorsResource(KeycloakSession session, HttpRequest request) {
        this.session = session;
        this.request = request;
    }

    public static final String[] METHODS = {
            "GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
    };

    @OPTIONS
    @Path("{any:.*}")
    public Response preflight() {
        return Cors.add(request, Response.ok()).auth().allowedMethods(METHODS).preflight().build();
    }
}
