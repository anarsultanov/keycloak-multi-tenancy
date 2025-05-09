package dev.sultanov.keycloak.multitenancy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TenantDto {

    private String id;
    private String name;
    private String realm;
    private Map<String, List<String>> attributes;
}