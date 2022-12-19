package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.constants.TenantRole;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.resources.admin.AdminAuth;

public class TenantAdminAuth extends AdminAuth {

    public TenantAdminAuth(RealmModel realm, AccessToken token, UserModel user, ClientModel client) {
        super(realm, token, user, client);
    }

    boolean isTenantAdmin(TenantModel tenantModel) {
        return tenantModel.getMembership(getUser()).filter(membership -> membership.getRoles().contains(TenantRole.TENANT_ADMIN)).isPresent();
    }
}
