package dev.sultanov.keycloak.multitenancy.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.lang3.ObjectUtils;
import org.jboss.logging.Logger;
import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.AuthenticationProcessor;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.*;
import org.keycloak.protocol.oidc.OIDCAdvancedConfigWrapper;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.TokenManager.AccessTokenResponseBuilder;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.services.Urls;
import org.keycloak.services.managers.AuthenticationSessionManager;
import org.keycloak.services.util.MtlsHoKTokenUtil;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;

import java.util.*;
import java.util.stream.Collectors;

public class TokenManager {

    private final KeycloakSession session;
    private final AccessToken accessToken;
    private final RealmModel realm;
    private final ClientModel targetClient;
    private final OIDCAdvancedConfigWrapper targetClientConfig;
    private final UserModel user;
    private static final Logger logger = Logger.getLogger(TokenManager.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String ALL_TENANTS_ATTRIBUTE = "all_tenants";

    public TokenManager(KeycloakSession session, AccessToken accessToken, RealmModel realm, UserModel user) {
        this.accessToken = accessToken;
        this.realm = realm;
        this.user = user;
        this.session = session;
        this.targetClient = session.getProvider(ClientProvider.class).getClientByClientId(realm, accessToken.getIssuedFor());
        if (this.targetClient == null) {
            throw new IllegalStateException("Client not found for clientId: " + accessToken.getIssuedFor());
        }
        this.targetClientConfig = OIDCAdvancedConfigWrapper.fromClientModel(targetClient);
        this.session.getContext().setClient(targetClient);
    }

    public AccessTokenResponse generateTokens() {
        AuthenticationSessionModel authSession = getAuthSession(getScopeIds());

        EventBuilder event = new EventBuilder(realm, session, session.getContext().getConnection());
        ClientSessionContext clientSessionCtx = AuthenticationProcessor.attachSession(authSession, null, session, realm, session.getContext().getConnection(), event);
        UserSessionModel userSession = clientSessionCtx.getClientSession().getUserSession();

        org.keycloak.protocol.oidc.TokenManager tokenManager = new org.keycloak.protocol.oidc.TokenManager();
        AccessTokenResponseBuilder responseBuilder = tokenManager
                .responseBuilder(realm, targetClient, event, session, userSession, clientSessionCtx)
                .generateAccessToken();

        responseBuilder.getAccessToken().audience(accessToken.getAudience());
        responseBuilder.getAccessToken().setAllowedOrigins(accessToken.getAllowedOrigins());

        // Add all_tenants claim
        List<Map<String, Object>> allTenants = getAllTenants(userSession, session);
        if (!allTenants.isEmpty()) {
            responseBuilder.getAccessToken().getOtherClaims().put("all_tenants", allTenants);
            logger.infof("Added all_tenants claim to access token for user %s: %s", userSession.getUser().getId(), allTenants);
        } else {
            logger.warnf("No tenants found for all_tenants claim for user: %s", userSession.getUser().getId());
        }

        // Add active_tenant claim
        Map<String, Object> activeTenant = getActiveTenant(userSession, session);
        if (ObjectUtils.isNotEmpty(activeTenant)) {
            responseBuilder.getAccessToken().getOtherClaims().put("active_tenant", activeTenant);
            logger.infof("Added active_tenant claim to access token for user %s: %s", userSession.getUser().getId(), activeTenant);
        } else {
            logger.warnf("No active tenant found for user: %s", userSession.getUser().getId());
        }

        boolean useRefreshToken = targetClientConfig.isUseRefreshToken();
        if (useRefreshToken) {
            responseBuilder.generateRefreshToken();
        }

        String scopeParam = clientSessionCtx.getClientSession().getNote(OAuth2Constants.SCOPE);
        if (org.keycloak.util.TokenUtil.isOIDCRequest(scopeParam)) {
            responseBuilder.generateIDToken();
            if (ObjectUtils.isNotEmpty(responseBuilder.getIdToken()) && ObjectUtils.isNotEmpty(activeTenant)) {
                responseBuilder.getIdToken().getOtherClaims().put("active_tenant", activeTenant);
                responseBuilder.getIdToken().getOtherClaims().put("all_tenants", allTenants);
                logger.infof("Added active_tenant and all_tenants claims to ID token for user %s: %s", userSession.getUser().getId(), activeTenant);
            }
            responseBuilder.generateAccessTokenHash();
        }

        checkAndBindMtlsHoKToken(event, responseBuilder, useRefreshToken);

        return responseBuilder.build();
    }

    private List<Map<String, Object>> getAllTenants(UserSessionModel userSession, KeycloakSession session) {
        TenantProvider tenantProvider = session.getProvider(TenantProvider.class);
        if (ObjectUtils.isEmpty(tenantProvider)) {
            logger.error("TenantProvider not available");
            return Collections.emptyList();
        }

        List<Map<String, Object>> allTenants = new ArrayList<>();
        tenantProvider.getUserTenantsStream(session.getContext().getRealm(), userSession.getUser())
                .forEach(tenant -> {
                    Map<String, Object> tenantData = new HashMap<>();
                    tenantData.put("tenant_id", tenant.getId());
                    tenantData.put("tenant_name", ObjectUtils.defaultIfNull(tenant.getName(), "Unknown"));
                    tenantData.put("attributes", tenant.getAttributes());
                    logger.debugf("Attributes for tenant %s: %s", tenant.getId(), tenant.getAttributes());
                    allTenants.add(tenantData);
                });

        // Update all_tenants attribute on user only if necessary
        try {
            String newAllTenantsJson = mapper.writeValueAsString(allTenants);
            String existingAllTenantsJson = userSession.getUser().getFirstAttribute(ALL_TENANTS_ATTRIBUTE);
            if (!Objects.equals(newAllTenantsJson, existingAllTenantsJson)) {
                userSession.getUser().setSingleAttribute(ALL_TENANTS_ATTRIBUTE, newAllTenantsJson);
                logger.infof("Updated all_tenants attribute for user %s: %s", userSession.getUser().getId(), newAllTenantsJson);
            } else {
                logger.debugf("No update needed for all_tenants attribute for user %s", userSession.getUser().getId());
            }
        } catch (Exception e) {
            logger.errorf("Failed to update all_tenants attribute for user %s: %s", userSession.getUser().getId(), e.getMessage());
        }

        return allTenants;
    }

    private Map<String, Object> getActiveTenant(UserSessionModel userSession, KeycloakSession session) {
        String tenantId = userSession.getUser().getFirstAttribute("active_tenant");
        if (ObjectUtils.isEmpty(tenantId)) {
            logger.warnf("No active_tenant attribute found for user: %s", userSession.getUser().getId());
            return null;
        }
        logger.infof("Fetched active_tenant: %s for user: %s", tenantId, userSession.getUser().getId());

        TenantProvider tenantProvider = session.getProvider(TenantProvider.class);
        if (ObjectUtils.isEmpty(tenantProvider)) {
            logger.error("TenantProvider not available");
            return null;
        }

        TenantModel tenant = tenantProvider.getUserTenantsStream(session.getContext().getRealm(), userSession.getUser())
                .filter(t -> t.getId().equals(tenantId))
                .findFirst()
                .orElse(null);

        if (ObjectUtils.isEmpty(tenant)) {
            logger.warnf("No tenant found for tenantId %s and user %s", tenantId, userSession.getUser().getId());
            return null;
        }

        Map<String, Object> activeTenant = new HashMap<>();
        activeTenant.put("tenant_id", tenantId);
        activeTenant.put("tenant_name", ObjectUtils.defaultIfNull(tenant.getName(), "Unknown"));
        activeTenant.put("attributes", tenant.getAttributes());
        logger.debugf("Attributes for active tenant %s: %s", tenantId, tenant.getAttributes());

        return activeTenant;
    }

    private void checkAndBindMtlsHoKToken(EventBuilder event, AccessTokenResponseBuilder responseBuilder, boolean useRefreshToken) {
        if (targetClientConfig.isUseMtlsHokToken()) {
            AccessToken.Confirmation confirmation = MtlsHoKTokenUtil.bindTokenWithClientCertificate(session.getContext().getHttpRequest(), session);
            if (ObjectUtils.isEmpty(confirmation)) {
                event.error(Errors.INVALID_REQUEST);
                throw new NotFoundException("Client Certification missing for MTLS HoK Token Binding");
            }
            responseBuilder.getAccessToken().setConfirmation(confirmation);
            if (useRefreshToken) {
                responseBuilder.getRefreshToken().setConfirmation(confirmation);
            }
        }
    }

    private Set<String> getScopeIds() {
        Map<String, ClientScopeModel> defaultClientScopes = targetClient.getClientScopes(true);
        Map<String, ClientScopeModel> optionalClientScopes = targetClient.getClientScopes(false);
        Set<String> clientScopeIds = defaultClientScopes.values().stream()
                .map(ClientScopeModel::getId)
                .collect(Collectors.toSet());

        Set<String> accessTokenScopes = Set.of(accessToken.getScope().split(" "));
        optionalClientScopes.values().stream()
                .filter(cs -> accessTokenScopes.contains(cs.getName()))
                .map(ClientScopeModel::getId)
                .forEach(clientScopeIds::add);

        return clientScopeIds;
    }

    private AuthenticationSessionModel getAuthSession(Set<String> clientScopeIds) {
        RootAuthenticationSessionModel rootAuthSession = new AuthenticationSessionManager(session)
                .createAuthenticationSession(realm, false);
        AuthenticationSessionModel authSession = rootAuthSession.createAuthenticationSession(targetClient);

        authSession.setAuthenticatedUser(user);
        authSession.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        authSession.setClientScopes(clientScopeIds);
        authSession.setClientNote(OIDCLoginProtocol.ISSUER, Urls.realmIssuer(session.getContext().getUri().getBaseUri(), realm.getName()));

        return authSession;
    }
}