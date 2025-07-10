package dev.sultanov.keycloak.multitenancy.resource.representation;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.Set;

@Schema
@Data
public class TenantGroupOwnershipRepresentation {

    @Schema(readOnly = true)
    private String id;

    @Schema(readOnly = true,
            properties = {
            @SchemaProperty(name = "id", type = SchemaType.STRING),
            @SchemaProperty(name = "name", type = SchemaType.STRING),
    })
    private GroupRepresentation group;

    @Schema(required = true)
    private Set<String> roles;
}
