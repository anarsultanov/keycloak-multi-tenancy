package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.model.entity.SwitchTenantRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Collections;

import org.jboss.logging.Logger;
import org.keycloak.TokenVerifier;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.AccessToken;

public class SwitchActiveTenant {

    private static final Logger log = Logger.getLogger(SwitchActiveTenant.class);
    private static final String ACTIVE_TENANT_ATTRIBUTE = "active_tenant_id";

    private final KeycloakSession session;

    public SwitchActiveTenant(KeycloakSession session) {
        this.session = session;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response switchActiveTenant(@Context HttpHeaders headers,
                                       SwitchTenantRequest request) {
        RealmModel realm = session.getContext().getRealm();

        // Extract and verify token
        String authHeader = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Collections.singletonMap("message", "Missing or invalid Authorization header"))
                    .build();
        }

        String tokenString = authHeader.substring("Bearer ".length());
        AccessToken token;
        try {
            token = TokenVerifier.create(tokenString, AccessToken.class).getToken();
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Collections.singletonMap("message", "Invalid token"))
                    .build();
        }

        // Get user from token
        String userId = token.getSubject();
        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Collections.singletonMap("message", "User not found"))
                    .build();
        }

        // Validate request
        if (request == null || request.getTenantId() == null || request.getTenantId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.singletonMap("message", "Tenant ID is required"))
                    .build();
        }

        // Get TenantProvider and check if tenant exists for user
        TenantProvider tenantProvider = session.getProvider(TenantProvider.class);
        if (tenantProvider == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Collections.singletonMap("message", "TenantProvider not available"))
                    .build();
        }

        TenantModel targetTenant = tenantProvider.getUserTenantsStream(realm, user)
                .filter(t -> t.getId().equals(request.getTenantId()))
                .findFirst()
                .orElse(null);

        if (targetTenant == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.singletonMap("message", "User does not have access to the specified tenant"))
                    .build();
        }

        // Set the new active tenant ID as user attribute
        user.setSingleAttribute(ACTIVE_TENANT_ATTRIBUTE, request.getTenantId());

        return Response.ok(Collections.singletonMap("activeTenantId", request.getTenantId())).build();
    }
}
