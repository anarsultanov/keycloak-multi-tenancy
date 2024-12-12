package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantMembershipRepresentation;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import dev.sultanov.keycloak.multitenancy.support.BaseIntegrationTest;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakAdminCli;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakUser;
import dev.sultanov.keycloak.multitenancy.support.api.TenantResource;
import dev.sultanov.keycloak.multitenancy.support.browser.AccountPage;
import dev.sultanov.keycloak.multitenancy.support.browser.ReviewInvitationsPage;
import dev.sultanov.keycloak.multitenancy.util.Constants;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

public class ApiIntegrationTest extends BaseIntegrationTest {

    private static KeycloakAdminCli keycloakAdminClient;

    @BeforeAll
    static void setUpRealm() {
        keycloakAdminClient = KeycloakAdminCli.forMainRealm();
        createTenantsManagementRole();

    }

    private static void createTenantsManagementRole() {
        keycloakAdminClient.getRealmResource().clients()
                .findByClientId(org.keycloak.models.Constants.REALM_MANAGEMENT_CLIENT_ID)
                .stream()
                .map(client -> keycloakAdminClient.getRealmResource().clients().get(client.getId()))
                .findFirst()
                .orElseThrow()
                .roles()
                .create(new RoleRepresentation(Constants.TENANTS_MANAGEMENT_ROLE, null, false));
    }

    private KeycloakUser tenantAdmin;
    private TenantResource tenantResource;
    private TenantRepresentation tenant;

    private KeycloakUser tenantsManager;
    private TenantResource tenantsManagerTenantResource;
    private TenantRepresentation tenantsManagerTenant;

    @BeforeEach
    void setUp() {
        tenantAdmin = keycloakAdminClient.createVerifiedUser();
        tenantResource = tenantAdmin.createTenant();
        tenant = tenantResource.toRepresentation();

        tenantsManager = keycloakAdminClient.createVerifiedUser();
        tenantsManagerTenantResource = tenantsManager.createTenant();
        tenantsManagerTenant = tenantsManagerTenantResource.toRepresentation();
        assignTenantsManagementRole(tenantsManager);
    }

    @SuppressWarnings("resource")
    @AfterEach
    void tearDown() {
        tenantResource.deleteTenant();
        keycloakAdminClient.getRealmResource().users().delete(tenantAdmin.getUserId());

        tenantsManagerTenantResource.deleteTenant();
        keycloakAdminClient.getRealmResource().users().delete(tenantsManager.getUserId());
    }

    @Test
    void adminRevokesMembership_shouldSucceed_whenUserHasAcceptedInvitation() {
        // given
        var user = keycloakAdminClient.createVerifiedUser();

        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(user.getUserData().getEmail());
        try (var response = tenantResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }

        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(user.getUserData().getEmail(), user.getUserData().getPassword())
                .signIn();
        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);
        ((ReviewInvitationsPage) nextPage).accept();

        var userMembership = tenantResource.memberships().listMemberships(user.getUserData().getEmail(), null, null).stream()
                .filter(membership -> membership.getUser().getEmail().equalsIgnoreCase(user.getUserData().getEmail()))
                .findFirst()
                .orElseThrow();

        // when
        try (var response = tenantResource.memberships().revokeMembership(userMembership.getId())) {

            // then
            assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);
            assertThat(tenantResource.memberships().listMemberships(null, null, null))
                    .extracting(TenantMembershipRepresentation::getUser)
                    .extracting(UserRepresentation::getEmail)
                    .extracting(String::toLowerCase)
                    .containsExactly(tenantAdmin.getUserData().getEmail().toLowerCase());
        }
    }

    @Test
    void adminUpdatesTenant_shouldReturnNoContent_whenTenantIsSuccessfullyUpdated() {
        // given
        var newName = "new-name";

        // when
        var request = new TenantRepresentation();
        request.setName(newName);
        try (var response = tenantResource.updateTenant(request)) {

            // then
            assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);
            assertThat(tenantResource.toRepresentation().getName()).isEqualTo(newName);
        }
    }

    @Test
    void adminUpdatesTenant_shouldReturnConflict_whenUpdatedTenantNameAlreadyExists() {
        // given
        var existingTenantName = keycloakAdminClient.createVerifiedUser().createTenant().toRepresentation().getName();

        // when
        var request = new TenantRepresentation();
        request.setName(existingTenantName);
        try (var response = tenantResource.updateTenant(request)) {

            // then
            assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_CONFLICT);
        }
    }

    @Test
    void userRemoval_shouldRemoveTheirMembership() {
        // given
        var user = keycloakAdminClient.createVerifiedUser();

        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(user.getUserData().getEmail());
        try (var response = tenantResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }

        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(user.getUserData().getEmail(), user.getUserData().getPassword())
                .signIn();
        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);
        ((ReviewInvitationsPage) nextPage).accept();

        // when
        try (var response = keycloakAdminClient.getRealmResource().users().delete(user.getUserId())) {

            //then
            assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);
            assertThat(tenantResource.memberships().listMemberships(null, null, null))
                    .noneMatch(membership -> membership.getUser().getEmail().equalsIgnoreCase(user.getUserData().getEmail()));
        }
    }

    @Test
    void tenantsManager_shouldListAllTenants() {
        // when
        var tenants = tenantsManager.tenantsResource().listTenants(null, null, null);

        // then
        assertThat(tenants).extracting(TenantRepresentation::getId).containsExactlyInAnyOrder(
                tenant.getId(),
                tenantsManagerTenant.getId()
        );
    }

    @Test
    void tenantsManager_shouldListMembers_whenTheyAreNotMemberOfTenant() {
        // when
        var memberships = tenantsManager.tenantsResource()
                .getTenantResource(tenant.getId())
                .memberships()
                .listMemberships(null, null, null);

        // then
        assertThat(memberships).extracting(TenantMembershipRepresentation::getUser)
                .extracting(UserRepresentation::getEmail)
                .containsExactly(tenantAdmin.getUserData().getEmail());
    }

    @Test
    void tenantsManager_shouldUpdateTenant_whenTheyAreNotMemberOfTenant() {
        // given
        var newName = "new-name";

        // when
        var request = new TenantRepresentation();
        request.setName(newName);
        try (var response = tenantsManager.tenantsResource().getTenantResource(tenant.getId()).updateTenant(request)) {

            // then
            assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);
        }

        // and user1 should see the updated tenant name
        assertThat(tenantResource.toRepresentation().getName()).isEqualTo(newName);
    }

    private void assignTenantsManagementRole(KeycloakUser user) {
        keycloakAdminClient.assignClientRoleToUser(
                org.keycloak.models.Constants.REALM_MANAGEMENT_CLIENT_ID,
                Constants.TENANTS_MANAGEMENT_ROLE,
                user.getUserId()
        );
    }
}
