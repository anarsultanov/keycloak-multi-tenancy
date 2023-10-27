package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import dev.sultanov.keycloak.multitenancy.support.BaseIntegrationTest;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakAdminCli;
import dev.sultanov.keycloak.multitenancy.support.browser.AccountPage;
import dev.sultanov.keycloak.multitenancy.support.browser.CreateTenantPage;
import dev.sultanov.keycloak.multitenancy.support.browser.ReviewInvitationsPage;
import dev.sultanov.keycloak.multitenancy.support.data.UserData;
import dev.sultanov.keycloak.multitenancy.support.mail.MailhogClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;

public class MailIntegrationTest extends BaseIntegrationTest {

    private KeycloakAdminCli keycloakAdminClient;
    private MailhogClient mailhogClient;

    @BeforeEach
    void setUp() {
        keycloakAdminClient = KeycloakAdminCli.forMainRealm();
        mailhogClient = MailhogClient.create();
    }

    @Test
    void invitee_shouldReceiveEmail_whenTheyAreInvitedToJoinTenant() {
        // given
        var tenantResource = keycloakAdminClient.createVerifiedUser().createTenant();
        var invitee = UserData.random();

        // when
        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(invitee.getEmail());
        try (var response = tenantResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }

        // then
        var emailsForRecipient = mailhogClient.findAllForRecipient(invitee.getEmail());
        assertThat(emailsForRecipient).hasSize(1);
        assertThat(emailsForRecipient.get(0).body()).contains("You have been invited to join");
    }

    @Test
    void invitee_shouldReceiveEmailInUsersLanguage_whenUserExists() {
        // given
        var tenantResource = keycloakAdminClient.createVerifiedUser().createTenant();
        var invitee = keycloakAdminClient.createVerifiedUser(Map.of("locale", List.of("sv-SE")));

        // when
        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(invitee.getUserData().getEmail());
        invitation.setLocale("sv-SE");
        try (var response = tenantResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }

        // then
        var emailsForRecipient = mailhogClient.findAllForRecipient(invitee.getUserData().getEmail());
        assertThat(emailsForRecipient).hasSize(1);
        assertThat(emailsForRecipient.get(0).body()).contains("Du har blivit inbjuden att");
    }

    @Test
    void invitee_shouldReceiveEmailInSpecifiedLanguage_whenRequestContainsLocale() {
        // given
        var tenantResource = keycloakAdminClient.createVerifiedUser().createTenant();
        var invitee = keycloakAdminClient.createVerifiedUser(Map.of("locale", List.of("sv-SE")));

        // when
        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(invitee.getUserData().getEmail());
        invitation.setLocale("en-US");
        try (var response = tenantResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }

        // then
        var emailsForRecipient = mailhogClient.findAllForRecipient(invitee.getUserData().getEmail());
        assertThat(emailsForRecipient).hasSize(1);
        assertThat(emailsForRecipient.get(0).body()).contains("You have been invited to join");
    }

    @Test
    void inviter_shouldReceiveEmail_whenInviteeDeclinesInvitation() {
        // given
        var inviter = keycloakAdminClient.createVerifiedUser();
        var tenantResource = inviter.createTenant();

        var invitee = keycloakAdminClient.createVerifiedUser();
        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(invitee.getUserData().getEmail());
        try (var response = tenantResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }

        // when
        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(invitee.getUserData().getEmail(), invitee.getUserData().getPassword())
                .signIn();
        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);
        nextPage = ((ReviewInvitationsPage) nextPage).uncheckInvitation(tenantResource.toRepresentation().getName()).accept();
        assertThat(nextPage).isInstanceOf(CreateTenantPage.class);

        // then
        var emailsForRecipient = mailhogClient.findAllForRecipient(inviter.getUserData().getEmail());
        assertThat(emailsForRecipient).hasSize(1);
        assertThat(emailsForRecipient.get(0).body()).contains("has been declined");
    }

    @Test
    void inviter_shouldReceiveEmail_whenInviteeAcceptsInvitation() {
        // given
        var inviter = keycloakAdminClient.createVerifiedUser();
        var tenantResource = inviter.createTenant();

        var inviterResource = keycloakAdminClient.getRealmResource().users().get(inviter.getUserId());
        var inviterRepresentation = inviterResource.toRepresentation();
        inviterRepresentation.setAttributes(Map.of("locale", List.of("sv-SE")));
        inviterResource.update(inviterRepresentation);

        var invitee = keycloakAdminClient.createVerifiedUser();
        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(invitee.getUserData().getEmail());
        try (var response = tenantResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }

        // when
        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(invitee.getUserData().getEmail(), invitee.getUserData().getPassword())
                .signIn();
        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);
        nextPage = ((ReviewInvitationsPage) nextPage).accept();
        assertThat(nextPage).isInstanceOf(AccountPage.class);

        // then
        var emailsForRecipient = mailhogClient.findAllForRecipient(inviter.getUserData().getEmail());
        assertThat(emailsForRecipient).hasSize(1);
        assertThat(emailsForRecipient.get(0).body()).contains("har accepterats");
    }
}
