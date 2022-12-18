package dev.sultanov.keycloak.multitenancy.authentication;

import dev.sultanov.keycloak.multitenancy.models.TenantInvitationModel;
import java.util.List;
import java.util.stream.Collectors;
import org.keycloak.models.RealmModel;

public class InvitationsData {

    private final String realmName;
    private final List<Invitation> invitations;

    public InvitationsData(RealmModel realm, List<TenantInvitationModel> invitations) {
        this.realmName = realm != null ? realm.getName() : null;
        this.invitations = invitations.stream()
                .map(invitation -> new Invitation(invitation.getTenant().getId(), invitation.getTenant().getName()))
                .collect(Collectors.toList());
    }

    public static class Invitation {

        private final String id;
        private final String name;

        public Invitation(String id, String name) {
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
