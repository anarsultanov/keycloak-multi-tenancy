package dev.sultanov.keycloak.multitenancy.model.jpa;

import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantInvitationEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantMembershipEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantAttributeEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;

import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.jpa.JpaModel;
import org.keycloak.models.jpa.PaginationUtils;
import org.keycloak.models.jpa.entities.UserEntity;
import org.keycloak.models.utils.KeycloakModelUtils;

import static java.util.Optional.ofNullable;
import static org.keycloak.common.util.CollectionUtil.collectionEquals;

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
    public void setSingleAttribute(String name, String value) {
        boolean found = false;
        List<TenantAttributeEntity> toRemove = new ArrayList<>();
        for (TenantAttributeEntity attr : tenant.getAttributes()) {
            if (attr.getName().equals(name)) {
                if (!found) {
                    attr.setValue(value);
                    found = true;
                } else {
                    toRemove.add(attr);
                }
            }
        }

        for (TenantAttributeEntity attr : toRemove) {
            em.remove(attr);
            tenant.getAttributes().remove(attr);
        }

        if (found) {
            return;
        }

        persistAttributeValue(name, value);
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        List<String> current = getAttributes().getOrDefault(name, List.of());

        if (collectionEquals(current, ofNullable(values).orElse(List.of()))) {
            return;
        }

        // Remove all existing
        removeAttribute(name);

        // Put all new
        for (String value : values) {
            persistAttributeValue(name, value);
        }
    }

    private void persistAttributeValue(String name, String value) {
        TenantAttributeEntity attr = new TenantAttributeEntity();
        attr.setId(KeycloakModelUtils.generateId());
        attr.setName(name);
        attr.setValue(value);
        attr.setTenant(tenant);
        em.persist(attr);
        tenant.getAttributes().add(attr);
    }

    @Override
    public void removeAttribute(String name) {
        Iterator<TenantAttributeEntity> it = tenant.getAttributes().iterator();
        while (it.hasNext()) {
            TenantAttributeEntity attr = it.next();
            if (attr.getName().equals(name)) {
                it.remove();
                em.remove(attr);
            }
        }
    }

    @Override
    public String getFirstAttribute(String name) {
        for (TenantAttributeEntity attr : tenant.getAttributes()) {
            if (attr.getName().equals(name)) {
                return attr.getValue();
            }
        }
        return null;
    }

    @Override
    public Stream<String> getAttributeStream(String name) {
        return tenant.getAttributes().stream().filter(attribute -> Objects.equals(attribute.getName(), name))
                .map(TenantAttributeEntity::getValue);
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        MultivaluedHashMap<String, String> result = new MultivaluedHashMap<>();
        for (TenantAttributeEntity attr : tenant.getAttributes()) {
            result.add(attr.getName(), attr.getValue());
        }
        return result;
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
