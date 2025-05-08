package dev.sultanov.keycloak.multitenancy.protocol.oidc.mappers;

import org.keycloak.Config.Scope;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.ProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderFactory;
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

public class ActiveTenantMapperFactory implements ProviderFactory<ProtocolMapper>, OIDCAccessTokenMapper {

    public static final String PROVIDER_ID = "active-tenant-mapper-factory";
    private static final String ACTIVE_TENANT_ATTRIBUTE = "active_tenant_id";
    private static final Logger logger = Logger.getLogger(ActiveTenantMapperFactory.class);

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public ProtocolMapper create(KeycloakSession session) {
        return new ActiveTenantProtocolMapper();
    }

    public String getDisplayType() {
        return "Active Tenant Mapper";
    }

    public String getDisplayCategory() {
        return "Token mapper";
    }

    public String getHelpText() {
        return "Maps user's active tenant information to the token";
    }

    public List<ProviderConfigProperty> getConfigProperties() {
        return new ArrayList<>();
    }

    public void postInit(KeycloakSession session) {
        RealmModel realm = session.realms().getRealmsStream().findFirst().orElse(null);
        if (realm != null) {
            for (ClientModel client : realm.getClientsStream().toList()) {
                boolean mapperExists = client.getProtocolMappersStream()
                        .anyMatch(mapper -> mapper.getProtocolMapper().equals(ActiveTenantProtocolMapper.PROVIDER_ID));
                if (!mapperExists) {
                    ProtocolMapperModel mapperModel = ActiveTenantProtocolMapper.createClaimMapper();
                    client.addProtocolMapper(mapperModel);
                    logger.info("Auto-registered Active Tenant Mapper for client: " + client.getClientId());
                }
            }
        } else {
            logger.warn("No realms found for auto-registering Active Tenant Mapper");
        }
    }

    @Override
    public void close() {
        // Nothing to do here
    }

    @Override
    public AccessToken transformAccessToken(AccessToken token, ProtocolMapperModel mappingModel,
                                            KeycloakSession session, UserSessionModel userSession, 
                                            ClientSessionContext clientSessionCtx) {
        String claimName = mappingModel.getConfig().getOrDefault("claim.name", "active_tenant");
        String tenantId = userSession.getUser().getFirstAttribute(ACTIVE_TENANT_ATTRIBUTE);
        if (tenantId == null || tenantId.isEmpty()) {
            logger.warn("No active_tenant_id attribute found for user: " + userSession.getUser().getId());
            return token;
        }

        TenantProvider tenantProvider = session.getProvider(TenantProvider.class);
        if (tenantProvider == null) {
            logger.error("TenantProvider not available");
            return token;
        }

        TenantModel tenant = tenantProvider.getUserTenantsStream(session.getContext().getRealm(), userSession.getUser())
                .filter(t -> t.getId().equals(tenantId))
                .findFirst()
                .orElse(null);

        if (tenant == null) {
            logger.warn("No tenant found for tenantId: " + tenantId + " and user: " + userSession.getUser().getId());
            return token;
        }

        Map<String, Object> activeTenant = new HashMap<>();
        activeTenant.put("tenant_id", tenantId);
        activeTenant.put("tenant_name", getTenantNameFromAllTenants(userSession, tenantId));

        List<String> roles = getRolesFromAllTenants(userSession, tenantId);
        if (roles == null || roles.isEmpty()) {
            roles = userSession.getUser().getAttributes().getOrDefault("tenant_roles_" + tenantId, new ArrayList<>());
        }
        if (roles.isEmpty()) {
            roles = List.of("admin");
        }
        activeTenant.put("roles", roles);

        token.getOtherClaims().put(claimName, activeTenant);
        logger.info("Added active_tenant claim to access token: " + activeTenant);
        return token;
    }

    private List<String> getRolesFromAllTenants(UserSessionModel userSession, String tenantId) {
        String allTenantsJson = userSession.getUser().getFirstAttribute("all_tenants");
        if (allTenantsJson == null) {
            logger.warn("No all_tenants attribute found for user: " + userSession.getUser().getId());
            return null;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> allTenants = mapper.readValue(allTenantsJson, new TypeReference<List<Map<String, Object>>>(){});
            
            Map<String, Object> activeTenant = allTenants.stream()
                    .filter(tenant -> tenantId.equals(tenant.get("tenant_id")))
                    .findFirst()
                    .orElse(null);

            if (activeTenant != null) {
                Object rolesObj = activeTenant.getOrDefault("roles", new ArrayList<>());
                if (rolesObj instanceof List) {
                    return ((List<?>) rolesObj).stream()
                            .map(Object::toString)
                            .collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse all_tenants for user: " + userSession.getUser().getId(), e);
        }
        return null;
    }

    private String getTenantNameFromAllTenants(UserSessionModel userSession, String tenantId) {
        String allTenantsJson = userSession.getUser().getFirstAttribute("all_tenants");
        if (allTenantsJson == null) {
            logger.warn("No all_tenants attribute found for user: " + userSession.getUser().getId());
            return "Unknown";
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> allTenants = mapper.readValue(allTenantsJson, new TypeReference<List<Map<String, Object>>>(){});
            
            Map<String, Object> activeTenant = allTenants.stream()
                    .filter(tenant -> tenantId.equals(tenant.get("tenant_id")))
                    .findFirst()
                    .orElse(null);

            if (activeTenant != null) {
                return (String) activeTenant.getOrDefault("tenant_name", "Unknown");
            }
        } catch (Exception e) {
            logger.error("Failed to parse all_tenants for user: " + userSession.getUser().getId(), e);
        }
        return "Unknown";
    }

    @Override
    public void init(Scope config) {
        // No initialization needed
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }
}