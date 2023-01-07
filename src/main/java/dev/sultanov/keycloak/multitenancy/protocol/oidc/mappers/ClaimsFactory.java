package dev.sultanov.keycloak.multitenancy.protocol.oidc.mappers;

import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ClaimsFactory {

    private static final String TENANT_ID_KEY = "tenant_id";
    private static final String TENANT_NAME_KEY = "tenant_name";
    private static final String ROLES_KEY = "roles";

    static Map<String, Object> toClaim(TenantMembershipModel membership) {
        Map<String, Object> claim = new HashMap<>();
        claim.put(TENANT_ID_KEY, membership.getTenant().getId());
        claim.put(TENANT_NAME_KEY, membership.getTenant().getName());
        claim.put(ROLES_KEY, membership.getRoles());
        return Collections.unmodifiableMap(claim);
    }

    private ClaimsFactory() {
        throw new AssertionError();
    }
}
