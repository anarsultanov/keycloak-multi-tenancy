package dev.sultanov.keycloak.multitenancy.dto;

import java.util.List;
import java.util.Map;

public class TenantDto {

    private String id;
    private String name;
    private String realm;
    private Map<String, List<String>> attributes;

    public TenantDto(String id, String name, String realm, Map<String, List<String>> attributes) {
        this.id = id;
        this.name = name;
        this.realm = realm;
        this.attributes = attributes;
    }

    // Getters and setters (or use Lombok if preferred)
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRealm() {
        return realm;
    }

    public Map<String, List<String>> getAttributes() {
        return attributes;
    }
}
