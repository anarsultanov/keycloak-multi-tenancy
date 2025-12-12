package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import dev.sultanov.keycloak.multitenancy.support.BaseIntegrationTest;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakAdminCli;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.ws.rs.ProcessingException;
import org.keycloak.admin.client.CreatedResponseUtil;

public class TenantAttributesTest extends BaseIntegrationTest {

    private KeycloakAdminCli keycloakAdminClient;

    @BeforeEach
    void setUp() {
        keycloakAdminClient = KeycloakAdminCli.forMainRealm();
    }

    @Test
    void shouldCreateTenantWithAttributes() {
        // given
        var user = keycloakAdminClient.createVerifiedUser();
        user.createTenant(); // complete "create-tenant" required action
        var tenantRequest = new TenantRepresentation();
        tenantRequest.setName("Tenant-" + UUID.randomUUID());
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("department", List.of("IT"));
        attributes.put("location", List.of("New York"));
        tenantRequest.setAttributes(attributes);

        // when
        var tenantsResource = user.tenantsResource();
        var response = tenantsResource.createTenant(tenantRequest);
        var tenantId = CreatedResponseUtil.getCreatedId(response);
        var tenantResource = tenantsResource.getTenantResource(tenantId);

        // then
        var createdTenant = tenantResource.toRepresentation();
        assertThat(createdTenant.getAttributes())
                .containsEntry("department", List.of("IT"))
                .containsEntry("location", List.of("New York"));
    }

    @Test
    void shouldUpdateTenantAttributes() {
        // given
        var user = keycloakAdminClient.createVerifiedUser();
        var tenantResource = user.createTenant();

        var updateRequest = new TenantRepresentation();
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("department", List.of("Sales"));
        attributes.put("location", List.of("London"));
        updateRequest.setAttributes(attributes);

        // when
        try (var response = tenantResource.updateTenant(updateRequest)) {
            assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);
        }

        // then
        var updatedTenant = tenantResource.toRepresentation();
        assertThat(updatedTenant.getAttributes())
                .containsEntry("department", List.of("Sales"))
                .containsEntry("location", List.of("London"));
    }

    @Test
    void shouldRemoveAttributesWhenUpdatingWithEmptyMap() {
        // given
        var user = keycloakAdminClient.createVerifiedUser();
        var tenantResource = user.createTenant();

        // First add some attributes
        var addAttributesRequest = new TenantRepresentation();
        Map<String, List<String>> initialAttributes = new HashMap<>();
        initialAttributes.put("department", List.of("IT"));
        addAttributesRequest.setAttributes(initialAttributes);
        tenantResource.updateTenant(addAttributesRequest);

        // Then update with empty map
        var updateRequest = new TenantRepresentation();
        updateRequest.setAttributes(new HashMap<>());

        // when
        try (var response = tenantResource.updateTenant(updateRequest)) {
            assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);
        }

        // then
        var updatedTenant = tenantResource.toRepresentation();
        assertThat(updatedTenant.getAttributes()).isEmpty();
    }

    @Test
    void shouldSearchTenantsByAttribute() {
        // given
        var user = keycloakAdminClient.createVerifiedUser();
        user.createTenant(); // complete "create-tenant" required action

        var tenantRequest = new TenantRepresentation();
        tenantRequest.setName("IT Tenant-" + UUID.randomUUID());
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("department", List.of("IT"));
        tenantRequest.setAttributes(attrs);
        var tenantsResource = user.tenantsResource();
        tenantsResource.createTenant(tenantRequest);

        // when
        var tenants = tenantsResource.listTenants(null, "department:IT", null, null);

        // then
        assertThat(tenants)
                .hasSize(1)
                .extracting(TenantRepresentation::getName)
                .allMatch(name -> name.startsWith("IT Tenant"));
    }

    @Test
    void shouldFailToCreateTenantWithEmptyAttributeKey() {
        // given
        var user = keycloakAdminClient.createVerifiedUser();
        var tenantRequest = new TenantRepresentation();
        tenantRequest.setName("Test Tenant" + UUID.randomUUID());
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("", List.of("value"));
        tenantRequest.setAttributes(attributes);

        // when/then
        assertThatThrownBy(() -> user.tenantsResource().createTenant(tenantRequest))
                .isInstanceOf(ProcessingException.class)
                .hasMessageContaining("HTTP 400 Bad Request");
    }

    @Test
    void shouldFailToCreateTenantWithEmptyAttributeValue() {
        // given
        var user = keycloakAdminClient.createVerifiedUser();
        var tenantRequest = new TenantRepresentation();
        tenantRequest.setName("Test Tenant" + UUID.randomUUID());
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("key", List.of());
        tenantRequest.setAttributes(attributes);

        // when/then
        assertThatThrownBy(() -> user.tenantsResource().createTenant(tenantRequest))
                .isInstanceOf(ProcessingException.class)
                .hasMessageContaining("HTTP 400 Bad Request");
    }

    @Test
    void shouldFailToCreateTenantWithEmptyAttributeListValue() {
        // given
        var user = keycloakAdminClient.createVerifiedUser();
        var tenantRequest = new TenantRepresentation();
        tenantRequest.setName("Test Tenant" + UUID.randomUUID());
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("key", List.of(""));
        tenantRequest.setAttributes(attributes);

        // when/then
        assertThatThrownBy(() -> user.tenantsResource().createTenant(tenantRequest))
                .isInstanceOf(ProcessingException.class)
                .hasMessageContaining("HTTP 400 Bad Request");
    }

    @Test
    void shouldStoreShortAttributeValueInValueField() {
        // given
        var user = keycloakAdminClient.createVerifiedUser();
        user.createTenant(); // complete "create-tenant" required action
        var tenantRequest = new TenantRepresentation();
        tenantRequest.setName("Tenant-" + UUID.randomUUID());

        String shortValue = "x".repeat(250);
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("test", List.of(shortValue));
        tenantRequest.setAttributes(attributes);

        // when
        var tenantsResource = user.tenantsResource();
        var response = tenantsResource.createTenant(tenantRequest);
        var tenantId = CreatedResponseUtil.getCreatedId(response);
        var tenantResource = tenantsResource.getTenantResource(tenantId);

        // then
        var createdTenant = tenantResource.toRepresentation();
        assertThat(createdTenant.getAttributes())
                .containsEntry("test", List.of(shortValue));
    }

    @Test
    void shouldStoreLongAttributeValueInLongValueField() {
        // given
        var user = keycloakAdminClient.createVerifiedUser();
        user.createTenant(); // complete "create-tenant" required action
        var tenantRequest = new TenantRepresentation();
        tenantRequest.setName("Tenant-" + UUID.randomUUID());

        String longValue = "x".repeat(260); // Exceeds max length for value field
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("test", List.of(longValue));
        tenantRequest.setAttributes(attributes);

        // when
        var tenantsResource = user.tenantsResource();
        var response = tenantsResource.createTenant(tenantRequest);
        var tenantId = CreatedResponseUtil.getCreatedId(response);
        var tenantResource = tenantsResource.getTenantResource(tenantId);

        // then
        var createdTenant = tenantResource.toRepresentation();
        assertThat(createdTenant.getAttributes())
                .containsEntry("test", List.of(longValue));
    }

    @Test
    void shouldPreserveAttributesWhenUpdatingWithoutAttributesField() {
        // given
        var user = keycloakAdminClient.createVerifiedUser();
        var tenantResource = user.createTenant();

        var addAttributesRequest = new TenantRepresentation();
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("department", List.of("Engineering"));
        attributes.put("location", List.of("San Francisco"));
        addAttributesRequest.setAttributes(attributes);
        tenantResource.updateTenant(addAttributesRequest);

        // when
        var updateRequest = new TenantRepresentation();
        updateRequest.setName("Updated-" + UUID.randomUUID());

        try (var response = tenantResource.updateTenant(updateRequest)) {
            assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);
        }

        // then
        var updatedTenant = tenantResource.toRepresentation();
        assertThat(updatedTenant.getAttributes())
                .containsEntry("department", List.of("Engineering"))
                .containsEntry("location", List.of("San Francisco"));
    }
}