package dev.sultanov.keycloak.multitenancy.authentication;

import java.util.Arrays;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.utils.StringUtil;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IdentityProviderTenantsConfig {

    public static final String IDENTITY_PROVIDER_TENANTS = "multi-tenancy.tenants";

    boolean tenantsSpecific;
    Set<String> accessibleTenantIds;

    public static IdentityProviderTenantsConfig of(IdentityProviderModel identityProviderModel) {
        var configValue = identityProviderModel.getConfig().get(IDENTITY_PROVIDER_TENANTS);
        if (StringUtil.isBlank(configValue)) {
            return new IdentityProviderTenantsConfig(false, Set.of());
        } else {
            var tenantIds = configValue.split(",");
            return new IdentityProviderTenantsConfig(true, Set.copyOf(Arrays.asList(tenantIds)));
        }
    }
}
