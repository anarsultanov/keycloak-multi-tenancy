package dev.sultanov.keycloak.multitenancy.resource.representation;

import java.util.HashSet;
import java.util.Set;

public class TenantInvitationRequest {

    private String email;
    private Set<String> roles = new HashSet<>();

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

}
