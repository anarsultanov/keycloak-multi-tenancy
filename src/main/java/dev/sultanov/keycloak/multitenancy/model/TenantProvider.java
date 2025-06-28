package dev.sultanov.keycloak.multitenancy.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.Provider;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantMembershipRepresentation;
import dev.sultanov.keycloak.multitenancy.resource.representation.UserMembershipRepresentation;

public interface TenantProvider extends Provider {

	TenantModel createTenant(RealmModel realm, String tenantName, String mobileNumber, String countryCode, String status, UserModel user);

    Optional<TenantModel> getTenantById(RealmModel realm, String id);

    Stream<TenantModel> getTenantsStream(RealmModel realm, String nameOrIdQuery, Map<String, String> attributes, String mobileNumber, String countryCode);

    boolean deleteTenant(RealmModel realm, String id);

    Stream<TenantInvitationModel> getTenantInvitationsStream(RealmModel realm, UserModel user);

    Stream<TenantMembershipModel> getTenantMembershipsStream(RealmModel realm, UserModel user);

    Stream<TenantModel> getUserTenantsStream(RealmModel realm, UserModel user);

    boolean revokeMembership(RealmModel realm, String tenantId, String userId);

	List<UserMembershipRepresentation> listMembershipsByUserId(RealmModel realm, String userId);

}
