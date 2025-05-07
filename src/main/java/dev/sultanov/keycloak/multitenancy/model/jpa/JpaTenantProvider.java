package dev.sultanov.keycloak.multitenancy.model.jpa;

import static org.keycloak.models.jpa.PaginationUtils.paginateQuery;
import static org.keycloak.utils.StreamsUtil.closing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.storage.jpa.JpaHashUtils;

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
    public TenantModel createTenant(RealmModel realm, String name, UserModel creator) {
        TenantEntity entity = new TenantEntity();
        entity.setId(KeycloakModelUtils.generateId());
        entity.setName(name);
        entity.setRealmId(realm.getId());
        em.persist(entity);
        em.flush();

        TenantModel tenant = new TenantAdapter(session, realm, em, entity);
        tenant.grantMembership(creator, Set.of(Constants.TENANT_ADMIN_ROLE));
        session.getKeycloakSessionFactory().publish(tenantCreatedEvent(realm, tenant));

        return tenant;
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
    public Stream<TenantModel> getTenantsStream(RealmModel realm, String name, Map<String, String> attributes, Integer firstResult, Integer maxResults) {
        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<TenantEntity> queryBuilder = builder.createQuery(TenantEntity.class);
        Root<TenantEntity> root = queryBuilder.from(TenantEntity.class);

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(builder.equal(root.get("realmId"), realm.getId()));
        if (name != null && !name.isEmpty()) {
            predicates.add(builder.like(root.get("name"), "%" + name + "%"));
        }

        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }
            String value = entry.getValue();

            Join<TenantEntity, TenantAttributeEntity> attributeJoin = root.join("attributes");

            Predicate attrNamePredicate = builder.equal(attributeJoin.get("name"), key);
            Predicate attrValuePredicate = builder.equal(attributeJoin.get("value"), value);
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
    public Stream<TenantModel> getAllTenantsStream() {
        TypedQuery<TenantEntity> query = em.createQuery("SELECT t FROM TenantEntity t", TenantEntity.class);
        return query.getResultStream()
                .map(t -> new TenantAdapter(session, session.realms().getRealm(t.getRealmId()), em, t));
    }
    
    @Override
    public void close() {
        // Clean up if necessary
    }
}
