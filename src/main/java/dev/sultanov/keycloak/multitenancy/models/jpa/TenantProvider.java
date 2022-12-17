package dev.sultanov.keycloak.multitenancy.models.jpa;

import dev.sultanov.keycloak.multitenancy.models.DefaultTenantRole;
import dev.sultanov.keycloak.multitenancy.models.Tenant;
import dev.sultanov.keycloak.multitenancy.models.jpa.entity.TenantEntity;
import java.util.Optional;
import java.util.Set;
import javax.persistence.EntityManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.Provider;

public class TenantProvider implements Provider {

    private final KeycloakSession session;
    private final EntityManager em;

    public TenantProvider(KeycloakSession session, EntityManager em) {
        this.session = session;
        this.em = em;
    }

    public Tenant create(RealmModel realm, String name, UserModel creator, boolean admin) {
        TenantEntity entity = new TenantEntity();
        entity.setId(KeycloakModelUtils.generateId());
        entity.setName(name);
        entity.setRealmId(realm.getId());
        em.persist(entity);
        em.flush();

        Tenant tenant = new TenantAdapter(session, realm, em, entity);
        tenant.grantMembership(creator, Set.of(DefaultTenantRole.ADMIN.name()));
        session.getKeycloakSessionFactory().publish(tenantCreatedEvent(realm, tenant));

        return tenant;
    }

    public Optional<Tenant> getById(RealmModel realm, String id) {
        TenantEntity entity = em.find(TenantEntity.class, id);
        if (entity != null && entity.getRealmId().equals(realm.getId())) {
            return Optional.of(new TenantAdapter(session, realm, em, entity));
        } else {
            return Optional.empty();
        }
    }

    public Tenant.TenantCreatedEvent tenantCreatedEvent(RealmModel realm, Tenant tenant) {
        return new Tenant.TenantCreatedEvent() {
            @Override
            public Tenant getTenant() {
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

    public Tenant.TenantDeletedEvent tenantDeletedEvent(RealmModel realm, Tenant tenant) {
        return new Tenant.TenantDeletedEvent() {
            @Override
            public Tenant getTenant() {
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
