package dev.sultanov.keycloak.multitenancy.model;

import java.util.Set;
import org.keycloak.models.UserModel;

public interface TenantMembershipModel {

    String getId();

    TenantModel getTenant();

    UserModel getUser();

    Set<String> getRoles();

    void addRoles(Set<String> roles);

    void removeRoles(Set<String> roles);
}
