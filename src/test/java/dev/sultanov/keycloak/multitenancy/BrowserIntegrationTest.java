package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import dev.sultanov.keycloak.multitenancy.support.BaseIntegrationTest;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakAdminCli;
import dev.sultanov.keycloak.multitenancy.support.browser.AccountPage;
import dev.sultanov.keycloak.multitenancy.support.browser.CreateTenantPage;
import dev.sultanov.keycloak.multitenancy.support.browser.ReviewInvitationsPage;
import dev.sultanov.keycloak.multitenancy.support.browser.SelectTenantPage;
import dev.sultanov.keycloak.multitenancy.support.data.TenantData;
import dev.sultanov.keycloak.multitenancy.support.data.UserData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;

public class BrowserIntegrationTest extends BaseIntegrationTest {

    private KeycloakAdminCli keycloakAdminClient;

    @BeforeEach
    void setUp() {
        keycloakAdminClient = KeycloakAdminCli.create();
    }

    @Test
    void user_shouldBePromptedToCreateTenant_whenTheyDontHaveInvitations() {
        var user = keycloakAdminClient.createVerifiedUser();
        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(user.getUserData().getEmail(), user.getUserData().getPassword())
                .signIn();
        assertThat(nextPage).isInstanceOf(CreateTenantPage.class);

        nextPage = ((CreateTenantPage) nextPage).fillTenantData(TenantData.random()).submit();
        assertThat(nextPage).isInstanceOf(AccountPage.class);
        assertThat(((AccountPage) nextPage).getLoggedInUser()).contains(user.getUserData().getFullName());
    }

    @Test
    void user_shouldBePromptedToCreateTenant_whenTheyDeclineInvitation() {
        var user = keycloakAdminClient.createVerifiedUser();
        var invitationTenant = createInvitationFor(user.getUserData());

        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(user.getUserData().getEmail(), user.getUserData().getPassword())
                .signIn();
        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);

        nextPage = ((ReviewInvitationsPage) nextPage).uncheckInvitation(invitationTenant.getName()).accept();
        assertThat(nextPage).isInstanceOf(CreateTenantPage.class);

        nextPage = ((CreateTenantPage) nextPage).fillTenantData(TenantData.random()).submit();
        assertThat(nextPage).isInstanceOf(AccountPage.class);
        assertThat(((AccountPage) nextPage).getLoggedInUser()).contains(user.getUserData().getFullName());
    }

    @Test
    void user_shouldNotBePromptedToCreateTenant_whenTheyAcceptInvitation() {
        var user = keycloakAdminClient.createVerifiedUser();
        createInvitationFor(user.getUserData());

        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(user.getUserData().getEmail(), user.getUserData().getPassword())
                .signIn();
        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);
        nextPage = ((ReviewInvitationsPage) nextPage).accept();

        assertThat(nextPage).isInstanceOf(AccountPage.class);
        assertThat(((AccountPage) nextPage).getLoggedInUser()).contains(user.getUserData().getFullName());
    }

    @Test
    void user_shouldBePromptedToSelectTenant_whenTheyAcceptMultipleInvitations() {
        var user = keycloakAdminClient.createVerifiedUser();
        var invitationTenant1 = createInvitationFor(user.getUserData());
        var invitationTenant2 = createInvitationFor(user.getUserData());

        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(user.getUserData().getEmail(), user.getUserData().getPassword())
                .signIn();
        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);
        nextPage = ((ReviewInvitationsPage) nextPage).accept();

        assertThat(nextPage).isInstanceOf(SelectTenantPage.class);
        assertThat(((SelectTenantPage) nextPage).availableOptions()).containsExactlyInAnyOrder(invitationTenant1.getName(), invitationTenant2.getName());
        nextPage = ((SelectTenantPage) nextPage).select(invitationTenant2.getName()).signIn();

        assertThat(nextPage).isInstanceOf(AccountPage.class);
        assertThat(((AccountPage) nextPage).getLoggedInUser()).contains(user.getUserData().getFullName());
    }

    private TenantData createInvitationFor(UserData inviteeData) {
        var inviter = keycloakAdminClient.createVerifiedUser();
        var tenantResource = inviter.createTenant();

        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(inviteeData.getEmail());
        try (var response = tenantResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }
        return inviter.getTenantData();
    }
}
