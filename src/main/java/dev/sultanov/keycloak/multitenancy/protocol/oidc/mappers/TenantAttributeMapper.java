package dev.sultanov.keycloak.multitenancy.protocol.oidc.mappers;

import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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
import org.keycloak.representations.IDToken;
import dev.sultanov.keycloak.multitenancy.util.Constants;

public class TenantAttributeMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    private static final String PROVIDER_ID = "oidc-tenant-attribute-mapper";
    private static final String TENANT_ATTRIBUTE_NAME = "tenant.attribute.name";
    private static final String MULTIVALUED = "multivalued";

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    static {
        OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties);

        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties, TenantAttributeMapper.class);

        ProviderConfigProperty attributeNameProperty = new ProviderConfigProperty();
        attributeNameProperty.setName(TENANT_ATTRIBUTE_NAME);
        attributeNameProperty.setLabel("Tenant Attribute Name");
        attributeNameProperty.setType(ProviderConfigProperty.STRING_TYPE);
        attributeNameProperty.setHelpText("Name of the tenant attribute to map");
        configProperties.add(attributeNameProperty);

        ProviderConfigProperty multivaluedProperty = new ProviderConfigProperty();
        multivaluedProperty.setName(MULTIVALUED);
        multivaluedProperty.setLabel("Multivalued");
        multivaluedProperty.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        multivaluedProperty.setDefaultValue("true");
        multivaluedProperty.setHelpText("Indicates if the attribute contains multiple values");
        configProperties.add(multivaluedProperty);
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getDisplayType() {
        return "Tenant Attribute";
    }

    @Override
    public String getHelpText() {
        return "Maps a tenant attribute to a token claim.";
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
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel, UserSessionModel userSession,
            KeycloakSession keycloakSession, ClientSessionContext clientSessionCtx) {

        String activeTenantId = userSession.getNote(Constants.ACTIVE_TENANT_ID_SESSION_NOTE);
        if (activeTenantId == null) {
            return;
        }

        String attributeName = mappingModel.getConfig().get(TENANT_ATTRIBUTE_NAME);
        if (attributeName == null) {
            return;
        }

        boolean isMultivalued = Boolean.parseBoolean(mappingModel.getConfig().getOrDefault(MULTIVALUED, "true"));
        String claimName = mappingModel.getConfig().get(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME);

        TenantProvider provider = keycloakSession.getProvider(TenantProvider.class);
        var tenant = provider.getTenantById(userSession.getRealm(), activeTenantId);

        if (tenant.isPresent()) {
            if (isMultivalued) {
                List<String> values = tenant.get()
                        .getAttributeStream(attributeName)
                        .collect(Collectors.toList());

                if (!values.isEmpty()) {
                    token.getOtherClaims().put(claimName, values);
                }
            } else {
                tenant.get()
                        .getAttributeStream(attributeName)
                        .findFirst()
                        .ifPresent(value -> token.getOtherClaims().put(claimName, value));
            }
        }
    }
}