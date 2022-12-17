package dev.sultanov.keycloak.multitenancy.models.jpa;

import dev.sultanov.keycloak.multitenancy.models.Tenant;
import dev.sultanov.keycloak.multitenancy.models.TenantInvitation;
import dev.sultanov.keycloak.multitenancy.models.jpa.entity.TenantEntity;
import dev.sultanov.keycloak.multitenancy.models.jpa.entity.TenantInvitationEntity;
import dev.sultanov.keycloak.multitenancy.models.jpa.entity.TenantMembershipEntity;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import javax.persistence.EntityManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.jpa.JpaModel;
import org.keycloak.models.utils.KeycloakModelUtils;

public class TenantAdapter implements Tenant, JpaModel<TenantEntity> {

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
    public Stream<UserModel> getMembers() {
        return tenant.getMemberships().stream()
                .map(TenantMembershipEntity::getUserId)
                .map(uid -> session.users().getUserById(realm, uid));
    }

    @Override
    public void grantMembership(UserModel user, Set<String> roles) {
        if (isMember(user)) {
            return;
        }
        TenantMembershipEntity membership = new TenantMembershipEntity();
        membership.setId(KeycloakModelUtils.generateId());
        membership.setUserId(user.getId());
        membership.setTenant(tenant);
        membership.setRoles(new HashSet<>(roles));
        em.persist(membership);
        tenant.getMemberships().add(membership);
    }

    @Override
    public void revokeMembership(UserModel user) {
        if (!isMember(user)) {
            return;
        }
        tenant.getMemberships().removeIf(member -> member.getUserId().equals(user.getId()));
        if (user.getEmail() != null) {
            revokeInvitations(user.getEmail());
        }
    }

    @Override
    public void grantRole(UserModel user, String role) {
        tenant.getMemberships().stream()
                .filter(membership -> membership.getUserId().equals(user.getId()))
                .findFirst()
                .ifPresent(membership -> membership.getRoles().add(role));
    }

    @Override
    public void revokeRole(UserModel user, String role) {
        tenant.getMemberships().stream()
                .filter(membership -> membership.getUserId().equals(user.getId()))
                .findFirst()
                .ifPresent(membership -> membership.getRoles().remove(role));
    }

    @Override
    public boolean hasRole(UserModel user, String role) {
        return tenant.getMemberships().stream()
                .filter(membership -> membership.getUserId().equals(user.getId()))
                .findFirst()
                .map(membership -> membership.getRoles().contains(role))
                .orElse(false);
    }

    @Override
    public Stream<TenantInvitation> getInvitations() {
        return tenant.getInvitations().stream().map(i -> new TenantInvitationAdapter(session, realm, em, i));
    }

    @Override
    public void revokeInvitation(String id) {
        tenant.getInvitations().removeIf(inv -> inv.getId().equals(id));
    }

    @Override
    public void revokeInvitations(String email) {
        tenant.getInvitations().removeIf(inv -> inv.getEmail().equals(email.toLowerCase()));
    }

    @Override
    public TenantInvitation addInvitation(String email, UserModel inviter) {
        TenantInvitationEntity entity = new TenantInvitationEntity();
        entity.setId(KeycloakModelUtils.generateId());
        entity.setTenant(tenant);
        entity.setEmail(email.toLowerCase());
        entity.setInvitedBy(inviter.getId());
        em.persist(entity);
        tenant.getInvitations().add(entity);
        return new TenantInvitationAdapter(session, realm, em, entity);
    }

    @Override
    public TenantEntity getEntity() {
        return tenant;
    }
}
