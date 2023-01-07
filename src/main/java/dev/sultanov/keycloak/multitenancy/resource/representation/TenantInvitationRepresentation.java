package dev.sultanov.keycloak.multitenancy.resource.representation;

import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema
@Data
public class TenantInvitationRepresentation {

    @Schema(readOnly = true)
    private String id;

    @Schema(readOnly = true)
    private String tenantId;

    @Schema(required = true)
    private String email;

    @Schema(required = true)
    private Set<String> roles = new HashSet<>();

    @Schema(readOnly = true)
    private String invitedBy;
}
