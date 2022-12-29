package dev.sultanov.keycloak.multitenancy.resource.representation;

import java.util.HashSet;
import java.util.Set;
import lombok.Data;

@Data
public class TenantInvitationRepresentation {

    private String id;
    private String tenantId;
    private String email;
    private Set<String> roles = new HashSet<>();
    private String invitedBy;
}
