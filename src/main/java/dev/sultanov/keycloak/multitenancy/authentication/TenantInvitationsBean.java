package dev.sultanov.keycloak.multitenancy.authentication;

import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import java.util.List;
import java.util.stream.Collectors;

public class TenantInvitationsBean {

    private final List<InvitationTenant> tenants;

    public TenantInvitationsBean(List<TenantInvitationModel> invitations) {
        this.tenants = invitations.stream()
                .map(invitation -> new InvitationTenant(invitation.getTenant().getId(), invitation.getTenant().getName()))
                .collect(Collectors.toList());
    }

    public List<InvitationTenant> getTenants() {
        return tenants;
    }

    public static class InvitationTenant {

        private final String id;
        private final String name;

        public InvitationTenant(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }
    }
}
