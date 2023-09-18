package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import dev.sultanov.keycloak.multitenancy.support.BaseIntegrationTest;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakAdminCli;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakUser;
import dev.sultanov.keycloak.multitenancy.support.data.UserData;
import dev.sultanov.keycloak.multitenancy.support.mail.MailhogClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;

public class MailIntegrationTest extends BaseIntegrationTest {

    private KeycloakAdminCli keycloakAdminClient;
    private MailhogClient mailhogClient;

    @BeforeEach
    void setUp() {
        keycloakAdminClient = KeycloakAdminCli.create();
        mailhogClient = MailhogClient.create();
    }

    @Test
    void user_shouldReceiveEmail_whenTheyAreInvitedToJoinTenant() {
        // given
        var user = keycloakAdminClient.createVerifiedUser();

        // when
        createInvitationFor(user.getUserData());

        // then
        var emailsForRecipient = mailhogClient.findAllForRecipient(user.getUserData().getEmail());
        assertThat(emailsForRecipient).hasSize(1);
        assertThat(emailsForRecipient.get(0).body()).contains("You have been invited to join");
    }

    private KeycloakUser createInvitationFor(UserData inviteeData) {
        var inviter = keycloakAdminClient.createVerifiedUser();
        var tenantResource = inviter.createTenant();

        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(inviteeData.getEmail());
        try (var response = tenantResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }
        return inviter;
    }
}
