package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.util.Constants;
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
        return tenantModel.getMembershipByUser(getUser()).filter(membership -> membership.getRoles().contains(Constants.TENANT_ADMIN_ROLE)).isPresent();
    }

    boolean isTenantMember(TenantModel tenantModel) {
        return tenantModel.getMembershipByUser(getUser()).isPresent();
    }

    boolean isTenantsManager() {
        return hasAppRole(getRealmManagementClient(), Constants.TENANTS_MANAGEMENT_ROLE);
    }

    private ClientModel getRealmManagementClient() {
        return getRealm().getClientByClientId(org.keycloak.models.Constants.REALM_MANAGEMENT_CLIENT_ID);
    }
}
