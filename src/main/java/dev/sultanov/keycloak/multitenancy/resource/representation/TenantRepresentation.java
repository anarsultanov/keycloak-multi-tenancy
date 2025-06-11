package dev.sultanov.keycloak.multitenancy.resource.representation;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.Map;
import java.util.HashMap;
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

    @Schema(description = "Mobile number of the tenant")
    private String mobileNumber;

    // Add these new fields
    @Schema(description = "Country code of the tenant's mobile number")
    private String countryCode;

    @Schema(description = "Status of the tenant (e.g., ACTIVE, INACTIVE, PENDING)")
    private String status;

    @Schema(description = "Attributes of the tenant")
    private Map<String, List<String>> attributes = new HashMap<>();
}