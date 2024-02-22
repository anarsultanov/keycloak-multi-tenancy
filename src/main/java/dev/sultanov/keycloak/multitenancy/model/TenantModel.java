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

    void setName(String name);

    RealmModel getRealm();

    /* Membership */

    TenantMembershipModel grantMembership(UserModel user, Set<String> roles);

    Stream<TenantMembershipModel> getMembershipsStream(Integer firstResult, Integer maxResults);

    Stream<TenantMembershipModel> getMembershipsStream(String email, Integer firstResult, Integer maxResults);

    Optional<TenantMembershipModel> getMembershipById(String membershipId);

    Optional<TenantMembershipModel> getMembershipByUser(UserModel user);

    default boolean hasMembership(UserModel user) {
        return getMembershipByUser(user).isPresent();
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
