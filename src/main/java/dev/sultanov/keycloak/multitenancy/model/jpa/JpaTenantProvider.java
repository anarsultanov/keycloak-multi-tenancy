package dev.sultanov.keycloak.multitenancy.model.jpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;

import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantInvitationEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantMembershipEntity;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantMembershipRepresentation;
import dev.sultanov.keycloak.multitenancy.resource.representation.UserMembershipRepresentation;
import dev.sultanov.keycloak.multitenancy.util.Constants;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JpaTenantProvider implements TenantProvider {

    private final KeycloakSession session;
    private final EntityManager em;

    public JpaTenantProvider(KeycloakSession session, EntityManager em) {
        this.session = session;
        this.em = em;
    }

    @Override
    public TenantModel createTenant(RealmModel realm, String tenantName, String mobileNumber, String countryCode, String status, UserModel user) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<TenantEntity> root = query.from(TenantEntity.class);
        Predicate realmPredicate = cb.equal(root.get("realmId"), realm.getId());
        Predicate mobilePredicate = cb.equal(root.get("mobileNumber"), mobileNumber);
        Predicate countryCodePredicate = cb.equal(root.get("countryCode"), countryCode);

        query.select(cb.count(root)).where(cb.and(realmPredicate, mobilePredicate, countryCodePredicate));
        Long count = em.createQuery(query).getSingleResult();

        if (count > 0) {
            throw new ModelDuplicateException("A tenant with this mobile number and country code already exists.");
        }

        TenantEntity entity = new TenantEntity();
        entity.setId(KeycloakModelUtils.generateId());
        entity.setName(tenantName);
        entity.setRealmId(realm.getId());
        entity.setMobileNumber(mobileNumber);
        entity.setCountryCode(countryCode);
        entity.setStatus(status);

        em.persist(entity);
        em.flush();

        TenantModel tenant = new TenantAdapter(session, realm, em, entity);
        tenant.grantMembership(user, Set.of(Constants.TENANT_ADMIN_ROLE));
        session.getKeycloakSessionFactory().publish(tenantCreatedEvent(realm, tenant));

        return tenant;
    }

    @Override
    public Optional<TenantModel> getTenantById(RealmModel realm, String id) {
        TenantEntity entity = em.find(TenantEntity.class, id);
        if (ObjectUtils.isNotEmpty(entity) && entity.getRealmId().equals(realm.getId())) {
            return Optional.of(new TenantAdapter(session, realm, em, entity));
        }
        log.debug("No tenant found for ID {} in realm {}", id, realm.getId());
        return Optional.empty();
    }

    @Override
    public Stream<TenantModel> getTenantsStream(RealmModel realm, String unusedNameOrIdQuery, Map<String, String> unusedAttributes, 
                                                String mobileNumber, String countryCode) {
        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<TenantEntity> queryBuilder = builder.createQuery(TenantEntity.class);
        Root<TenantEntity> root = queryBuilder.from(TenantEntity.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(root.get("realmId"), realm.getId()));

        if (!ObjectUtils.isEmpty(mobileNumber)) {
            predicates.add(builder.equal(root.get("mobileNumber"), mobileNumber));
        }

        if (!ObjectUtils.isEmpty(countryCode)) {
            predicates.add(builder.equal(root.get("countryCode"), countryCode));
        }

        Predicate finalPredicate = builder.and(predicates.toArray(new Predicate[0]));
        queryBuilder.where(finalPredicate).orderBy(builder.asc(root.get("name")));

        TypedQuery<TenantEntity> query = em.createQuery(queryBuilder);
        return query.getResultStream()
                    .map(tenantEntity -> new TenantAdapter(session, realm, em, tenantEntity));
    }
    
    @Override
    public List<UserMembershipRepresentation> listMembershipsByUserId(RealmModel realm, String userId) {
        log.debug("Fetching memberships for user ID: {}", userId);

        if (ObjectUtils.isEmpty(userId) || userId.trim().isEmpty()) {
            log.error("User ID cannot be null or empty");
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }

        UserModel user = session.users().getUserById(realm, userId);
        if (ObjectUtils.isEmpty(user)) {
            log.error("User not found with ID: {}", userId);
            throw new NotFoundException(String.format("User %s not found", userId));
        }

        TypedQuery<TenantMembershipEntity> query =
                em.createNamedQuery("getMembershipsByUserId", TenantMembershipEntity.class);
        query.setParameter("userId", userId);

        List<UserMembershipRepresentation> memberships = query.getResultList()
            .stream()
            .filter(entity -> entity.getTenant() != null && entity.getTenant().getId() != null)
            .map(entity -> {
                UserMembershipRepresentation membership = new UserMembershipRepresentation();
                membership.setId(entity.getId());
                membership.setTenantId(entity.getTenant().getId());
                membership.setRoles(entity.getRoles() != null ? new ArrayList<>(entity.getRoles()) : new ArrayList<>());
                log.debug("Constructed membership: id={}, tenantId={}, roles={}",
                        membership.getId(), membership.getTenantId(), membership.getRoles());
                return membership;
            })
            .collect(Collectors.toList());

        log.info("Found {} valid memberships for user ID: {}", memberships.size(), userId);
        return memberships;
    }
    
    @Override
    public boolean revokeMembership(RealmModel realm, String tenantId, String userId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TenantMembershipEntity> query = cb.createQuery(TenantMembershipEntity.class);
        Root<TenantMembershipEntity> root = query.from(TenantMembershipEntity.class);
        Join<TenantMembershipEntity, TenantEntity> tenantJoin = root.join("tenant");

        Predicate realmMatch = cb.equal(tenantJoin.get("realmId"), realm.getId());
        Predicate tenantMatch = cb.equal(tenantJoin.get("id"), tenantId);
        Predicate userMatch = cb.equal(root.get("user").get("id"), userId);

        query.select(root).where(cb.and(realmMatch, tenantMatch, userMatch));

        List<TenantMembershipEntity> memberships = em.createQuery(query).getResultList();
        if (memberships.isEmpty()) {
            log.debug("No membership found for tenant ID: {} and user ID: {}", tenantId, userId);
            return false;
        }

        for (TenantMembershipEntity entity : memberships) {
            em.remove(entity);
        }
        em.flush();
        return true;
    }

    @Override
    public boolean revokeInvitation(RealmModel realm, String tenantId, String userId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TenantInvitationEntity> query = cb.createQuery(TenantInvitationEntity.class);
        Root<TenantInvitationEntity> root = query.from(TenantInvitationEntity.class);
        Join<TenantInvitationEntity, TenantEntity> tenantJoin = root.join("tenant");

        Predicate realmMatch = cb.equal(tenantJoin.get("realmId"), realm.getId());
        Predicate tenantMatch = cb.equal(tenantJoin.get("id"), tenantId);
        Predicate userMatch = cb.equal(root.get("userId"), userId);

        query.select(root).where(cb.and(realmMatch, tenantMatch, userMatch));

        List<TenantInvitationEntity> invitations = em.createQuery(query).getResultList();
        if (invitations.isEmpty()) {
            log.debug("No invitation found for tenant ID: {} and user ID: {}", tenantId, userId);
            return false;
        }

        for (TenantInvitationEntity entity : invitations) {
            em.remove(entity);
        }
        em.flush();
        return true;
    }

    @Override
    public boolean deleteTenant(RealmModel realm, String id) {
        getTenantById(realm, id).ifPresent(tenant -> {
            var entity = em.find(TenantEntity.class, id);
            em.remove(entity);
            em.flush();
            session.getKeycloakSessionFactory().publish(tenantDeletedEvent(realm, tenant));
        });
        return true;
    }

    @Override
    public Stream<TenantInvitationModel> getTenantInvitationsStream(RealmModel realm, UserModel user) {
        TypedQuery<TenantInvitationEntity> query = em.createNamedQuery("getInvitationsByRealmAndEmail", TenantInvitationEntity.class);
        query.setParameter("realmId", realm.getId());
        query.setParameter("search", user.getEmail());
        return query.getResultStream().map(i -> new TenantInvitationAdapter(session, realm, em, i));
    }

    @Override
    public Stream<TenantMembershipModel> getTenantMembershipsStream(RealmModel realm, UserModel user) {
        TypedQuery<TenantMembershipEntity> query = em.createNamedQuery("getMembershipsByRealmIdAndUserId", TenantMembershipEntity.class);
        query.setParameter("realmId", realm.getId());
        query.setParameter("userId", user.getId());
        return query.getResultStream().map(m -> new TenantMembershipAdapter(session, realm, em, m));
    }
    
    @Override
    public Stream<TenantModel> getUserTenantsStream(RealmModel realm, UserModel user) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TenantMembershipEntity> query = cb.createQuery(TenantMembershipEntity.class);
        Root<TenantMembershipEntity> root = query.from(TenantMembershipEntity.class);
        Join<TenantMembershipEntity, TenantEntity> tenantJoin = root.join("tenant");

        Predicate realmMatch = cb.equal(tenantJoin.get("realmId"), realm.getId());
        Predicate userMatch = cb.equal(root.get("user").get("id"), user.getId());

        query.select(root).where(cb.and(realmMatch, userMatch));

        return em.createQuery(query)
                 .getResultStream()
                 .map(TenantMembershipEntity::getTenant)
                 .map(entity -> new TenantAdapter(session, realm, em, entity));
    }
    
    public TenantModel.TenantCreatedEvent tenantCreatedEvent(RealmModel realm, TenantModel tenant) {
        return new TenantModel.TenantCreatedEvent() {
            @Override
            public TenantModel getTenant() {
                return tenant;
            }

            @Override
            public KeycloakSession getKeycloakSession() {
                return session;
            }

            @Override
            public RealmModel getRealm() {
                return realm;
            }
        };
    }

    public TenantModel.TenantRemovedEvent tenantDeletedEvent(RealmModel realm, TenantModel tenant) {
        return new TenantModel.TenantRemovedEvent() {
            @Override
            public TenantModel getTenant() {
                return tenant;
            }

            @Override
            public KeycloakSession getKeycloakSession() {
                return session;
            }

            @Override
            public RealmModel getRealm() {
                return realm;
            }
        };
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub
    }
}