package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantMembershipRepresentation;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.ModelToRepresentation;

public class ModelMapper {

    private ModelMapper() {
        throw new AssertionError();
    }

    static TenantRepresentation toRepresentation(TenantModel tenant) {
        TenantRepresentation representation = new TenantRepresentation();
        representation.setId(tenant.getId());
        representation.setName(tenant.getName());
        representation.setRealm(tenant.getRealm().getName());
        return representation;
    }

    static TenantInvitationRepresentation toRepresentation(TenantInvitationModel invitation) {
        TenantInvitationRepresentation representation = new TenantInvitationRepresentation();
        representation.setId(invitation.getId());
        representation.setTenantId(representation.getTenantId());
        representation.setEmail(invitation.getEmail());
        representation.setRoles(invitation.getRoles());
        representation.setInvitedBy(invitation.getInvitedBy().getId());
        return representation;
    }

    static TenantMembershipRepresentation toRepresentation(TenantMembershipModel membership) {
        TenantMembershipRepresentation representation = new TenantMembershipRepresentation();
        representation.setId(membership.getId());
        representation.setUser(ModelToRepresentation.toBriefRepresentation(membership.getUser()));
        representation.setRoles(membership.getRoles());
        return representation;
    }
}
