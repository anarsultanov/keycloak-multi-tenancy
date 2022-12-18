package dev.sultanov.keycloak.multitenancy.models;

import java.util.Optional;
import java.util.stream.Stream;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.Provider;

public interface TenantProvider extends Provider {

    TenantModel createTenant(RealmModel realm, String name, UserModel creator);

    public Optional<TenantModel> getTenantById(RealmModel realm, String id);

    boolean deleteTenant(RealmModel realm, String id);

    public Stream<TenantInvitationModel> getTenantInvitationsStream(RealmModel realm, UserModel user);
}
