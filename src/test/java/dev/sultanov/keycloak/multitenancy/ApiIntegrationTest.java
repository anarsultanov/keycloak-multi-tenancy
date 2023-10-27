package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantMembershipRepresentation;
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
    void admin_shouldBeAbleToRevokeMembership_whenUserAcceptsInvitation() {
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

        var userMembership = tenantResource.memberships().listMemberships("", null, null).stream()
                .filter(membership -> membership.getUser().getEmail().equalsIgnoreCase(user.getUserData().getEmail()))
                .findFirst()
                .orElseThrow();

        // when
        try (var response = tenantResource.memberships().revokeMembership(userMembership.getId())) {

            // then
            assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NO_CONTENT);
            assertThat(tenantResource.memberships().listMemberships("", null, null))
                    .extracting(TenantMembershipRepresentation::getUser)
                    .extracting(UserRepresentation::getEmail)
                    .extracting(String::toLowerCase)
                    .containsExactly(adminUser.getUserData().getEmail().toLowerCase());
        }
    }
}
