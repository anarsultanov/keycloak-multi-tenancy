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

    @Schema
    private String locale;

    @Schema(required = true)
    private Set<String> roles = new HashSet<>();

    @Schema(readOnly = true)
    private String invitedBy;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTenantId() {
		return tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public Set<String> getRoles() {
		return roles;
	}

	public void setRoles(Set<String> roles) {
		this.roles = roles;
	}

	public String getInvitedBy() {
		return invitedBy;
	}

	public void setInvitedBy(String invitedBy) {
		this.invitedBy = invitedBy;
	}
    
    
}
