package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import dev.sultanov.keycloak.multitenancy.support.BaseIntegrationTest;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakAdminCli;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;

public class TenantCreationRbacIntegrationTest extends BaseIntegrationTest {

    private static final String ATTRIBUTE_NAME = "requiredRoleForTenantCreation";
    private static final String REQUIRED_ROLE = "create-tenant";

    private KeycloakAdminCli keycloakAdminClient;

    @BeforeEach
    void setUp() {
        keycloakAdminClient = KeycloakAdminCli.forMainRealm();
    }

    @Test
    void user_shouldCreateTenant_whenRoleIsRequiredAndPresent() {
        // given
        addRealmAttribute(ATTRIBUTE_NAME, REQUIRED_ROLE);
        var user = keycloakAdminClient.createVerifiedUser();
        user.createTenant(); // complete "create-tenant" required action
        keycloakAdminClient.assignClientRoleToUser(user.getClientId(), REQUIRED_ROLE, user.getUserId());

        // when
        var tenantRepresentation = new TenantRepresentation();
        tenantRepresentation.setName("Tenant-" + UUID.randomUUID());
        try (var response = user.tenantsResource().createTenant(tenantRepresentation)) {

            // then
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        } finally {
            removeRealmAttribute(ATTRIBUTE_NAME);
        }
    }

    @Test
    void user_shouldFailToCreateTenant_whenRoleIsRequiredButMissing() {
        // given
        addRealmAttribute(ATTRIBUTE_NAME, REQUIRED_ROLE);
        var user = keycloakAdminClient.createVerifiedUser();
        user.createTenant(); // complete "create-tenant" required action

        // when
        var tenantRepresentation = new TenantRepresentation();
        tenantRepresentation.setName("Tenant-" + UUID.randomUUID());
        try (var response = user.tenantsResource().createTenant(tenantRepresentation)) {

            // then
            assertThat(response.getStatus()).isEqualTo(403);
        } finally {
            removeRealmAttribute(ATTRIBUTE_NAME);
        }
    }

    @Test
    void user_shouldCreateTenant_whenRoleIsNotRequired() {
        // given
        var user = keycloakAdminClient.createVerifiedUser();
        user.createTenant(); // complete "create-tenant" required action

        // when
        var tenantRepresentation = new TenantRepresentation();
        tenantRepresentation.setName("Tenant-" + UUID.randomUUID());
        try (var response = user.tenantsResource().createTenant(tenantRepresentation)) {

            // then
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }
    }

    private void addRealmAttribute(String key, String value) {
        var realmRepresentation = keycloakAdminClient.getRealmResource().toRepresentation();
        var attributes = new HashMap<>(realmRepresentation.getAttributesOrEmpty());
        attributes.put(key, value);
        realmRepresentation.setAttributes(attributes);

        keycloakAdminClient.getRealmResource().update(realmRepresentation);
    }

    private void removeRealmAttribute(String key) {
        var realmRepresentation = keycloakAdminClient.getRealmResource().toRepresentation();
        Map<String, String> attributes = new HashMap<>(realmRepresentation.getAttributesOrEmpty());
        attributes.remove(key);
        realmRepresentation.setAttributes(attributes);

        keycloakAdminClient.getRealmResource().update(realmRepresentation);
    }
}
