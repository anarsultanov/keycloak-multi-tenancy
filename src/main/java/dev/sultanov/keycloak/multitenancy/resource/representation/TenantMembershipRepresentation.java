package dev.sultanov.keycloak.multitenancy.resource.representation;

import java.util.Set;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.keycloak.representations.idm.UserRepresentation;

@Schema
@Data
public class TenantMembershipRepresentation {

    @Schema(readOnly = true)
    private String id;

    @Schema(readOnly = true,
            properties = {
            @SchemaProperty(name = "id", type = SchemaType.STRING),
            @SchemaProperty(name = "createdTimestamp", type = SchemaType.NUMBER),
            @SchemaProperty(name = "username", type = SchemaType.STRING),
            @SchemaProperty(name = "enabled", type = SchemaType.BOOLEAN),
            @SchemaProperty(name = "firstName", type = SchemaType.STRING),
            @SchemaProperty(name = "lastName", type = SchemaType.STRING),
            @SchemaProperty(name = "email", type = SchemaType.STRING),
            @SchemaProperty(name = "emailVerified", type = SchemaType.BOOLEAN),
            @SchemaProperty(name = "federationLink", type = SchemaType.STRING)
    })
    private UserRepresentation user;

    @Schema(required = true)
    private Set<String> roles;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public UserRepresentation getUser() {
		return user;
	}

	public void setUser(UserRepresentation user) {
		this.user = user;
	}

	public Set<String> getRoles() {
		return roles;
	}

	public void setRoles(Set<String> roles) {
		this.roles = roles;
	}
    
    
}
