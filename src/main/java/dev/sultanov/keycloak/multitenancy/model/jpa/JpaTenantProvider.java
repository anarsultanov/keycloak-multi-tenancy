package dev.sultanov.keycloak.multitenancy.model.jpa;

import static org.keycloak.models.jpa.PaginationUtils.paginateQuery;
import static org.keycloak.utils.StreamsUtil.closing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.storage.jpa.JpaHashUtils;

import dev.sultanov.keycloak.multitenancy.authentication.requiredactions.ReviewTenantInvitations;
import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.TenantModel.TenantRemovedEvent;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantAttributeEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantInvitationEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantMembershipEntity;
import dev.sultanov.keycloak.multitenancy.util.Constants;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException; // Add this import
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public class JpaTenantProvider implements TenantProvider {

    private final KeycloakSession session;
    private final EntityManager em;

    public JpaTenantProvider(KeycloakSession session, EntityManager em) {
        this.session = session;
        this.em = em;
    }

    @Override
    public TenantModel createTenant(RealmModel realm, String tenantName, String mobileNumber, String countryCode, String status, UserModel user) {
        if (ObjectUtils.isEmpty(tenantName) || ObjectUtils.isEmpty(tenantName.trim())) {
            throw new IllegalArgumentException("Tenant name cannot be null or empty.");
        }
        if (ObjectUtils.isEmpty(mobileNumber) || ObjectUtils.isEmpty(mobileNumber.trim())) {
            throw new IllegalArgumentException("Mobile number cannot be null or empty.");
        }
        if (ObjectUtils.isEmpty(countryCode) || ObjectUtils.isEmpty(countryCode.trim())) {
            throw new IllegalArgumentException("Country code cannot be null or empty.");
        }
        if (ObjectUtils.isEmpty(status) || ObjectUtils.isEmpty(status.trim())) {
            throw new IllegalArgumentException("Status cannot be null or empty.");
        }

        // Check if a tenant already exists with this mobileNumber + countryCode
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<TenantEntity> root = query.from(TenantEntity.class);

        Predicate realmPredicate = cb.equal(root.get("realmId"), realm.getId());
        Predicate mobilePredicate = cb.equal(root.get("mobileNumber"), mobileNumber);
        Predicate countryPredicate = cb.equal(root.get("countryCode"), countryCode);

        query.select(cb.count(root)).where(cb.and(realmPredicate, mobilePredicate, countryPredicate));
        Long count = em.createQuery(query).getSingleResult();

        if (count > 0) {
            throw new ModelDuplicateException("A tenant with this mobile number and country code already exists.");
        }

        // Create and persist new tenant entity
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

        // Grant user membership with admin role
        tenant.grantMembership(user, Set.of(Constants.TENANT_ADMIN_ROLE));

        // Publish tenant created event
        session.getKeycloakSessionFactory().publish(tenantCreatedEvent(realm, tenant));

        return tenant;
    }

    @Override
    public Optional<TenantModel> getTenantByMobileNumberAndCountryCode(RealmModel realm, String mobileNumber, String countryCode) {
        if (ObjectUtils.isEmpty(mobileNumber) || ObjectUtils.isEmpty(countryCode)) { // Corrected check for mobileNumber
            return Optional.empty();
        }
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TenantEntity> query = cb.createQuery(TenantEntity.class);
            Root<TenantEntity> root = query.from(TenantEntity.class);
            Predicate realmPredicate = cb.equal(root.get("realmId"), realm.getId());
            Predicate mobilePredicate = cb.equal(root.get("mobileNumber"), mobileNumber);
            Predicate countryPredicate = cb.equal(root.get("countryCode"), countryCode);
            query.select(root).where(cb.and(realmPredicate, mobilePredicate, countryPredicate));
            TenantEntity result = em.createQuery(query).getSingleResult();
            return Optional.of(new TenantAdapter(session, realm, em, result));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<TenantModel> getTenantById(RealmModel realm, String id) {
        TenantEntity entity = em.find(TenantEntity.class, id);
        if (entity != null && entity.getRealmId().equals(realm.getId())) {
            return Optional.of(new TenantAdapter(session, realm, em, entity));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Stream<TenantModel> getTenantsStream(RealmModel realm) {
        TypedQuery<TenantEntity> query = em.createNamedQuery("getTenantsByRealmId", TenantEntity.class);
        query.setParameter("realmId", realm.getId());
        return query.getResultStream().map(t -> new TenantAdapter(session, realm, em, t));
    }

    @Override
    public Stream<TenantModel> getTenantsStream(RealmModel realm, String nameOrId, Map<String, String> attributes, Integer firstResult, Integer maxResults) {
        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<TenantEntity> queryBuilder = builder.createQuery(TenantEntity.class);
        Root<TenantEntity> root = queryBuilder.from(TenantEntity.class);

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(builder.equal(root.get("realmId"), realm.getId()));

        // Search by tenant name (partial, case-insensitive) OR tenant ID (exact)
        if (ObjectUtils.isEmpty(nameOrId)) {
            // Check if nameOrId looks like a Keycloak ID (36 characters long, alphanumeric)
            // This is a common pattern for Keycloak generated IDs.
            if (nameOrId.length() == 36 && nameOrId.matches("[a-f0-9\\-]+")) {
                predicates.add(builder.equal(root.get("id"), nameOrId));
            } else {
                predicates.add(builder.like(builder.lower(root.get("name")), "%" + nameOrId.toLowerCase() + "%"));
            }
        }

        // Search by mobile number (exact match)
        String mobileNumber = attributes.get("mobileNumber");
        if (ObjectUtils.isEmpty(mobileNumber)) {
            predicates.add(builder.equal(root.get("mobileNumber"), mobileNumber));
        }

        // Search by country code (exact match)
        String countryCode = attributes.get("countryCode");
        if (ObjectUtils.isEmpty(countryCode)) {
            predicates.add(builder.equal(root.get("countryCode"), countryCode));
        }
        
        // Search by status (exact match)
        String status = attributes.get("status");
        if (ObjectUtils.isEmpty(status)) {
            predicates.add(builder.equal(root.get("status"), status));
        }

        // Handle generic attributes (excluding dedicated fields)
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            // Skip dedicated fields that are now directly mapped to TenantEntity properties
            if ("mobileNumber".equalsIgnoreCase(key) ||
                "countryCode".equalsIgnoreCase(key) ||
                "status".equalsIgnoreCase(key)) {
                continue;
            }

            if (key == null || key.isEmpty()) {
                continue;
            }
            String value = entry.getValue();

            Join<TenantEntity, TenantAttributeEntity> attributeJoin = root.join("attributes"); // Changed to directly join

            Predicate attrNamePredicate = builder.equal(attributeJoin.get("name"), key);
            Predicate attrValuePredicate = builder.like(builder.lower(attributeJoin.get("value")), "%" + value.toLowerCase() + "%");
            predicates.add(builder.and(attrNamePredicate, attrValuePredicate));
        }

        Predicate finalPredicate = builder.and(predicates.toArray(new Predicate[0]));
        queryBuilder.where(finalPredicate).orderBy(builder.asc(root.get("name")));

        TypedQuery<TenantEntity> query = em.createQuery(queryBuilder);
        return closing(paginateQuery(query, firstResult, maxResults).getResultStream())
                .map(tenantEntity -> new TenantAdapter(session, realm, em, tenantEntity));
    }

    @Override
    public Stream<TenantModel> getTenantsByAttributeStream(RealmModel realm, String attrName, String attrValue) {
        boolean longAttribute = attrValue != null && attrValue.length() > 255;
        TypedQuery<TenantEntity> query = longAttribute ?
                em.createNamedQuery("getTenantsByAttributeNameAndLongValue", TenantEntity.class)
                        .setParameter("realmId", realm.getId())
                        .setParameter("name", attrName)
                        .setParameter("longValueHash", JpaHashUtils.hashForAttributeValue(attrValue)):
                em.createNamedQuery("getTenantsByAttributeNameAndValue", TenantEntity.class)
                        .setParameter("realmId", realm.getId())
                        .setParameter("name", attrName)
                        .setParameter("value", attrValue);

        return closing(query.getResultStream()
                .map(tenantEntity -> new TenantAdapter(session, realm, em, tenantEntity)));
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

    public TenantRemovedEvent tenantDeletedEvent(RealmModel realm, TenantModel tenant) {
        return new TenantRemovedEvent() {
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
    public Stream<TenantModel> getUserTenantsStream(RealmModel realm, UserModel user) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TenantMembershipEntity> query = cb.createQuery(TenantMembershipEntity.class);
        Root<TenantMembershipEntity> root = query.from(TenantMembershipEntity.class);
        Join<TenantMembershipEntity, TenantEntity> tenantJoin = root.join("tenant");

        Predicate realmMatch = cb.equal(tenantJoin.get("realmId"), realm.getId());
        Predicate userMatch = cb.equal(root.get("user").get("id"), user.getId());

        query.select(root).where(cb.and(realmMatch, userMatch));

        return em.createQuery(query).getResultStream()
                .map(TenantMembershipEntity::getTenant)
                .map(entity -> new TenantAdapter(session, realm, em, entity));
    }
    
    // Helper method to check if a string is a valid UUID format (Keycloak ID)
    private boolean isValidKeycloakId(String id) {
        if (id == null || id.length() != 36) {
            return false;
        }
        // Basic check for UUID format: 8-4-4-4-12 hexadecimal digits
        return id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }
    
    @Override
    public void close() {
        // Clean up if necessary
    }
}