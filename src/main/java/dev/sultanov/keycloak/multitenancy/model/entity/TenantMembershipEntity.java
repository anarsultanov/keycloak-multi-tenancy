package dev.sultanov.keycloak.multitenancy.model.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.keycloak.models.jpa.entities.UserEntity;

@Table(name = "TENANT_MEMBERSHIP", uniqueConstraints = {@UniqueConstraint(columnNames = {"TENANT_ID", "USER_ID"})})
@Entity
@NamedQuery(name = "getMembershipsByRealmAndUserId",
        query = "SELECT m FROM TenantMembershipEntity m WHERE m.tenant.realmId = :realmId AND m.user.id = :userId")
public class TenantMembershipEntity {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TENANT_ID")
    private TenantEntity tenant;

    @OneToOne(fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "USER_ID")
    private UserEntity user;

    @ElementCollection(fetch = FetchType.EAGER)
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

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
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
