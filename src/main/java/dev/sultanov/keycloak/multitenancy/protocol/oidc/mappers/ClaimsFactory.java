package dev.sultanov.keycloak.multitenancy.protocol.oidc.mappers;

import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import dev.sultanov.keycloak.multitenancy.util.Constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ClaimsFactory {

    private static final String TENANT_ID_KEY = "tenant_id";
    private static final String TENANT_NAME_KEY = "tenant_name";
    private static final String ROLES_KEY = "roles";
    private static final String PROVIDER_KEY = "provider";

    static Map<String, Object> toClaim(TenantMembershipModel membership) {
        Map<String, Object> claim = new HashMap<>();
        claim.put(TENANT_ID_KEY, membership.getTenant().getId());
        claim.put(TENANT_NAME_KEY, membership.getTenant().getName());
        claim.put(ROLES_KEY, membership.getRoles());
        claim.put(PROVIDER_KEY, Constants.KEYCLOAK_TENANT_PROVIDER_CLAIM);
        return Collections.unmodifiableMap(claim);
    }

    static Map<String, Object> toClaim(String tenantId, String tenantProvider, List<String> roles) {
        Map<String, Object> claim = new HashMap<>();
        claim.put(TENANT_ID_KEY, tenantId);
        claim.put(TENANT_NAME_KEY, "");
        claim.put(ROLES_KEY, new HashSet<String>(roles));
        claim.put(PROVIDER_KEY, tenantProvider);
        return Collections.unmodifiableMap(claim);
    }

    private ClaimsFactory() {
        throw new AssertionError();
    }
}
