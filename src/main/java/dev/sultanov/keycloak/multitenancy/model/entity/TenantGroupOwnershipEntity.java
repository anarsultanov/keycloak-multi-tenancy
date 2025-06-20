package dev.sultanov.keycloak.multitenancy.model.entity;

import jakarta.persistence.*;
import org.keycloak.models.jpa.entities.GroupEntity;

import java.util.Objects;

@Table(name = "TENANT_GROUP_OWNERSHIP", uniqueConstraints = {@UniqueConstraint(columnNames = {"TENANT_ID", "GROUP_ID"})})
@Entity
@NamedQueries({
        @NamedQuery(name = "getGroupOwnershipsByRealmIdAndGroupId", query = "SELECT m FROM TenantGroupOwnershipEntity m WHERE m.tenant.realmId = :realmId AND m.group.id = :userId"),
        @NamedQuery(name = "getGroupOwnershipsByTenantId", query = "SELECT m FROM TenantGroupOwnershipEntity m WHERE m.tenant.id = :tenantId"),
        @NamedQuery(name = "getGroupOwnershipsByTenantIdAndGroupId", query = "SELECT m FROM TenantGroupOwnershipEntity m WHERE m.tenant.id = :tenantId AND m.group.id = :userId"),
        @NamedQuery(name = "getGroupOwnershipsByTenantIdAndGroupName", query = "SELECT m FROM TenantGroupOwnershipEntity m WHERE m.tenant.id = :tenantId AND m.group.name = :name")
})
public class TenantGroupOwnershipEntity {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TENANT_ID")
    private TenantEntity tenant;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GROUP_ID")
    private GroupEntity group;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TenantEntity getTenant() {
        return tenant;
    }

    public void setTenant(TenantEntity tenant) {
        this.tenant = tenant;
    }

    public GroupEntity getGroup() {
        return group;
    }

    public void setGroup(GroupEntity user) {
        this.group = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TenantGroupOwnershipEntity that = (TenantGroupOwnershipEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
