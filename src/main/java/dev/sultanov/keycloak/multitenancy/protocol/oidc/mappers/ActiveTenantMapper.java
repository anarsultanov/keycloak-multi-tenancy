package dev.sultanov.keycloak.multitenancy.protocol.oidc.mappers;

import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.util.Constants;

import java.util.List;
import java.util.Optional;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.representations.IDToken;

@JBossLog
public class ActiveTenantMapper extends AbstractOIDCProtocolMapper implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    private static final String CFG_USER_ATTRIBUTE_ACTIVE_TENANT_ID = "attribute.active-tenant-id";

    private static final String DEFAULT_TOKEN_CLAIM_NAME = "active_tenant";

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

    static {
        var properties =
                ProviderConfigurationBuilder.create()
                        .property()
                        .name(CFG_USER_ATTRIBUTE_ACTIVE_TENANT_ID)
                        .label("Active Tenant ID User Attribute")
                        .helpText(
                                """
                                        Specifies the name of the user attribute that stores the active tenant ID. \
                                        This value is used to map the active tenant to a token claim. \
                                        It is primarily intended for service accounts, where users cannot interactively select a tenant. \
                                        If the active tenant is not set in the session note, this user attribute acts as a fallback. \
                                        Note: If the configuration is not set, the active tenant is exclusively determined by the session note (if available).
                                        """)
                        .type(ProviderConfigProperty.USER_PROFILE_ATTRIBUTE_LIST_TYPE)
                        .add()
                        .build();
        OIDCAttributeMapperHelper.addTokenClaimNameConfig(properties);
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(properties, ActiveTenantMapper.class);
        CONFIG_PROPERTIES = properties;
    }

    public static final String PROVIDER_ID = "oidc-active-tenant-mapper";


    public List<ProviderConfigProperty> getConfigProperties() {
        return List.copyOf(CONFIG_PROPERTIES);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Active tenant";
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getHelpText() {
        return "Maps selected active tenant to a token claim";
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel, UserSessionModel userSession, KeycloakSession keycloakSession,
                            ClientSessionContext clientSessionCtx) {
        var config = mappingModel.getConfig();

        // Check if the active tenant is set in the session note
        var sessionNoteTenantId =
                Optional.ofNullable(userSession.getNote(Constants.ACTIVE_TENANT_ID_SESSION_NOTE));

        // Check if the user attribute for active tenant ID is configured
        var tenantIdAttribute = config.get(CFG_USER_ATTRIBUTE_ACTIVE_TENANT_ID);
        if (tenantIdAttribute == null && sessionNoteTenantId.isEmpty()) {
            log.debug(
                    "No active tenant set in session note and user attribute not configured. Skipping mapping of active tenant claim.");
            return; // No active tenant set, nothing to map
        }

        // Determine the active tenant ID from session note or user attribute
        var userAttributeTenantId =
                Optional.ofNullable(userSession.getUser().getFirstAttribute(tenantIdAttribute));
        var activeTenantId = sessionNoteTenantId.or(() -> userAttributeTenantId);
        if (activeTenantId.isEmpty()) {
            return; // No active tenant set, nothing to map
        }

        var provider = keycloakSession.getProvider(TenantProvider.class);
        provider.getTenantMembershipsStream(userSession.getRealm(), userSession.getUser())
                .filter(membership -> membership.getTenant().getId().equals(activeTenantId.get()))
                .map(ClaimsFactory::toClaim)
                .findFirst()
                .ifPresent(claim -> {
                    var claimName = config.getOrDefault(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME, DEFAULT_TOKEN_CLAIM_NAME);
                    token.getOtherClaims().put(claimName, claim);
                });
    }
}
