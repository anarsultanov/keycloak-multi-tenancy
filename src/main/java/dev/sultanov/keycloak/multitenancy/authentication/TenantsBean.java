package dev.sultanov.keycloak.multitenancy.authentication;

import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TenantsBean {

    private final List<Tenant> tenants;

    private TenantsBean(List<Tenant> tenants) {
        this.tenants = tenants;
    }

    public static TenantsBean fromInvitations(List<TenantInvitationModel> invitations) {
        var tenants = invitations.stream()
                .map(invitation -> new Tenant(invitation.getTenant().getId(), invitation.getTenant().getName(), invitation.getTenant().getAttributes()))
                .sorted(Comparator.comparing(Tenant::getName))
                .toList();
        return new TenantsBean(tenants);
    }

    public static TenantsBean fromMembership(List<TenantMembershipModel> memberships) {
        var tenants = memberships.stream()
                .map(membership -> new Tenant(membership.getTenant().getId(), membership.getTenant().getName(), membership.getTenant().getAttributes()))
                .sorted(Comparator.comparing(Tenant::getName))
                .toList();
        return new TenantsBean(tenants);
    }

    public List<Tenant> getTenants() {
        return tenants;
    }

    public static class Tenant {

        private final String id;
        private final String name;
        private final Map<String, List<String>> attributes;

        public Tenant(String id, String name, Map<String, List<String>> attributes) {
            this.id = id;
            this.name = name;
            this.attributes = attributes;        }

        public String getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }
        
        public Map<String, List<String>> getAttributes() {
        	return this.attributes;
        }
    }
}
