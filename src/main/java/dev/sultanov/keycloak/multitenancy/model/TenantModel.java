package dev.sultanov.keycloak.multitenancy.model;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderEvent;

public interface TenantModel {

    String getId();

    String getName();

    RealmModel getRealm();

    /* Membership */

    TenantMembershipModel grantMembership(UserModel user, Set<String> roles);

    Stream<TenantMembershipModel> getMembershipsStream();

    default Optional<TenantMembershipModel> getMembershipById(String membershipId) {
        return getMembershipsStream().filter(membership -> membership.getId().equals(membershipId)).findFirst();
    };

    default boolean hasMembership(UserModel user) {
        return getMembershipsStream().anyMatch(membership -> membership.getUser().getId().equals(user.getId()));
    }

    default Optional<TenantMembershipModel> getMembership(UserModel user) {
        return getMembershipsStream().filter(membership -> membership.getUser().getId().equals(user.getId())).findFirst();
    }

    boolean revokeMembership(String membershipId);

    /* Invitations */
    TenantInvitationModel addInvitation(String email, UserModel inviter, Set<String> roles);

    Stream<TenantInvitationModel> getInvitationsStream();

    default Stream<TenantInvitationModel> getInvitationsByEmail(String email) {
        return getInvitationsStream().filter(i -> i.getEmail().equals(email));
    }

    boolean revokeInvitation(String id);

    void revokeInvitations(String email);

    interface TenantEvent extends ProviderEvent {

        TenantModel getTenant();

        KeycloakSession getKeycloakSession();

        RealmModel getRealm();
    }

    interface TenantCreatedEvent extends TenantEvent {

    }

    interface TenantRemovedEvent extends TenantEvent {

    }
}
