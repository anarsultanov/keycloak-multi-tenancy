package dev.sultanov.keycloak.multitenancy.model.jpa;

import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantMembershipEntity;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.jpa.JpaModel;

public class TenantMembershipAdapter implements TenantMembershipModel, JpaModel<TenantMembershipEntity> {

    private final KeycloakSession session;
    private final TenantMembershipEntity membership;
    private final EntityManager em;
    private final RealmModel realm;

    public TenantMembershipAdapter(KeycloakSession session, RealmModel realm, EntityManager em, TenantMembershipEntity membership) {
        this.session = session;
        this.em = em;
        this.membership = membership;
        this.realm = realm;
    }

    @Override
    public String getId() {
        return membership.getId();
    }

    @Override
    public TenantModel getTenant() {
        return session.getProvider(TenantProvider.class).getTenantById(realm, membership.getTenant().getId()).orElseThrow();
    }

    @Override
    public UserModel getUser() {
        return session.users().getUserById(realm, membership.getUser().getId());
    }

    @Override
    public Set<String> getRoles() {
        return membership.getRoles();
    }

    @Override
    public void updateRoles(Set<String> roles) {
        membership.getRoles().clear();
        membership.getRoles().addAll(roles);
    }

    @Override
    public TenantMembershipEntity getEntity() {
        return membership;
    }
}
