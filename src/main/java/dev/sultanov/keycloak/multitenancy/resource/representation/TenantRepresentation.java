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

    @Schema(description = "Attributes of the tenant")
    private Map<String, List<String>> attributes = new HashMap<>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getRealm() {
		return realm;
	}

	public void setRealm(String realm) {
		this.realm = realm;
	}

	public Map<String, List<String>> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, List<String>> attributes) {
		this.attributes = attributes;
	}
    
    
}
