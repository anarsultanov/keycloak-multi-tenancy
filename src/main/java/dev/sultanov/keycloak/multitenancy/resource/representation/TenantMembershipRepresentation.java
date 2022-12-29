package dev.sultanov.keycloak.multitenancy.resource.representation;

import java.util.Set;
import lombok.Data;
import org.keycloak.representations.idm.UserRepresentation;

@Data
public class TenantMembershipRepresentation {

    private String id;
    private UserRepresentation user;
    private Set<String> roles;
}
