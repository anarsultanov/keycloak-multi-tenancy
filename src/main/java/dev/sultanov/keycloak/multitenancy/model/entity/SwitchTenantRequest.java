package dev.sultanov.keycloak.multitenancy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SwitchTenantRequest {
    private String tenantId;
}
