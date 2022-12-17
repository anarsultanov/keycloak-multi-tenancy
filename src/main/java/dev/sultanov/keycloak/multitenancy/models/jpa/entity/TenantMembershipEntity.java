package dev.sultanov.keycloak.multitenancy.models.jpa.entity;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Table(name = "TENANT_MEMBERSHIP", uniqueConstraints = {@UniqueConstraint(columnNames = {"TENANT_ID", "USER_ID"})})
@Entity
public class TenantMembershipEntity {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TENANT_ID")
    private TenantEntity tenant;

    @Column(name = "USER_ID")
    private String userId;

    @ElementCollection
    @Column(name = "ROLE")
    @CollectionTable(name = "TENANT_MEMBERSHIP_ROLE", joinColumns = {@JoinColumn(name = "TENANT_MEMBERSHIP_ID")})
    private Set<String> roles = new HashSet<>();

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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TenantMembershipEntity that = (TenantMembershipEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
