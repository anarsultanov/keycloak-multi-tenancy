package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import dev.sultanov.keycloak.multitenancy.support.BaseIntegrationTest;
import dev.sultanov.keycloak.multitenancy.support.IntegrationTestContextHolder;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakAdminCli;
import dev.sultanov.keycloak.multitenancy.support.browser.AccountPage;
import dev.sultanov.keycloak.multitenancy.support.browser.ReviewInvitationsPage;
import dev.sultanov.keycloak.multitenancy.support.browser.SelectTenantPage;
import dev.sultanov.keycloak.multitenancy.support.data.UserData;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.IDToken;

public class TenantSelectionIntegrationTest extends BaseIntegrationTest {

    private static final String REALM_NAME = "multi-tenant";
    private static final String CLIENT_ID = "multi-tenant";

    private static KeycloakAdminCli keycloakAdminClient;

    @BeforeAll
    static void setUpRealm() {
        keycloakAdminClient = KeycloakAdminCli.forMainRealm();
    }

    @Test
    void userWithMultipleTenants_canSwitchTenantViaApplicationInitiatedAction() {
        // This test verifies the Application-Initiated Action (AIA) flow for tenant switching
        // as described in https://www.keycloak.org/docs/latest/server_admin/index.html#con-aia_server_administration_guide
        
        // given - create user with multiple tenant memberships
        var user = keycloakAdminClient.createVerifiedUser();
        var tenant1 = createInvitationFor(user.getUserData());
        var tenant2 = createInvitationFor(user.getUserData());
        
        // Configure redirect URI for multi-tenant client to enable AIA
        configureMultiTenantClientRedirectUri();
        
        // First, complete the initial login flow
        var accountPage = AccountPage.open();
        var signInPage = accountPage.signIn();
        var nextPage = signInPage
                .fillCredentials(user.getUserData().getEmail(), user.getUserData().getPassword())
                .signIn();
        
        // Accept invitations
        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);
        nextPage = ((ReviewInvitationsPage) nextPage).accept();
        
        // Select first tenant initially
        assertThat(nextPage).isInstanceOf(SelectTenantPage.class);
        var selectPage = (SelectTenantPage) nextPage;
        var finalPage = selectPage.select(tenant1.getName()).signIn();
        assertThat(finalPage).isInstanceOf(AccountPage.class);
        
        // when - Application initiates tenant switch action via AIA
        // According to Keycloak docs, AIA works by sending authenticated user to auth endpoint with kc_action
        // We use the account console as redirect URI
        var redirectUri = IntegrationTestContextHolder.getContext().keycloakUrl() + "/realms/" + REALM_NAME + "/account/";
        var aiaUrl = String.format("%s/realms/%s/protocol/openid-connect/auth?" +
                "client_id=%s&redirect_uri=%s&response_type=code&scope=openid&kc_action=%s",
                IntegrationTestContextHolder.getContext().keycloakUrl(),
                REALM_NAME,
                CLIENT_ID, // Use multi-tenant client which has the active tenant mapper
                redirectUri,
                "select-active-tenant");
        
        // Navigate to the AIA URL using the existing page
        finalPage.getPage().navigate(aiaUrl);

        // then - User should see tenant selection page
        // We're still on the same page object, just navigated to a different URL
        var page = finalPage.getPage();
        page.locator("select[name='tenant']").waitFor(new Locator.WaitForOptions().setTimeout(10000));
        
        // Verify cancel button is visible for AIA (unlike initial login)
        assertThat(page.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Cancel")).isVisible()).isTrue();
        
        // Select the second tenant and sign in
        page.locator("select[name='tenant']").selectOption(tenant2.getName());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
        
        // Wait for redirect to complete
        page.waitForURL("**/realms/multi-tenant/account/**", new Page.WaitForURLOptions().setTimeout(10000));
        var finalRedirectUrl = page.url();
        assertThat(finalRedirectUrl).contains("code=");
        
        // Exchange code for token and verify tenant switch
        var code = extractCodeFromUrl(finalRedirectUrl);
        var tokenResponse = exchangeCodeForToken(code);
        var decodedToken = decodeToken(tokenResponse.getToken());
        
        assertThat(decodedToken.getOtherClaims()).containsKey("active_tenant");
        @SuppressWarnings("unchecked")
        var activeTenantClaim = (Map<String, Object>) decodedToken.getOtherClaims().get("active_tenant");
        assertThat(activeTenantClaim.get("tenant_id")).isEqualTo(tenant2.getId());
        assertThat(activeTenantClaim.get("tenant_name")).isEqualTo(tenant2.getName());
    }

    @Data
    @Builder
    private static class TenantInfo {
        private String id;
        private String name;
    }

    private TenantInfo createInvitationFor(UserData inviteeData) {
        var inviter = keycloakAdminClient.createVerifiedUser();
        var tenantResource = inviter.createTenant();
        var tenantData = inviter.getTenantData();

        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(inviteeData.getEmail());
        try (var response = tenantResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }
        
        // Get the tenant ID from the resource
        var tenant = tenantResource.toRepresentation();
        
        return TenantInfo.builder()
                .id(tenant.getId())
                .name(tenantData.getName())
                .build();
    }

    private AccessTokenResponse exchangeCodeForToken(String code) {
        // Create a new HTTP client for this request
        var context = IntegrationTestContextHolder.getContext();
        
        try (var client = jakarta.ws.rs.client.ClientBuilder.newClient()) {
            // Build the token endpoint URL
            var tokenUrl = context.keycloakUrl() + "/realms/" + REALM_NAME + "/protocol/openid-connect/token";
            
            // Prepare the form data
            var redirectUri = context.keycloakUrl() + "/realms/" + REALM_NAME + "/account/";
            var form = new jakarta.ws.rs.core.Form()
                    .param("grant_type", "authorization_code")
                    .param("code", code)
                    .param("client_id", CLIENT_ID) // Use multi-tenant client
                    .param("redirect_uri", redirectUri);
            
            // Make the request
            try (var response = client.target(tokenUrl)
                    .request()
                    .post(jakarta.ws.rs.client.Entity.form(form))) {
                return response.readEntity(AccessTokenResponse.class);
            }
        }
    }

    private IDToken decodeToken(String token) {
        var parts = token.split("\\.");
        var payload = parts[1];
        var decoded = java.util.Base64.getUrlDecoder().decode(payload);
        var json = new String(decoded, StandardCharsets.UTF_8);
        
        try {
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return objectMapper.readValue(json, org.keycloak.representations.IDToken.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode token", e);
        }
    }

    private String extractCodeFromUrl(String url) {
        var uri = URI.create(url);
        var query = uri.getQuery();
        if (query == null) {
            throw new IllegalArgumentException("No query parameters in URL: " + url);
        }
        
        var params = query.split("&");
        for (var param : params) {
            var parts = param.split("=");
            if (parts.length == 2 && "code".equals(parts[0])) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("No code found in URL: " + url);
    }

    private void configureMultiTenantClientRedirectUri() {
        var realm = keycloakAdminClient.getRealmResource();
        var clients = realm.clients().findByClientId(CLIENT_ID);
        if (clients.isEmpty()) {
            throw new IllegalStateException("Client " + CLIENT_ID + " not found");
        }
        
        var client = realm.clients().get(clients.get(0).getId());
        var clientRepresentation = client.toRepresentation();
        
        // Add redirect URIs for the account page
        var redirectUris = clientRepresentation.getRedirectUris();
        if (redirectUris == null) {
            redirectUris = new ArrayList<>();
        }
        
        // Add the account page as a valid redirect URI
        var accountRedirectUri = IntegrationTestContextHolder.getContext().keycloakUrl() + "/realms/" + REALM_NAME + "/account/*";
        if (!redirectUris.contains(accountRedirectUri)) {
            redirectUris.add(accountRedirectUri);
            clientRepresentation.setRedirectUris(redirectUris);
            client.update(clientRepresentation);
        }
    }
}