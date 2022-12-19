package dev.sultanov.keycloak.multitenancy.resource.representation;

import java.util.Set;
import org.keycloak.representations.idm.UserRepresentation;

public class TenantMembershipRepresentation {

    private String id;
    private UserRepresentation user;
    private Set<String> roles;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UserRepresentation getUser() {
        return user;
    }

    public void setUser(UserRepresentation user) {
        this.user = user;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

}
