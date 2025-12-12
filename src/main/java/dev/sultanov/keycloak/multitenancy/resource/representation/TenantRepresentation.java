package dev.sultanov.keycloak.multitenancy.resource.representation;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.Map;
import java.util.List;

@Schema
@Data
public class TenantRepresentation {

    @Schema(readOnly = true)
    private String id;

    @Schema(required = true)
    private String name;

    @Schema(readOnly = true)
    private String realm;

    @Schema(description = "Attributes of the tenant")
    private Map<String, List<String>> attributes;
}
