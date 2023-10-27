package dev.sultanov.keycloak.multitenancy.authentication.authenticators;

import static dev.sultanov.keycloak.multitenancy.authentication.IdentityProviderTenantsConfig.IDENTITY_PROVIDER_TENANTS;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public class IdpTenantMembershipsCreatingAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "idp-tenant-memberships";

    private static final IdpTenantMembershipsCreatingAuthenticator SINGLETON = new IdpTenantMembershipsCreatingAuthenticator();

    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    public void init(Config.Scope config) {
    }

    public void postInit(KeycloakSessionFactory factory) {
    }

    public void close() {
    }

    public String getId() {
        return PROVIDER_ID;
    }

    public String getReferenceCategory() {
        return "multiTenancy";
    }

    public boolean isConfigurable() {
        return false;
    }

    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    public String getDisplayType() {
        return "Create tenant memberships";
    }

    public String getHelpText() {
        return "Automatically create tenant memberships for users based on Identity Provider configuration property: " + IDENTITY_PROVIDER_TENANTS;
    }


    public List<ProviderConfigProperty> getConfigProperties() {
        return null;
    }

    public boolean isUserSetupAllowed() {
        return false;
    }
}
