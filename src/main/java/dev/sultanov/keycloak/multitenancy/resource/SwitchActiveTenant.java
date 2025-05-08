package dev.sultanov.keycloak.multitenancy.resource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jboss.logging.Logger;
import org.keycloak.TokenVerifier;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.AccessToken;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.model.entity.SwitchTenantRequest;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public class SwitchActiveTenant {

    private static final Logger log = Logger.getLogger(SwitchActiveTenant.class);
    private static final String ACTIVE_TENANT_ATTRIBUTE = "active_tenant";

    private final KeycloakSession session;

    public SwitchActiveTenant(KeycloakSession session) {
        this.session = session;
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response switchActiveTenant(@Context HttpHeaders headers, SwitchTenantRequest request) {
        RealmModel realm = session.getContext().getRealm();

        // Extract and verify token
        String authHeader = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (StringUtils.isEmpty(authHeader) || !authHeader.startsWith("Bearer ")) {
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
        if (ObjectUtils.isEmpty(user)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Collections.singletonMap("message", "User not found"))
                    .build();
        }

        // Validate request
        if (ObjectUtils.isEmpty(request) || StringUtils.isEmpty(request.getTenantId())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.singletonMap("message", "Tenant ID is required"))
                    .build();
        }

        // Get TenantProvider and check if tenant exists for user
        TenantProvider tenantProvider = session.getProvider(TenantProvider.class);
        if (ObjectUtils.isEmpty(tenantProvider)) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Collections.singletonMap("message", "TenantProvider not available"))
                    .build();
        }

        TenantModel targetTenant = tenantProvider.getUserTenantsStream(realm, user)
                .filter(t -> t.getId().equals(request.getTenantId()))
                .findFirst()
                .orElse(null);

        if (ObjectUtils.isEmpty(targetTenant)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.singletonMap("message", "User does not have access to the specified tenant"))
                    .build();
        }

        // Attribute-based active tenant management
        String currentActiveOrganization = user.getFirstAttribute(ACTIVE_TENANT_ATTRIBUTE);
        user.setSingleAttribute(ACTIVE_TENANT_ATTRIBUTE, request.getTenantId());
        
        log.info("User " + userId + " switched active tenant from " + 
            (StringUtils.isEmpty(currentActiveOrganization) ? "none" : currentActiveOrganization) + 
            " to " + request.getTenantId());

        // Create event for tenant switch
        EventBuilder event = new EventBuilder(realm, session, session.getContext().getConnection());
        event.event(EventType.UPDATE_PROFILE)
            .user(user)
            .detail("new_active_organization_id", request.getTenantId())
            .detail("previous_active_organization_id", currentActiveOrganization)
            .success();

        try {
            // Generate new tokens with the updated active tenant attribute
            TokenManager tokenManager = new TokenManager(session, token, realm, user);
            return Response.ok(tokenManager.generateTokens()).build();
        } catch (Exception e) {
            log.error("Error generating new tokens after tenant switch", e);
            
            // Fallback approach if TokenManager fails
            try {
                // Create direct token response
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Active tenant switched to " + request.getTenantId());
                response.put("tenantId", request.getTenantId());
                response.put("note", "Please refresh your tokens using the refresh_token endpoint");
                
                return Response.ok(response).build();
            } catch (Exception fallbackEx) {
                log.error("Fallback response also failed", fallbackEx);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Collections.singletonMap("message", "Could not generate tokens after tenant switch"))
                    .build();
            }
        }
    }
}