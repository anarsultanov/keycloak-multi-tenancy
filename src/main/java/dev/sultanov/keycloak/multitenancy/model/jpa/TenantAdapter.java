package dev.sultanov.keycloak.multitenancy.model.jpa;

import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantInvitationEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantMembershipEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.jpa.JpaModel;
import org.keycloak.models.jpa.PaginationUtils;
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
    public void setName(String name) {
        tenant.setName(name);
    }

    @Override
    public RealmModel getRealm() {
        return session.realms().getRealm(tenant.getRealmId());
    }

    @Override
    public TenantMembershipModel grantMembership(UserModel user, Set<String> roles) {
        TenantMembershipEntity entity = new TenantMembershipEntity();
        entity.setId(KeycloakModelUtils.generateId());
        entity.setUser(em.getReference(UserEntity.class, user.getId()));
        entity.setTenant(tenant);
        entity.setRoles(new HashSet<>(roles));
        em.persist(entity);
        em.flush();
        tenant.getMemberships().add(entity);
        return new TenantMembershipAdapter(session, realm, em, entity);
    }

    @Override
    public Stream<TenantMembershipModel> getMembershipsStream(Integer first, Integer max) {
        TypedQuery<TenantMembershipEntity> query = em.createNamedQuery("getMembershipsByTenantId", TenantMembershipEntity.class);
        query.setParameter("tenantId", tenant.getId());
        return PaginationUtils.paginateQuery(query, first, max).getResultStream()
                .map((membership) -> new TenantMembershipAdapter(session, realm, em, membership));
    }

    @Override
    public Stream<TenantMembershipModel> getMembershipsStream(String email, Integer first, Integer max) {
        TypedQuery<TenantMembershipEntity> query = em.createNamedQuery("getMembershipsByTenantIdAndUserEmail", TenantMembershipEntity.class);
        query.setParameter("tenantId", tenant.getId());
        query.setParameter("email", email);
        return PaginationUtils.paginateQuery(query, first, max).getResultStream()
                .map((membership) -> new TenantMembershipAdapter(session, realm, em, membership));
    }

    @Override
    public Optional<TenantMembershipModel> getMembershipById(String membershipId) {
        TenantMembershipEntity membership = em.find(TenantMembershipEntity.class, membershipId);
        if (membership != null && realm.getId().equals(membership.getTenant().getRealmId())) {
            return Optional.of(new TenantMembershipAdapter(session, realm, em, membership));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<TenantMembershipModel> getMembershipByUser(UserModel user) {
        TypedQuery<TenantMembershipEntity> query = em.createNamedQuery("getMembershipsByTenantIdAndUserId", TenantMembershipEntity.class);
        query.setParameter("tenantId", tenant.getId());
        query.setParameter("userId", user.getId());
        return query.getResultStream().map(m -> (TenantMembershipModel) new TenantMembershipAdapter(session, realm, em, m)).findFirst();
    }

    @Override
    public boolean revokeMembership(String membershipId) {
        var membershipEntity = em.find(TenantMembershipEntity.class, membershipId);
        if (membershipEntity != null) {
            var membershipEmail = membershipEntity.getUser().getEmail();

            if (membershipEmail != null) {
                revokeInvitations(membershipEmail);
            }
            em.remove(membershipEntity);
            em.flush();
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
        em.flush();
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
