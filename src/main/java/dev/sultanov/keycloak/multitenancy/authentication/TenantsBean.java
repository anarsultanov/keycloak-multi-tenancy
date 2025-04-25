package dev.sultanov.keycloak.multitenancy.authentication;

import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class TenantsBean {

    private final List<Tenant> tenants;

    private TenantsBean(List<Tenant> tenants) {
        this.tenants = tenants;
    }

    public List<Tenant> getTenants() {
        return tenants;
    }

    public static TenantsBean fromInvitations(List<TenantInvitationModel> invitations) {
        List<Tenant> tenants = invitations.stream()
        		.map(invitation -> new Tenant(
        			    invitation.getTenant().getId(),
        			    invitation.getTenant().getName(),
        			    invitation.getRoles(),
        			    invitation.getLogoUrl() != null ? invitation.getLogoUrl() : "https://cdn-icons-png.flaticon.com/512/9187/9187604.png"))
                .collect(Collectors.toList());
        return new TenantsBean(tenants);
    }

    public static TenantsBean fromMembership(List<TenantMembershipModel> memberships) {
        List<Tenant> tenants = memberships.stream()
        		.map(membership -> new Tenant(
        			    membership.getTenant().getId(),
        			    membership.getTenant().getName(),
        			    membership.getRoles(),
        			    Optional.ofNullable(membership.getTenant().getFirstAttribute("logoUrl"))
        			            .orElse("https://cdn-icons-png.flaticon.com/512/9187/9187604.png")))
                .collect(Collectors.toList());
        return new TenantsBean(tenants);
    }

    public static class Tenant {
        private final String id;
        private final String name;
        private final Set<String> roles;
        private final String logoUrl;

        public Tenant(String id, String name, Set<String> roles, String logoUrl) {
            this.id = id;
            this.name = name;
            this.roles = roles;
            this.logoUrl = logoUrl;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Set<String> getRoles() {
            return roles;
        }

        public String getLogoUrl() {
            return logoUrl;
        }
    }
}