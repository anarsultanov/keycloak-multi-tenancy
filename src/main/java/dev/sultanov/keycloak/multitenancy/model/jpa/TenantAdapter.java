package dev.sultanov.keycloak.multitenancy.model.jpa;

import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantInvitationEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantMembershipEntity;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.jpa.JpaModel;
import org.keycloak.models.jpa.entities.UserEntity;
import org.keycloak.models.utils.KeycloakModelUtils;

public class TenantAdapter implements TenantModel, JpaModel<TenantEntity> {

    private final KeycloakSession session;
    private final TenantEntity tenant;
    private final EntityManager em;
    private final RealmModel realm;

    public TenantAdapter(KeycloakSession session, RealmModel realm, EntityManager em, TenantEntity tenant) {
        this.session = session;
        this.em = em;
        this.tenant = tenant;
        this.realm = realm;
    }

    @Override
    public String getId() {
        return tenant.getId();
    }

    @Override
    public String getName() {
        return tenant.getName();
    }

    @Override
    public RealmModel getRealm() {
        return session.realms().getRealm(tenant.getRealmId());
    }

    @Override
    public TenantMembershipAdapter grantMembership(UserModel user, Set<String> roles) {
        TenantMembershipEntity entity = new TenantMembershipEntity();
        entity.setId(KeycloakModelUtils.generateId());
        entity.setUser(em.getReference(UserEntity.class, user.getId()));
        entity.setTenant(tenant);
        entity.setRoles(new HashSet<>(roles));
        em.persist(entity);
        tenant.getMemberships().add(entity);
        return new TenantMembershipAdapter(session, realm, em, entity);
    }

    @Override
    public Stream<TenantMembershipModel> getMembershipsStream() {
        return tenant.getMemberships().stream()
                .map(membership -> new TenantMembershipAdapter(session, realm, em, membership));
    }

    @Override
    public boolean revokeMembership(String membershipId) {
        var optionalMembership = getMembershipById(membershipId);
        if (optionalMembership.isPresent()) {
            var membershipEmail = optionalMembership.get().getUser().getEmail();
            tenant.getMemberships().removeIf(entity -> entity.getId().equals(membershipId));
            if (membershipEmail != null) {
                revokeInvitations(membershipEmail);
            }
            return true;
        }
        return false;
    }

    @Override
    public TenantInvitationModel addInvitation(String email, UserModel inviter, Set<String> roles) {
        TenantInvitationEntity entity = new TenantInvitationEntity();
        entity.setId(KeycloakModelUtils.generateId());
        entity.setTenant(tenant);
        entity.setEmail(email.toLowerCase());
        entity.setInvitedBy(inviter.getId());
        entity.setRoles(new HashSet<>(roles));
        em.persist(entity);
        tenant.getInvitations().add(entity);
        return new TenantInvitationAdapter(session, realm, em, entity);
    }

    @Override
    public Stream<TenantInvitationModel> getInvitationsStream() {
        return tenant.getInvitations().stream().map(i -> new TenantInvitationAdapter(session, realm, em, i));
    }

    @Override
    public boolean revokeInvitation(String id) {
        return tenant.getInvitations().removeIf(inv -> inv.getId().equals(id));
    }

    @Override
    public void revokeInvitations(String email) {
        tenant.getInvitations().removeIf(inv -> inv.getEmail().equals(email.toLowerCase()));
    }

    @Override
    public TenantEntity getEntity() {
        return tenant;
    }
}
