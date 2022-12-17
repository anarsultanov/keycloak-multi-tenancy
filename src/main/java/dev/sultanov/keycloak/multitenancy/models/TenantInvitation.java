package dev.sultanov.keycloak.multitenancy.models;

import java.util.Collection;
import java.util.Set;
import org.keycloak.models.UserModel;

public interface TenantInvitation {

    String getId();

    Tenant getTenant();

    String getEmail();

    Set<String> getRoles();
    
    UserModel getInvitedBy();
}
