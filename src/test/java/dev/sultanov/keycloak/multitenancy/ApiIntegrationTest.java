package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantMembershipRepresentation;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import dev.sultanov.keycloak.multitenancy.support.BaseIntegrationTest;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakAdminCli;
import dev.sultanov.keycloak.multitenancy.support.browser.AccountPage;
import dev.sultanov.keycloak.multitenancy.support.browser.ReviewInvitationsPage;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.representations.idm.UserRepresentation;

public class ApiIntegrationTest extends BaseIntegrationTest {

    private KeycloakAdminCli keycloakAdminClient;

    @BeforeEach
    void setUp() {
        keycloakAdminClient = KeycloakAdminCli.forMainRealm();
    }

    @Test
    void adminRevokesMembership_shouldSucceed_whenUserHasAcceptedInvitation() {
        // given
        var adminUser = keycloakAdminClient.createVerifiedUser();
        var tenantResource = adminUser.createTenant();

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
                    .containsExactly(adminUser.getUserData().getEmail().toLowerCase());
        }
    }

    @Test
    void adminUpdatesTenant_shouldReturnNoContent_whenTenantIsSuccessfullyUpdated() {
        // given
        var adminUser = keycloakAdminClient.createVerifiedUser();
        var tenantResource = adminUser.createTenant();
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
        var adminUser = keycloakAdminClient.createVerifiedUser();
        var tenantResource = adminUser.createTenant();
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
        var adminUser = keycloakAdminClient.createVerifiedUser();
        var tenantResource = adminUser.createTenant();

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
}
