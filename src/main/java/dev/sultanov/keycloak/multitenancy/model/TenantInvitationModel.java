package dev.sultanov.keycloak.multitenancy.model;

import java.util.Set;
import org.keycloak.models.UserModel;

public interface TenantInvitationModel {

    String getId();

    TenantModel getTenant();

    String getEmail();

    Set<String> getRoles();
    
    UserModel getInvitedBy();
}
