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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "TENANT_INVITATION", uniqueConstraints = {@UniqueConstraint(columnNames = {"TENANT_ID", "EMAIL"})})
@NamedQuery(name = "getInvitationsByRealmAndEmail",
        query = "SELECT i FROM TenantInvitationEntity i WHERE i.tenant in (SELECT o FROM TenantEntity o WHERE o.realmId = :realmId) AND lower(i.email) = lower(:search)")
public class TenantInvitationEntity {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TENANT_ID")
    private TenantEntity tenant;

    @Email
    @Column(name = "EMAIL")
    private String email;

    @ElementCollection
    @Column(name = "ROLE")
    @CollectionTable(name = "TENANT_INVITATION_ROLE", joinColumns = {@JoinColumn(name = "TENANT_INVITATION_ID")})
    private Set<String> roles = new HashSet<>();

    @Column(name = "INVITED_BY")
    private String invitedBy;

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public String getInvitedBy() {
        return invitedBy;
    }

    public void setInvitedBy(String invitedBy) {
        this.invitedBy = invitedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TenantInvitationEntity that = (TenantInvitationEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
