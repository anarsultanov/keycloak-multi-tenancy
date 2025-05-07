package dev.sultanov.keycloak.multitenancy.model.entity;

public class SwitchTenantRequest {
    private String tenantId;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
