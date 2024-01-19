package dev.sultanov.keycloak.multitenancy.resource;

import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.keycloak.http.HttpRequest;
import org.keycloak.services.resources.Cors;

public class CorsResource {

    public static final String[] METHODS = {
            "GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
    };

    private final HttpRequest request;

    public CorsResource(HttpRequest request) {
        this.request = request;
    }

    @OPTIONS
    @Path("{any:.*}")
    public Response preflight() {
        return Cors.add(request, Response.ok()).auth().allowedMethods(METHODS).preflight().build();
    }
}
