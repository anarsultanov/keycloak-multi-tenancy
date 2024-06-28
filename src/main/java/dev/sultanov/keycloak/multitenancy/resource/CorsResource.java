package dev.sultanov.keycloak.multitenancy.resource;

import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.keycloak.http.HttpRequest;
import org.keycloak.services.cors.Cors;

public class CorsResource {

    public static final String[] METHODS = {
            "GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
    };

    @OPTIONS
    @Path("{any:.*}")
    public Response preflight() {
        return Cors.builder().auth().allowedMethods(METHODS).preflight().add(Response.ok());
    }
}
