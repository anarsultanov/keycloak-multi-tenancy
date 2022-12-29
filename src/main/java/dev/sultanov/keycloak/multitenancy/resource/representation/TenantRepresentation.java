package dev.sultanov.keycloak.multitenancy.resource.representation;

import lombok.Data;

@Data
public class TenantRepresentation {

    private String id;
    private String name;
    private String realm;

}
