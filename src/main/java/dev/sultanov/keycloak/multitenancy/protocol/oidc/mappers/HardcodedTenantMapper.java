package dev.sultanov.keycloak.multitenancy.protocol.oidc.mappers;

import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;

public class HardcodedTenantMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    private static final String TENANT_ID_PROPERTY_NAME = "hardcoded_tenant_id";

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = new ArrayList<>(
            List.of(new ProviderConfigProperty(
                    TENANT_ID_PROPERTY_NAME, "Tenant ID", null, ProviderConfigProperty.STRING_TYPE, null, false,
                    true)));

    static {
        OIDCAttributeMapperHelper.addTokenClaimNameConfig(CONFIG_PROPERTIES);
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(CONFIG_PROPERTIES, HardcodedTenantMapper.class);
    }

    public static final String PROVIDER_ID = "oidc-hardcoded-tenant-mapper";

    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Hardcoded tenant";
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getHelpText() {
        return "Maps hardcoded tenant to a token claim";
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel, UserSessionModel userSession,
            KeycloakSession keycloakSession,
            ClientSessionContext clientSessionCtx) {
        var tenantID = mappingModel.getConfig().get(TENANT_ID_PROPERTY_NAME);
        var provider = keycloakSession.getProvider(TenantProvider.class);
        var optionalTenant = provider.getTenantById(userSession.getRealm(), tenantID);

        var claimName = mappingModel.getConfig().get(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME);
        if (claimName != null && optionalTenant.isPresent()) {
            var tenant = optionalTenant.get();
            var user = userSession.getUser();
            var membership = new TenantMembershipModel() {
                @Override
                public String getId() {
                    return null;
                }

                @Override
                public TenantModel getTenant() {
                    return tenant;
                }

                @Override
                public UserModel getUser() {
                    return user;
                }

                @Override
                public Set<String> getRoles() {
                    return Collections.emptySet();
                }

                @Override
                public void updateRoles(Set<String> roles) {
                    throw new RuntimeException("UNSUPPORTED METHOD");
                }
            };
            token.getOtherClaims().put(claimName, ClaimsFactory.toClaim(membership));
        }
    }
}