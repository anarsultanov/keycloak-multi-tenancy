package dev.sultanov.keycloak.multitenancy.resource.representation;

import java.util.Set;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.keycloak.representations.idm.UserRepresentation;

@Schema
@Data
public class TenantMembershipRepresentation {

    @Schema(readOnly = true)
    private String id;

    @Schema(readOnly = true)
    private UserRepresentation user;

    @Schema(required = true)
    private Set<String> roles;
}
