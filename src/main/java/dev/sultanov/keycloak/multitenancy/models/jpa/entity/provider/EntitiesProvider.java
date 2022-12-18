package dev.sultanov.keycloak.multitenancy.models.jpa.entity.provider;

import dev.sultanov.keycloak.multitenancy.models.jpa.entity.TenantEntity;
import dev.sultanov.keycloak.multitenancy.models.jpa.entity.TenantInvitationEntity;
import dev.sultanov.keycloak.multitenancy.models.jpa.entity.TenantMembershipEntity;
import java.util.List;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;

public class EntitiesProvider implements JpaEntityProvider {

    @Override
    public List<Class<?>> getEntities() {
        return List.of(
                TenantEntity.class,
                TenantMembershipEntity.class,
                TenantInvitationEntity.class
        );
    }

    @Override
    public String getChangelogLocation() {
        return "META-INF/keycloak-multi-tenancy-changelog-master.xml";
    }

    @Override
    public String getFactoryId() {
        return EntitiesProviderFactory.ID;
    }

    @Override
    public void close() {

    }
}
