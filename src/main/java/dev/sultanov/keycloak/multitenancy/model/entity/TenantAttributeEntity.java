package dev.sultanov.keycloak.multitenancy.model.entity;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import org.hibernate.annotations.Nationalized;
import org.keycloak.storage.jpa.JpaHashUtils;

@NamedQueries({
    @NamedQuery(name="deleteTenantAttributesByRealm", 
        query="delete from TenantAttributeEntity attr where attr.tenant IN (select t from TenantEntity t where t.realmId=:realmId)"),
    @NamedQuery(name="deleteTenantAttributesByNameAndTenant", 
        query="delete from TenantAttributeEntity attr where attr.tenant.id = :tenantId and attr.name = :name"),
    @NamedQuery(name="deleteTenantAttributesByNameAndTenantOtherThan", 
        query="delete from TenantAttributeEntity attr where attr.tenant.id = :tenantId and attr.name = :name and attr.id <> :attrId")
})
@Table(name="TENANT_ATTRIBUTE")
@Entity
public class TenantAttributeEntity {

    @Id
    @Column(name="ID", length = 36)
    @Access(AccessType.PROPERTY)
    protected String id;

    @ManyToOne(fetch= FetchType.LAZY)
    @JoinColumn(name = "TENANT_ID")
    protected TenantEntity tenant;

    @Column(name = "NAME")
    protected String name;

    @Nationalized
    @Column(name = "VALUE")
    protected String value;

    @Column(name = "LONG_VALUE_HASH")
    private byte[] longValueHash;

    @Column(name = "LONG_VALUE_HASH_LOWER_CASE")
    private byte[] longValueHashLowerCase;

    @Nationalized
    @Column(name = "LONG_VALUE")
    private String longValue;

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

    public String getValue() {
        if (value != null && longValue != null) {
            throw new IllegalStateException(String.format("Tenant with id %s should not have set both `value` and `longValue` for attribute %s.", tenant.getId(), name));
        }
        return value != null ? value : longValue;
    }

    public void setValue(String value) {
        if (value == null) {
            this.value = null;
            this.longValue = null;
            this.longValueHash = null;
            this.longValueHashLowerCase = null;
        } else if (value.length() > 255) {
            this.value = null;
            this.longValue = value;
            this.longValueHash = JpaHashUtils.hashForAttributeValue(value);
            this.longValueHashLowerCase = JpaHashUtils.hashForAttributeValueLowerCase(value);
        } else {
            this.value = value;
            this.longValue = null;
            this.longValueHash = null;
            this.longValueHashLowerCase = null;
        }
    }

    public TenantEntity getTenant() {
        return tenant;
    }

    public void setTenant(TenantEntity tenant) {
        this.tenant = tenant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!(o instanceof TenantAttributeEntity)) return false;

        TenantAttributeEntity that = (TenantAttributeEntity) o;

        if (!id.equals(that.getId())) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}