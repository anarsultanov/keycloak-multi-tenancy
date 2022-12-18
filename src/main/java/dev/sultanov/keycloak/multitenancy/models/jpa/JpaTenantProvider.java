package dev.sultanov.keycloak.multitenancy.models.jpa;

import dev.sultanov.keycloak.multitenancy.models.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.models.TenantModel;
import dev.sultanov.keycloak.multitenancy.models.TenantProvider;
import dev.sultanov.keycloak.multitenancy.models.jpa.entity.TenantEntity;
import dev.sultanov.keycloak.multitenancy.models.jpa.entity.TenantInvitationEntity;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;

public class JpaTenantProvider implements TenantProvider {

    private static final String DEFAULT_TENANT_ROLE = "tenant_admin";

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
        tenant.grantMembership(creator, Set.of(DEFAULT_TENANT_ROLE));
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

    public TenantModel.TenantDeletedEvent tenantDeletedEvent(RealmModel realm, TenantModel tenant) {
        return new TenantModel.TenantDeletedEvent() {
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

    }
}
