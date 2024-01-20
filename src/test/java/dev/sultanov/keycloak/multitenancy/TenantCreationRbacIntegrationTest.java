package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import dev.sultanov.keycloak.multitenancy.support.BaseIntegrationTest;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakAdminCli;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;

public class TenantCreationRbacIntegrationTest extends BaseIntegrationTest {

    @Nested
    class WithRequiredRole {

        private static final String TENANT_CREATION_ROLE = "create-tenant";

        @Test
        void runTests() {
            withCustomKeycloak(Map.of("TENANT_CREATION_ROLE", TENANT_CREATION_ROLE), () -> {
                assertThatCanCreateTenantWhenRequiredRoleIsPresent();
                assertThatCannotCreateTenantWhenRequiredRoleIsMissing();
            });
        }

        void assertThatCanCreateTenantWhenRequiredRoleIsPresent() {
            // given
            var keycloakAdminClient = KeycloakAdminCli.forMainRealm();
            var user = keycloakAdminClient.createVerifiedUser();
            user.createTenant(); // complete "create-tenant" required action
            keycloakAdminClient.assignClientRoleToUser(user.getClientId(), TENANT_CREATION_ROLE, user.getUserId());

            // when
            var tenantRepresentation = new TenantRepresentation();
            tenantRepresentation.setName("Tenant-" + UUID.randomUUID());
            try (var response = user.tenantsResource().createTenant(tenantRepresentation)) {

                // then
                assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
            }
        }

        void assertThatCannotCreateTenantWhenRequiredRoleIsMissing() {
            // given
            var keycloakAdminClient = KeycloakAdminCli.forMainRealm();
            var user = keycloakAdminClient.createVerifiedUser();
            user.createTenant(); // complete "create-tenant" required action

            // when
            var tenantRepresentation = new TenantRepresentation();
            tenantRepresentation.setName("Tenant-" + UUID.randomUUID());
            try (var response = user.tenantsResource().createTenant(tenantRepresentation)) {

                // then
                assertThat(response.getStatus()).isEqualTo(401);
            }
        }
    }

    @Nested
    class WithoutRequiredRole {

        @Test
        void user_shouldCreateTenant_whenRoleIsNotRequired() {
            // given
            var keycloakAdminClient = KeycloakAdminCli.forMainRealm();
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
    }
}
