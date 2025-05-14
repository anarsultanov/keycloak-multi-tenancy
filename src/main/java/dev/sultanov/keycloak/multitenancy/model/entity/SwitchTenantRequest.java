package dev.sultanov.keycloak.multitenancy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SwitchTenantRequest {
    @NotBlank(message = "Tenant ID is mandatory")
    private String tenantId;
}