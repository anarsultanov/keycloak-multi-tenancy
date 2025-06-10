package dev.sultanov.keycloak.multitenancy.model;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.Map;
import java.util.List;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderEvent;

public interface TenantModel {

    String getId();

    String getName();

    void setName(String name);

    String getMobileNumber();

    void setMobileNumber(String mobileNumber);

    String getCountryCode(); // Add this method

    void setCountryCode(String countryCode); // Add this method

    String getStatus(); // Add this method

    void setStatus(String status); // Add this method

    RealmModel getRealm();

    /* Attribute */
    /**
     * Set single value of specified attribute. Remove all other existing values
     *
     * @param name
     * @param value
     */
    void setSingleAttribute(String name, String value);

    void setAttribute(String name, List<String> values);

    void removeAttribute(String name);

    /**
     * @param name
     * @return null if there is not any value of specified attribute or first value otherwise. Don't throw exception if there are more values of the attribute
     */
    String getFirstAttribute(String name);

    /**
     * Returns tenant attributes that match the given name as a stream.
     * @param name {@code String} Name of the attribute to be used as a filter.
     * @return Stream of all attribute values or empty stream if there are not any values. Never return {@code null}.
     */
    Stream<String> getAttributeStream(String name);

    Map<String, List<String>> getAttributes();

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