package dev.sultanov.keycloak.multitenancy.resource.representation;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema
@Data
public class TenantRepresentation {

    @Schema(readOnly = true)
    private String id;

    @Schema(required = true)
    private String name;

    @Schema(readOnly = true)
    private String realm;

}
