package dev.sultanov.keycloak.multitenancy.models;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderEvent;

public interface Tenant {

    String getId();

    String getName();

    RealmModel getRealm();

    Stream<UserModel> getMembers();

    default boolean isMember(UserModel user) {
        return getMembers().anyMatch(member -> member.getId().equals(user.getId()));
    }

    void grantMembership(UserModel user, Set<String> roles);

    void revokeMembership(UserModel user);

    void grantRole(UserModel user, String role);

    void revokeRole(UserModel user, String role);

    boolean hasRole(UserModel user, String role);

    Stream<TenantInvitation> getInvitations();

    default Stream<TenantInvitation> getInvitationsByEmail(String email) {
        return getInvitations().filter(i -> i.getEmail().equals(email));
    }

    void revokeInvitation(String id);

    void revokeInvitations(String email);

    TenantInvitation addInvitation(String email, UserModel inviter);

    interface TenantEvent extends ProviderEvent {

        Tenant getTenant();

        KeycloakSession getKeycloakSession();

        RealmModel getRealm();
    }

    interface TenantCreatedEvent extends TenantEvent {

    }

    interface TenantDeletedEvent extends TenantEvent {

    }
}
