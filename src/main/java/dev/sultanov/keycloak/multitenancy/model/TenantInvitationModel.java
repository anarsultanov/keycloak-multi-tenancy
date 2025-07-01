package dev.sultanov.keycloak.multitenancy.model;

import java.util.Set;
import org.keycloak.models.UserModel;

public interface TenantInvitationModel {

    String getId();

    TenantModel getTenant();

    String getEmail();

    Set<String> getRoles();
    
    UserModel getInvitedBy();

    /**
     * Retrieves the logo URL from the tenant's attributes.
     * @return The logo URL or null if not set.
     */
    default String getLogoUrl() {
        return getTenant().getFirstAttribute("logoUrl");
    }
}