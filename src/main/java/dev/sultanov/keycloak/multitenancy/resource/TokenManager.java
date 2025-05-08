package dev.sultanov.keycloak.multitenancy.resource;

import jakarta.ws.rs.NotFoundException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.AuthenticationProcessor;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientProvider;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
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
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.jboss.logging.Logger;

public class TokenManager {

  private final KeycloakSession session;
  private final AccessToken accessToken;
  private final RealmModel realm;
  private final ClientModel targetClient;
  private final OIDCAdvancedConfigWrapper targetClientConfig;
  private final UserModel user;
  private static final Logger logger = Logger.getLogger(TokenManager.class);

  public TokenManager(
      KeycloakSession session, AccessToken accessToken, RealmModel realm, UserModel user) {
    this.accessToken = accessToken;
    this.realm = realm;
    this.targetClient =
        session
            .getProvider(ClientProvider.class)
            .getClientByClientId(realm, accessToken.getIssuedFor());
    this.targetClientConfig = OIDCAdvancedConfigWrapper.fromClientModel(targetClient);
    this.user = user;
    this.session = session;
    this.session.getContext().setClient(targetClient);
  }

  public AccessTokenResponse generateTokens() {
    // Create new authSession with Default + Optional Scope matching old token
    AuthenticationSessionModel authSession = getAuthSession(getScopeIds());

    EventBuilder event =
        new EventBuilder(
            session.getContext().getRealm(), session, session.getContext().getConnection());
    ClientSessionContext clientSessionCtx =
        AuthenticationProcessor.attachSession(
            authSession, null, session, realm, session.getContext().getConnection(), event);
    UserSessionModel userSession = clientSessionCtx.getClientSession().getUserSession();

    // Generate new token
    org.keycloak.protocol.oidc.TokenManager tokenManager =
        new org.keycloak.protocol.oidc.TokenManager();
    AccessTokenResponseBuilder responseBuilder =
        tokenManager
            .responseBuilder(realm, targetClient, event, session, userSession, clientSessionCtx)
            .generateAccessToken();
    // Rewrite audience and allowed origin based on previous token
    responseBuilder.getAccessToken().audience(accessToken.getAudience());
    responseBuilder.getAccessToken().setAllowedOrigins(accessToken.getAllowedOrigins());

    // Add active_tenant claim
    Map<String, Object> activeTenant = getActiveTenant(userSession, session);
    if (activeTenant != null) {
      responseBuilder.getAccessToken().getOtherClaims().put("active_tenant", activeTenant);
      logger.info("Added active_tenant claim to access token: " + activeTenant);
    } else {
      logger.warn("Could not add active_tenant claim to access token: no active tenant found");
    }

    boolean useRefreshToken = targetClientConfig.isUseRefreshToken();
    if (useRefreshToken) {
      responseBuilder.generateRefreshToken();
    }

    String scopeParam = clientSessionCtx.getClientSession().getNote(OAuth2Constants.SCOPE);
    if (org.keycloak.util.TokenUtil.isOIDCRequest(scopeParam)) {
      responseBuilder.generateIDToken();
      // Add active_tenant to ID token if generated
      if (responseBuilder.getIdToken() != null && activeTenant != null) {
        responseBuilder.getIdToken().getOtherClaims().put("active_tenant", activeTenant);
        logger.info("Added active_tenant claim to ID token: " + activeTenant);
      }
      responseBuilder.generateAccessTokenHash();
    }

    checkAndBindMtlsHoKToken(event, responseBuilder, useRefreshToken);

    return responseBuilder.build();
  }

  private Map<String, Object> getActiveTenant(UserSessionModel userSession, KeycloakSession session) {
    String tenantId = userSession.getUser().getFirstAttribute("active_tenant");
    logger.info("Fetched active_tenant: " + tenantId + " for user: " + userSession.getUser().getId());
    if (tenantId == null || tenantId.isEmpty()) {
      logger.warn("No active_tenant attribute found for user: " + userSession.getUser().getId());
      return null;
    }

    // Use TenantProvider to fetch tenant details
    TenantProvider tenantProvider = session.getProvider(TenantProvider.class);
    if (tenantProvider == null) {
      logger.error("TenantProvider not available");
      return null;
    }

    TenantModel tenant = tenantProvider.getUserTenantsStream(session.getContext().getRealm(), userSession.getUser())
        .filter(t -> t.getId().equals(tenantId))
        .findFirst()
        .orElse(null);

    if (tenant == null) {
      logger.warn("No tenant found for tenantId: " + tenantId + " and user: " + userSession.getUser().getId());
      return null;
    }

    // Construct active_tenant object
    Map<String, Object> activeTenant = new HashMap<>();
    activeTenant.put("tenant_id", tenantId);

    // Try all_tenants first, then fallback to TenantModel
    String tenantName = getTenantNameFromAllTenants(userSession, tenantId);
    if ("Unknown".equals(tenantName)) {
      // Fallback to TenantModel (adjust if TenantModel has specific method)
      tenantName = tenant.getName() != null ? tenant.getName() : "Unknown";
      logger.info("Using TenantModel fallback for tenant_name: " + tenantName + " for tenantId: " + tenantId);
    }
    activeTenant.put("tenant_name", tenantName);

    // Fetch roles from all_tenants user attribute
    List<String> roles = getRolesFromAllTenants(userSession, tenantId);
    if (roles == null || roles.isEmpty()) {
      // Fallback to TenantModel attribute
      roles = tenant.getFirstAttribute("roles") != null ? List.of(tenant.getFirstAttribute("roles").split(",")) : new ArrayList<>();
      logger.info("Using TenantModel fallback for roles: " + roles + " for tenantId: " + tenantId);
    }
    if (roles.isEmpty()) {
      // Fallback to user attribute
      roles = userSession.getUser().getAttributes().getOrDefault("tenant_roles_" + tenantId, new ArrayList<>());
      logger.info("Using user attribute fallback tenant_roles_" + tenantId + ": " + roles);
    }
    if (roles.isEmpty()) {
      // Fallback to default
      roles = List.of("admin");
      logger.info("Using default roles: " + roles);
    }
    activeTenant.put("roles", roles);

    return activeTenant;
  }

  private List<String> getRolesFromAllTenants(UserSessionModel userSession, String tenantId) {
    String allTenantsJson = userSession.getUser().getFirstAttribute("all_tenants");
    logger.info("Raw all_tenants attribute for user " + userSession.getUser().getId() + ": " + allTenantsJson);
    if (allTenantsJson == null) {
      logger.warn("No all_tenants attribute found for user: " + userSession.getUser().getId());
      return null;
    }

    try {
      ObjectMapper mapper = new ObjectMapper();
      List<Map<String, Object>> allTenants = mapper.readValue(allTenantsJson, new TypeReference<List<Map<String, Object>>>(){});
      logger.info("Parsed all_tenants for user " + userSession.getUser().getId() + ": " + allTenants);
      
      Map<String, Object> activeTenant = allTenants.stream()
          .filter(tenant -> tenantId.equals(tenant.get("tenant_id")))
          .findFirst()
          .orElse(null);

      if (activeTenant != null) {
        Object rolesObj = activeTenant.getOrDefault("roles", new ArrayList<>());
        if (rolesObj instanceof List) {
          List<String> roles = ((List<?>) rolesObj).stream()
              .map(Object::toString)
              .collect(Collectors.toList());
          logger.info("Roles found for tenantId " + tenantId + ": " + roles);
          return roles;
        }
        logger.warn("Roles field is not a list for tenantId: " + tenantId);
      } else {
        logger.warn("No tenant found in all_tenants for tenantId: " + tenantId);
      }
    } catch (Exception e) {
      logger.error("Failed to parse all_tenants for user: " + userSession.getUser().getId() + ": " + e.getMessage(), e);
    }
    return null;
  }

  private String getTenantNameFromAllTenants(UserSessionModel userSession, String tenantId) {
    String allTenantsJson = userSession.getUser().getFirstAttribute("all_tenants");
    logger.info("Raw all_tenants attribute for user " + userSession.getUser().getId() + ": " + allTenantsJson);
    if (allTenantsJson == null) {
      logger.warn("No all_tenants attribute found for user: " + userSession.getUser().getId());
      return "Unknown";
    }

    try {
      ObjectMapper mapper = new ObjectMapper();
      List<Map<String, Object>> allTenants = mapper.readValue(allTenantsJson, new TypeReference<List<Map<String, Object>>>(){});
      logger.info("Parsed all_tenants for user " + userSession.getUser().getId() + ": " + allTenants);
      
      Map<String, Object> activeTenant = allTenants.stream()
          .filter(tenant -> tenantId.equals(tenant.get("tenant_id")))
          .findFirst()
          .orElse(null);

      if (activeTenant != null) {
        String tenantName = (String) activeTenant.getOrDefault("tenant_name", "Unknown");
        logger.info("Tenant name found for tenantId " + tenantId + ": " + tenantName);
        return tenantName;
      }
      logger.warn("No tenant found in all_tenants for tenantId: " + tenantId);
      return "Unknown";
    } catch (Exception e) {
      logger.error("Failed to parse all_tenants for user: " + userSession.getUser().getId() + ": " + e.getMessage(), e);
      return "Unknown";
    }
  }

  private void checkAndBindMtlsHoKToken(
      EventBuilder event, AccessTokenResponseBuilder responseBuilder, boolean useRefreshToken) {
    if (targetClientConfig.isUseMtlsHokToken()) {
      AccessToken.Confirmation confirmation =
          MtlsHoKTokenUtil.bindTokenWithClientCertificate(
              session.getContext().getHttpRequest(), session);
      if (confirmation != null) {
        responseBuilder.getAccessToken().setConfirmation(confirmation);
        if (useRefreshToken) {
          responseBuilder.getRefreshToken().setConfirmation(confirmation);
        }
      } else {
        event.error(Errors.INVALID_REQUEST);
        throw new NotFoundException("Client Certification missing for MTLS HoK Token Binding");
      }
    }
  }

  private Set<String> getScopeIds() {
    Map<String, ClientScopeModel> defaultClientScopes = targetClient.getClientScopes(true);
    Map<String, ClientScopeModel> optionalClientScopes = targetClient.getClientScopes(false);
    Set<String> clientScopeIds =
        defaultClientScopes.values().stream()
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
    RootAuthenticationSessionModel rootAuthSession =
        new AuthenticationSessionManager(session).createAuthenticationSession(realm, false);
    AuthenticationSessionModel authSession =
        rootAuthSession.createAuthenticationSession(targetClient);

    authSession.setAuthenticatedUser(user);
    authSession.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
    authSession.setClientNote(
        OIDCLoginProtocol.ISSUER,
        Urls.realmIssuer(session.getContext().getUri().getBaseUri(), realm.getName()));
    authSession.setClientNote(OIDCLoginProtocol.SCOPE_PARAM, accessToken.getScope());
    authSession.setClientScopes(clientScopeIds);

    return authSession;
  }
}