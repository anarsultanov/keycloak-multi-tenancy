package dev.sultanov.keycloak.multitenancy.model.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

@Entity
@Table(name = "TENANT", uniqueConstraints = {@UniqueConstraint(columnNames = {"NAME", "REALM_ID"})})
@NamedQuery(name = "getTenantsByRealmId", query = "SELECT t FROM TenantEntity t WHERE t.realmId = :realmId")
public class TenantEntity {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "REALM_ID", nullable = false)
    private String realmId;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "tenant")
    private Collection<TenantMembershipEntity> memberships = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "tenant")
    private Collection<TenantInvitationEntity> invitations = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public Collection<TenantMembershipEntity> getMemberships() {
        return memberships;
    }

    public void setMemberships(Collection<TenantMembershipEntity> memberships) {
        this.memberships = memberships;
    }

    public Collection<TenantInvitationEntity> getInvitations() {
        return invitations;
    }

    public void setInvitations(Collection<TenantInvitationEntity> invitations) {
        this.invitations = invitations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TenantEntity that = (TenantEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
