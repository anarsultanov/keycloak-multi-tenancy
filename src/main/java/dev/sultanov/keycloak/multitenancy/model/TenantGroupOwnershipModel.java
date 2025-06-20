package dev.sultanov.keycloak.multitenancy.model;

import org.keycloak.models.GroupModel;
import org.keycloak.models.UserModel;

import java.util.Set;

public interface TenantGroupOwnershipModel {

    String getId();

    TenantModel getTenant();

    GroupModel getGroup();

}
