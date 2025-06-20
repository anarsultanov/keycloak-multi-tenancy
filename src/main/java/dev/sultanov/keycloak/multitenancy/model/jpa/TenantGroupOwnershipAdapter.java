package dev.sultanov.keycloak.multitenancy.model.jpa;

import dev.sultanov.keycloak.multitenancy.model.TenantGroupOwnershipModel;
import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.model.entity.TenantGroupOwnershipEntity;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.jpa.JpaModel;

public class TenantGroupOwnershipAdapter implements TenantGroupOwnershipModel, JpaModel<TenantGroupOwnershipEntity> {

    private final KeycloakSession session;
    private final TenantGroupOwnershipEntity groupOwnership;
    private final RealmModel realm;

    public TenantGroupOwnershipAdapter(KeycloakSession session, RealmModel realm, TenantGroupOwnershipEntity groupOwnership) {
        this.session = session;
        this.groupOwnership = groupOwnership;
        this.realm = realm;
    }

    @Override
    public String getId() {
        return groupOwnership.getId();
    }

    @Override
    public TenantModel getTenant() {
        return session.getProvider(TenantProvider.class).getTenantById(realm, groupOwnership.getTenant().getId()).orElseThrow();
    }

    @Override
    public GroupModel getGroup() {
        return session.groups().getGroupById(realm, groupOwnership.getGroup().getId());
    }

    @Override
    public TenantGroupOwnershipEntity getEntity() {
        return groupOwnership;
    }
}
