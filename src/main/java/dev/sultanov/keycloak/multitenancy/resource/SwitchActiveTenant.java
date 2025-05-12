package dev.sultanov.keycloak.multitenancy.resource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.models.RoleModel;

import dev.sultanov.keycloak.multitenancy.util.TokenVerificationUtils;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.model.entity.SwitchTenantRequest;
import dev.sultanov.keycloak.multitenancy.util.Constants;
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
    private static final String ROLE_ATTRIBUTE = "role";

    private final KeycloakSession session;

    public SwitchActiveTenant(KeycloakSession session) {
        this.session = session;
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response switchActiveTenant(@Context HttpHeaders headers, SwitchTenantRequest request) {
        TokenVerificationUtils.TokenVerificationResult verificationResult = 
                TokenVerificationUtils.verifyToken(session, headers);
        if (!verificationResult.isSuccess()) {
            return verificationResult.getErrorResponse();
        }

        AccessToken token = verificationResult.getToken();
        UserModel user = verificationResult.getUser();
        RealmModel realm = session.getContext().getRealm();

        if (ObjectUtils.isEmpty(request) || StringUtils.isEmpty(request.getTenantId())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.singletonMap("message", "Tenant ID is required"))
                    .build();
        }

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

        String currentActiveOrganization = user.getFirstAttribute(ACTIVE_TENANT_ATTRIBUTE);
        user.setSingleAttribute(ACTIVE_TENANT_ATTRIBUTE, request.getTenantId());

        // Also store in session note for protocol mapper
        UserSessionModel userSession = session.sessions().getUserSession(realm, token.getId());
        if (userSession != null) {
            userSession.setNote(Constants.ACTIVE_TENANT_ID_SESSION_NOTE, request.getTenantId());
        }

        // Update user roles based on the new active tenant's role attribute
        updateUserRoles(user, targetTenant, realm);

        log.info("User " + user.getId() + " switched active tenant from " +
                (StringUtils.isEmpty(currentActiveOrganization) ? "none" : currentActiveOrganization) +
                " to " + request.getTenantId());

        EventBuilder event = new EventBuilder(realm, session, session.getContext().getConnection());
        event.event(EventType.UPDATE_PROFILE)
                .user(user)
                .detail("new_active_organization_id", request.getTenantId())
                .detail("previous_active_organization_id", currentActiveOrganization)
                .success();

        try {
            TokenManager tokenManager = new TokenManager(session, token, realm, user);
            return Response.ok(tokenManager.generateTokens()).build();
        } catch (Exception e) {
            log.error("Error generating new tokens after tenant switch", e);

            try {
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

    private void updateUserRoles(UserModel user, TenantModel tenant, RealmModel realm) {
        // Get the roles from the tenant's 'role' attribute
        List<String> roleNames = tenant.getAttributeStream(ROLE_ATTRIBUTE)
                .collect(Collectors.toList());

        // Log the roles being assigned
        log.info("Assigning roles " + roleNames + " to user " + user.getId() + " for tenant " + tenant.getId());

        // Remove existing realm roles from user
        user.getRoleMappingsStream()
                .filter(role -> role.getContainer() instanceof RealmModel)
                .forEach(user::deleteRoleMapping);

        // Assign new roles to user based on the tenant's role attribute
        for (String roleName : roleNames) {
            RoleModel role = realm.getRole(roleName);
            if (role != null) {
                user.grantRole(role);
                log.info("Assigned role " + roleName + " to user " + user.getId() + " for tenant " + tenant.getId());
            } else {
                log.warn("Role " + roleName + " not found in realm for tenant " + tenant.getId());
            }
        }

        log.info("Completed role update for user " + user.getId() + " in tenant " + tenant.getId());
    }
}