package dev.sultanov.keycloak.multitenancy.protocol.oidc.mappers;

import org.apache.commons.lang3.ObjectUtils;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.AccessToken;
import org.jboss.logging.Logger;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ActiveTenantProtocolMapper extends AbstractOIDCProtocolMapper 
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    public static final String PROVIDER_ID = "oidc-active-tenant-mapper";
    private static final String ACTIVE_TENANT_ATTRIBUTE = "active_tenant";
    private static final String CLAIM_NAME = "active_tenant";
    private static final Logger logger = Logger.getLogger(ActiveTenantProtocolMapper.class);
    private static final ObjectMapper mapper = new ObjectMapper(); // Class-level ObjectMapper

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    static {
        ProviderConfigProperty property = new ProviderConfigProperty();
        property.setName("claim.name");
        property.setLabel("Token Claim Name");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setDefaultValue(CLAIM_NAME);
        property.setHelpText("Name of the claim to insert into the token. Default is 'active_tenant'.");
        configProperties.add(property);
    }

    @Override
    public String getDisplayCategory() {
        return "Token mapper";
    }

    @Override
    public String getDisplayType() {
        return "Active Tenant Mapper";
    }

    @Override
    public String getHelpText() {
        return "Maps user's active tenant information to a token claim";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel, 
                           UserSessionModel userSession, KeycloakSession keycloakSession,
                           ClientSessionContext clientSessionContext) {
        String claimName = mappingModel.getConfig().getOrDefault("claim.name", CLAIM_NAME);
        Map<String, Object> activeTenant = getActiveTenant(userSession, keycloakSession);
        if (activeTenant != null) {
            token.getOtherClaims().put(claimName, activeTenant);
            logger.info("Added active_tenant claim to ID token: " + activeTenant);
        } else {
            logger.warn("Could not add active_tenant claim to ID token: no active tenant found");
        }
    }

    @Override
    public AccessToken transformAccessToken(AccessToken token, ProtocolMapperModel mappingModel,
                                            KeycloakSession session, UserSessionModel userSession, 
                                            ClientSessionContext clientSessionCtx) {
        String claimName = mappingModel.getConfig().getOrDefault("claim.name", CLAIM_NAME);
        Map<String, Object> activeTenant = getActiveTenant(userSession, session);
        if (activeTenant != null) {
            token.getOtherClaims().put(claimName, activeTenant);
            logger.info("Added active_tenant claim to access token: " + activeTenant);
        } else {
            logger.warn("Could not add active_tenant claim to access token: no active tenant found");
        }
        return token;
    }

    private Map<String, Object> getActiveTenant(UserSessionModel userSession, KeycloakSession session) {
        String tenantId = userSession.getUser().getFirstAttribute(ACTIVE_TENANT_ATTRIBUTE);
        logger.info("Fetched active_tenant: " + tenantId + " for user: " + userSession.getUser().getId());
        if (ObjectUtils.isEmpty(tenantId)) {
            logger.warn("No active_tenant attribute found for user: " + userSession.getUser().getId());
            return null;
        }

        // Use TenantProvider to fetch tenant details
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
            logger.warn("No tenant found for tenantId: " + tenantId + " and user: " + userSession.getUser().getId());
            return null;
        }

        // Construct active_tenant object
        Map<String, Object> activeTenant = new HashMap<>();
        activeTenant.put("tenant_id", tenantId);

        // Try all_tenants first, then fallback to TenantModel
        String tenantName = getTenantAttribute(userSession, tenantId, "tenant_name", String.class, "Unknown");
        if ("Unknown".equals(tenantName)) {
            // Fallback to TenantModel (adjust if TenantModel has specific method)
            tenantName = ObjectUtils.defaultIfNull(tenant.getName(), "Unknown");
            logger.info("Using TenantModel fallback for tenant_name: " + tenantName + " for tenantId: " + tenantId);
        }
        activeTenant.put("tenant_name", tenantName);

        // Fetch roles from all_tenants user attribute
        List<String> roles = getTenantAttribute(userSession, tenantId, "roles", List.class, new ArrayList<>());
        if (ObjectUtils.isEmpty(roles)) {
            // Fallback to TenantModel attribute
            roles = ObjectUtils.defaultIfNull(
                    tenant.getFirstAttribute("roles"), 
                    ""
            ).split(",").length > 0 ? List.of(tenant.getFirstAttribute("roles").split(",")) : new ArrayList<>();
            logger.info("Using TenantModel fallback for roles: " + roles + " for tenantId: " + tenantId);
        }
        if (roles.isEmpty()) {
            // Fallback to user attribute
            roles = ObjectUtils.defaultIfNull(
                    userSession.getUser().getAttributes().get("tenant_roles_" + tenantId), 
                    new ArrayList<>()
            );
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

    private <T> T getTenantAttribute(UserSessionModel userSession, String tenantId, String attributeName, 
                                     Class<T> type, T defaultValue) {
        String allTenantsJson = userSession.getUser().getFirstAttribute("all_tenants");
        logger.info("Raw all_tenants attribute for user " + userSession.getUser().getId() + ": " + allTenantsJson);
        if (ObjectUtils.isEmpty(allTenantsJson)) {
            logger.warn("No all_tenants attribute found for user: " + userSession.getUser().getId());
            return defaultValue;
        }

        try {
            List<Map<String, Object>> allTenants = mapper.readValue(allTenantsJson, new TypeReference<List<Map<String, Object>>>(){});
            logger.info("Parsed all_tenants for user " + userSession.getUser().getId() + ": " + allTenants);
            
            Map<String, Object> activeTenant = allTenants.stream()
                    .filter(tenant -> tenantId.equals(tenant.get("tenant_id")))
                    .findFirst()
                    .orElse(null);

            if (ObjectUtils.isEmpty(activeTenant)) {
                logger.warn("No tenant found in all_tenants for tenantId: " + tenantId);
                return defaultValue;
            }

            Object attributeValue = activeTenant.getOrDefault(attributeName, defaultValue);
            if (attributeValue != null && type.isInstance(attributeValue)) {
                if (type == List.class) {
                    // Handle List type specifically for roles
                    return type.cast(((List<?>) attributeValue).stream()
                            .map(Object::toString)
                            .collect(Collectors.toList()));
                }
                return type.cast(attributeValue);
            }
            logger.warn(attributeName + " field is not of expected type for tenantId: " + tenantId);
            return defaultValue;
        } catch (Exception e) {
            logger.error("Failed to parse all_tenants for user: " + userSession.getUser().getId() + ": " + e.getMessage(), e);
            return defaultValue;
        }
    }

    public static ProtocolMapperModel createClaimMapper() {
        ProtocolMapperModel mapper = new ProtocolMapperModel();
        mapper.setName("Active Tenant Mapper");
        mapper.setProtocolMapper(PROVIDER_ID);
        mapper.setProtocol("openid-connect");

        Map<String, String> config = new HashMap<>();
        config.put("claim.name", CLAIM_NAME);
        config.put("id.token.claim", "true");
        config.put("access.token.claim", "true");
        config.put("userinfo.token.claim", "true");
        mapper.setConfig(config);

        return mapper;
    }
}