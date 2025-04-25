package dev.sultanov.keycloak.multitenancy.model;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.Provider;

public interface TenantProvider extends Provider {

    TenantModel createTenant(RealmModel realm, String name, UserModel creator);

    Optional<TenantModel> getTenantById(RealmModel realm, String id);

    Stream<TenantModel> getTenantsStream(RealmModel realm);

    Stream<TenantModel> getTenantsStream(RealmModel realm, String name, Map<String, String> attributes, Integer firstResult, Integer maxResults);

    Stream<TenantModel> getTenantsByAttributeStream(RealmModel realm, String attrName, String attrValue);

    boolean deleteTenant(RealmModel realm, String id);

    Stream<TenantInvitationModel> getTenantInvitationsStream(RealmModel realm, UserModel user);

    Stream<TenantMembershipModel> getTenantMembershipsStream(RealmModel realm, UserModel user);

    void revokeInvitation(String realmId, String userId, String invitationId);
}
