package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.Playwright;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import dev.sultanov.keycloak.multitenancy.support.api.KeycloakClient;
import dev.sultanov.keycloak.multitenancy.support.api.KeycloakClientFactory;
import dev.sultanov.keycloak.multitenancy.support.browser.AccountPage;
import dev.sultanov.keycloak.multitenancy.support.browser.CreateTenantPage;
import dev.sultanov.keycloak.multitenancy.support.browser.ReviewInvitationsPage;
import dev.sultanov.keycloak.multitenancy.support.browser.SelectTenantPage;
import dev.sultanov.keycloak.multitenancy.support.data.TenantData;
import dev.sultanov.keycloak.multitenancy.support.data.TestDataFactory;
import dev.sultanov.keycloak.multitenancy.support.data.UserData;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class BrowserFlowTest {

    @Container
    private static final KeycloakContainer keycloak = new KeycloakContainer()
            .withRealmImportFile("/realm-export.json")
            .withProviderClassesFrom("target/classes");

    private static String keycloakUrl;
    private static KeycloakClientFactory clientFactory;
    private static KeycloakClient adminClient;
    private static Playwright playwright;
    private static Browser browser;

    @BeforeAll
    static void launchBrowser() {
        keycloakUrl = keycloak.getAuthServerUrl();
        clientFactory = new KeycloakClientFactory(keycloakUrl);
        adminClient = clientFactory.createAdminClient();
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void closeBrowser() {
        playwright.close();
    }

    @Test
    void user_shouldBePromptedToCreateTenant_whenTheyDontHaveInvitations() {
        var user = createVerifiedUser(TestDataFactory.userData());
        var nextPage = AccountPage.open(browser, keycloakUrl)
                .signIn()
                .fillCredentials(user.getEmail(), user.getPassword())
                .signIn();
        assertThat(nextPage).isInstanceOf(CreateTenantPage.class);

        var tenant = TestDataFactory.tenantData();
        nextPage = ((CreateTenantPage) nextPage).fillTenantData(tenant).submit();

        assertThat(nextPage).isInstanceOf(AccountPage.class);
        assertThat(((AccountPage) nextPage).getLoggedInUser()).contains(user.getFullName());
    }

    @Test
    void user_shouldBePromptedToCreateTenant_whenTheyDeclineInvitation() {
        var user = createVerifiedUser(TestDataFactory.userData());
        var invitationTenant = createInvitationFor(user);

        var nextPage = AccountPage.open(browser, keycloakUrl)
                .signIn()
                .fillCredentials(user.getEmail(), user.getPassword())
                .signIn();
        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);
        nextPage = ((ReviewInvitationsPage) nextPage).uncheckInvitation(invitationTenant.getName()).accept();

        assertThat(nextPage).isInstanceOf(CreateTenantPage.class);

        var tenant = TestDataFactory.tenantData();
        nextPage = ((CreateTenantPage) nextPage).fillTenantData(tenant).submit();

        assertThat(nextPage).isInstanceOf(AccountPage.class);
        assertThat(((AccountPage) nextPage).getLoggedInUser()).contains(user.getFullName());
    }

    @Test
    void user_shouldNotBePromptedToCreateTenant_whenTheyAcceptInvitation() {

        var user = createVerifiedUser(TestDataFactory.userData());

        createInvitationFor(user);

        var nextPage = AccountPage.open(browser, keycloakUrl)
                .signIn()
                .fillCredentials(user.getEmail(), user.getPassword())
                .signIn();
        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);
        nextPage = ((ReviewInvitationsPage) nextPage).accept();

        assertThat(nextPage).isInstanceOf(AccountPage.class);
        assertThat(((AccountPage) nextPage).getLoggedInUser()).contains(user.getFullName());
    }

    @Test
    void user_shouldBePromptedToSelectTenant_whenTheyAcceptMultipleInvitations() {
        var user = createVerifiedUser(TestDataFactory.userData());
        var invitationTenant1 = createInvitationFor(user);
        var invitationTenant2 = createInvitationFor(user);

        var nextPage = AccountPage.open(browser, keycloakUrl)
                .signIn()
                .fillCredentials(user.getEmail(), user.getPassword())
                .signIn();
        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);
        nextPage = ((ReviewInvitationsPage) nextPage).accept();

        assertThat(nextPage).isInstanceOf(SelectTenantPage.class);
        assertThat(((SelectTenantPage) nextPage).availableOptions()).containsExactlyInAnyOrder(invitationTenant1.getName(), invitationTenant2.getName());
        nextPage = ((SelectTenantPage) nextPage).select(invitationTenant2.getName()).signIn();

        assertThat(nextPage).isInstanceOf(AccountPage.class);
        assertThat(((AccountPage) nextPage).getLoggedInUser()).contains(user.getFullName());
    }

    private UserData createVerifiedUser(UserData userData) {
        var userRepresentation = new UserRepresentation();
        userRepresentation.setFirstName(userData.getFirstName());
        userRepresentation.setLastName(userData.getLastName());
        userRepresentation.setEmail(userData.getEmail());
        userRepresentation.setEmailVerified(true);
        userRepresentation.setEnabled(true);
        var credentialRepresentation = new CredentialRepresentation();
        credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
        credentialRepresentation.setValue(userData.getPassword());
        credentialRepresentation.setTemporary(false);
        userRepresentation.setCredentials(List.of(credentialRepresentation));
        try (var response = adminClient.realmResource().users().create(userRepresentation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }
        return userData;
    }

    private TenantData createInvitationFor(UserData user) {
        var inviter = createVerifiedUser(TestDataFactory.userData());
        var tenant = TestDataFactory.tenantData();
        ((CreateTenantPage) AccountPage.open(browser, keycloakUrl)
                .signIn()
                .fillCredentials(inviter.getEmail(), inviter.getPassword())
                .signIn())
                .fillTenantData(tenant)
                .submit();
        var inviterTenantsResource = clientFactory.createUserClient(inviter).tenantsResource();
        var tenantResource = inviterTenantsResource.listTenants(null, null, null)
                .stream()
                .findFirst()
                .map(TenantRepresentation::getId)
                .map(inviterTenantsResource::getTenantResource)
                .orElseThrow();

        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(user.getEmail());
        try (var response = tenantResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }
        return tenant;
    }
}
