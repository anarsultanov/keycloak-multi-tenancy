package dev.sultanov.keycloak.multitenancy.model.jpa;

import static org.keycloak.models.jpa.PaginationUtils.paginateQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.storage.jpa.JpaHashUtils;

import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantAttributeEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantInvitationEntity;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantMembershipEntity;
import dev.sultanov.keycloak.multitenancy.util.Constants;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
    public Optional<TenantModel> getTenantByMobileNumberAndCountryCode(RealmModel realm, String mobileNumber, String countryCode) {
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TenantEntity> query = cb.createQuery(TenantEntity.class);
            Root<TenantEntity> root = query.from(TenantEntity.class);
            Predicate realmPredicate = cb.equal(root.get("realmId"), realm.getId());
            Predicate mobilePredicate = cb.equal(root.get("mobileNumber"), mobileNumber);
            Predicate countryCodePredicate = cb.equal(root.get("countryCode"), countryCode);
            query.select(root).where(cb.and(realmPredicate, mobilePredicate, countryCodePredicate));
            TenantEntity result = em.createQuery(query).getSingleResult();
            return Optional.of(new TenantAdapter(session, realm, em, result));
        } catch (NoResultException e) {
            log.debug("No tenant found for mobile number {} and country code {}", mobileNumber, countryCode);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error querying tenant by mobile number {} and country code {}", mobileNumber, countryCode, e);
            throw new RuntimeException("Database error while fetching tenant", e);
        }
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
    public Stream<TenantModel> getTenantsStream(RealmModel realm) {
        TypedQuery<TenantEntity> query = em.createNamedQuery("getTenantsByRealmId", TenantEntity.class);
        query.setParameter("realmId", realm.getId());
        return query.getResultStream().map(t -> new TenantAdapter(session, realm, em, t));
    }

    @Override
    public Stream<TenantModel> getTenantsStream(RealmModel realm, String name, Map<String, String> attributes, Integer firstResult, Integer maxResults) {
        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<TenantEntity> queryBuilder = builder.createQuery(TenantEntity.class);
        Root<TenantEntity> root = queryBuilder.from(TenantEntity.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(root.get("realmId"), realm.getId()));

        String exactMatch = attributes != null ? attributes.get("exactMatch") : null;
        boolean isExactMatch = "true".equalsIgnoreCase(exactMatch);
        if (!ObjectUtils.isEmpty(name)) {
            String trimmedName = name.trim();
            Predicate namePredicate;
            if (isExactMatch || trimmedName.length() < 3) {
                namePredicate = builder.equal(builder.lower(root.get("name")), trimmedName.toLowerCase());
            } else {
                namePredicate = builder.like(builder.lower(root.get("name")), "%" + trimmedName.toLowerCase() + "%");
            }
            Predicate idPredicate = builder.equal(root.get("id"), trimmedName);
            predicates.add(builder.or(namePredicate, idPredicate));
        }

        String mobileNumber = attributes != null ? attributes.get("mobileNumber") : null;
        if (!ObjectUtils.isEmpty(mobileNumber)) {
            predicates.add(builder.equal(root.get("mobileNumber"), mobileNumber));
        }

        String countryCode = attributes != null ? attributes.get("countryCode") : null;
        if (!ObjectUtils.isEmpty(countryCode)) {
            predicates.add(builder.equal(root.get("countryCode"), countryCode));
        }

        String status = attributes != null ? attributes.get("status") : null;
        if (!ObjectUtils.isEmpty(status)) {
            predicates.add(builder.equal(root.get("status"), status));
        }

        Join<TenantEntity, TenantAttributeEntity> attributeJoin = null;
        if (ObjectUtils.isNotEmpty(attributes)) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                String key = entry.getKey();
                if ("mobileNumber".equalsIgnoreCase(key) || "countryCode".equalsIgnoreCase(key) || "status".equalsIgnoreCase(key) || "exactMatch".equalsIgnoreCase(key)) {
                    continue;
                }
                if (ObjectUtils.isEmpty(key)) {
                    continue;
                }
                String value = entry.getValue();
                if (ObjectUtils.isEmpty(value)) {
                    continue;
                }
                if (attributeJoin == null) {
                    attributeJoin = root.join("attributes");
                }
                Predicate attrNamePredicate = builder.equal(attributeJoin.get("name"), key);
                Predicate attrValuePredicate = builder.like(builder.lower(attributeJoin.get("value")), "%" + value.toLowerCase() + "%");
                predicates.add(builder.and(attrNamePredicate, attrValuePredicate));
            }
        }

        Predicate finalPredicate = builder.and(predicates.toArray(new Predicate[0]));
        queryBuilder.where(finalPredicate).orderBy(builder.asc(root.get("name")));
        TypedQuery<TenantEntity> query = em.createQuery(queryBuilder);
        return paginateQuery(query, firstResult, maxResults)
                .getResultStream()
                .map(tenantEntity -> new TenantAdapter(session, realm, em, tenantEntity));
    }


    @Override
    public Stream<TenantModel> getTenantsByAttributeStream(RealmModel realm, String attrName, String attrValue) {
        boolean longValue = attrValue.length() > 255;
        TypedQuery<TenantEntity> query = longValue ?
                em.createNamedQuery("getTenantsByAttributeNameAndLongValue", TenantEntity.class)
                        .setParameter("realmId", realm.getId())
                        .setParameter("name", attrName)
                        .setParameter("longValueHash", JpaHashUtils.hashForAttributeValue(attrValue)) :
                em.createNamedQuery("getTenantsByAttributeNameAndValue", TenantEntity.class)
                        .setParameter("realmId", realm.getId())
                        .setParameter("name", attrName)
                        .setParameter("value", attrValue);

        return query.getResultStream().map(tenantEntity -> new TenantAdapter(session, realm, em, tenantEntity));
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
