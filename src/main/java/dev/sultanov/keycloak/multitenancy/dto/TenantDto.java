package dev.sultanov.keycloak.multitenancy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class TenantDto {

    private String id;
    private String name;
    private String realm;
    private String mobileNumber;
    private String countryCode;
    private String status;
    private Map<String, List<String>> attributes;
}