package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import dev.sultanov.keycloak.multitenancy.support.BaseIntegrationTest;
import dev.sultanov.keycloak.multitenancy.support.IntegrationTestContextHolder;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakAdminCli;
import dev.sultanov.keycloak.multitenancy.support.browser.AccountPage;
import dev.sultanov.keycloak.multitenancy.support.browser.CreateTenantPage;
import dev.sultanov.keycloak.multitenancy.support.browser.ErrorPage;
import dev.sultanov.keycloak.multitenancy.support.browser.ReviewInvitationsPage;
import dev.sultanov.keycloak.multitenancy.support.browser.SelectTenantPage;
import dev.sultanov.keycloak.multitenancy.support.data.FakerProvider;
import dev.sultanov.keycloak.multitenancy.support.data.TenantData;
import dev.sultanov.keycloak.multitenancy.support.data.UserData;
import jakarta.annotation.Nullable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.representations.idm.IdentityProviderRepresentation;

public class IdentityProviderIntegrationTest extends BaseIntegrationTest {

    private static final String FIRST_LOGIN_FLOW_WITH_MEMBERSHIP_CREATION = "first broker login with tenant membership creation";
    private static final String FIRST_LOGIN_FLOW_WITHOUT_MEMBERSHIP_CREATION = "first broker login without tenant membership creation";
    private static final String POST_LOGIN_MEMBERSHIP_CREATION = "create tenant memberships only";

    private KeycloakAdminCli mainRealmClient;
    private KeycloakAdminCli idpRealmClient;

    private String idpAlias;

    @BeforeEach
    void setUp() {
        mainRealmClient = KeycloakAdminCli.forMainRealm();
        idpRealmClient = KeycloakAdminCli.forIdpRealm();
    }

    @AfterEach
    void tearDown() {
        deleteIdentityProvider(idpAlias);
    }

    @Test
    void shouldRequireToCreateTenant_whenSignInUsingPublicIdpAndNotMemberOfAnyTenants() {
        // given
        var idpUser = idpRealmClient.createVerifiedUser();
        idpAlias = createFirstLoginMembershipCreationIdp(Set.of());

        // when
        var nextPage = AccountPage.open()
                .signIn()
                .signInWith(idpAlias)
                .fillCredentials(idpUser.getUserData().getEmail(), idpUser.getUserData().getPassword())
                .signIn();
        assertThat(nextPage).isInstanceOf(CreateTenantPage.class);

        // then
        nextPage = ((CreateTenantPage) nextPage).fillTenantData(TenantData.random()).submit();
        assertThat(nextPage).isInstanceOf(AccountPage.class);
        assertThat(((AccountPage) nextPage).getLoggedInUser()).contains(idpUser.getUserData().getFullName());
    }

    @Test
    void shouldAutomaticallySignIn_whenSignInUsingPublicIdpAndMemberOfOneTenant() {
        // given
        var multiTenantUser = createMultiTenantUser();
        var idpUser = idpRealmClient.createVerifiedUser(multiTenantUser.getKey());
        idpAlias = createFirstLoginMembershipCreationIdp(Set.of());

        // when
        var nextPage = AccountPage.open()
                .signIn()
                .signInWith(idpAlias)
                .fillCredentials(idpUser.getUserData().getEmail(), idpUser.getUserData().getPassword())
                .signIn();

        // then
        assertThat(nextPage).isInstanceOf(SelectTenantPage.class);
        assertThat(((SelectTenantPage) nextPage).availableOptions()).containsExactlyInAnyOrderElementsOf(
                multiTenantUser.getValue().stream().map(TenantRepresentation::getName).toList()
        );
    }

    @Test
    void shouldShowTenantSelection_whenSignInUsingPublicIdpAndMemberOfMultipleTenants() {
        // given
        var multiTenantUser = createMultiTenantUser();
        var idpUser = idpRealmClient.createVerifiedUser(multiTenantUser.getKey());
        idpAlias = createFirstLoginMembershipCreationIdp(Set.of());

        // when
        var nextPage = AccountPage.open()
                .signIn()
                .signInWith(idpAlias)
                .fillCredentials(idpUser.getUserData().getEmail(), idpUser.getUserData().getPassword())
                .signIn();

        // then
        assertThat(nextPage).isInstanceOf(SelectTenantPage.class);
        assertThat(((SelectTenantPage) nextPage).availableOptions()).containsExactlyInAnyOrderElementsOf(
                multiTenantUser.getValue().stream().map(TenantRepresentation::getName).toList()
        );
    }

    @Test
    void shouldCreateMissingMembership_whenSignInUsingTenantIdpWithFirstLoginTenantMembershipCreation() {
        // given
        var multiTenantUser = createMultiTenantUser();
        var idpUser = idpRealmClient.createVerifiedUser(multiTenantUser.getKey());

        var idpTenant1 = multiTenantUser.getValue().get(0);
        var idpTenant2 = mainRealmClient.createVerifiedUser().createTenant().toRepresentation();
        idpAlias = createFirstLoginMembershipCreationIdp(Set.of(idpTenant1.getId(), idpTenant2.getId()));

        // when
        var nextPage = AccountPage.open()
                .signIn()
                .signInWith(idpAlias)
                .fillCredentials(idpUser.getUserData().getEmail(), idpUser.getUserData().getPassword())
                .signIn();

        // then
        assertThat(nextPage).isInstanceOf(SelectTenantPage.class);
        assertThat(((SelectTenantPage) nextPage).availableOptions()).containsExactlyInAnyOrderElementsOf(
                Stream.of(idpTenant1, idpTenant2).map(TenantRepresentation::getName).toList()
        );
    }

    @Test
    void shouldCreateMissingMembership_whenSignInUsingTenantIdpWithPostLoginTenantMembershipCreation() {
        // given
        var multiTenantUser = createMultiTenantUser();
        var idpUser = idpRealmClient.createVerifiedUser(multiTenantUser.getKey());

        var idpTenant1 = multiTenantUser.getValue().get(0);
        var idpTenant2 = mainRealmClient.createVerifiedUser().createTenant().toRepresentation();
        idpAlias = createPostLoginMembershipCreationIdp(Set.of(idpTenant1.getId(), idpTenant2.getId()));

        // when
        var nextPage = AccountPage.open()
                .signIn()
                .signInWith(idpAlias)
                .fillCredentials(idpUser.getUserData().getEmail(), idpUser.getUserData().getPassword())
                .signIn();

        // then
        assertThat(nextPage).isInstanceOf(SelectTenantPage.class);
        assertThat(((SelectTenantPage) nextPage).availableOptions()).containsExactlyInAnyOrderElementsOf(
                Stream.of(idpTenant1, idpTenant2).map(TenantRepresentation::getName).toList()
        );
    }

    @Test
    void shouldFail_whenSignInUsingTenantIdpWithoutTenantMembershipCreationAndWithoutAccessToAnyOfIdpTenants() {
        // given
        var multiTenantUser = createMultiTenantUser();
        var idpUser = idpRealmClient.createVerifiedUser(multiTenantUser.getKey());

        var idpTenant1 = mainRealmClient.createVerifiedUser().createTenant().toRepresentation();
        var idpTenant2 = mainRealmClient.createVerifiedUser().createTenant().toRepresentation();
        idpAlias = createIdpWithoutMembershipCreation(Set.of(idpTenant1.getId(), idpTenant2.getId()));

        // when
        var nextPage = AccountPage.open()
                .signIn()
                .signInWith(idpAlias)
                .fillCredentials(idpUser.getUserData().getEmail(), idpUser.getUserData().getPassword())
                .signIn();

        // then
        assertThat(nextPage).isInstanceOf(ErrorPage.class);
    }

    @Test
    void shouldSeeTenantsCreatedByIdp_whenSignInUsingCredentials() {
        // given
        var multiTenantUser = createMultiTenantUser();
        var idpUser = idpRealmClient.createVerifiedUser(multiTenantUser.getKey());

        var idpTenant1 = mainRealmClient.createVerifiedUser().createTenant().toRepresentation();
        var idpTenant2 = mainRealmClient.createVerifiedUser().createTenant().toRepresentation();
        idpAlias = createFirstLoginMembershipCreationIdp(Set.of(idpTenant1.getId(), idpTenant2.getId()));

        AccountPage.open()
                .signIn()
                .signInWith(idpAlias)
                .fillCredentials(idpUser.getUserData().getEmail(), idpUser.getUserData().getPassword())
                .signIn();

        // when
        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(multiTenantUser.getKey().getEmail(), multiTenantUser.getKey().getPassword())
                .signIn();

        // then
        assertThat(nextPage).isInstanceOf(SelectTenantPage.class);
        assertThat(((SelectTenantPage) nextPage).availableOptions()).containsExactlyInAnyOrderElementsOf(
                Stream.concat(multiTenantUser.getValue().stream(), Stream.of(idpTenant1, idpTenant2)).map(TenantRepresentation::getName).toList()
        );
    }

    @Test
    void shouldSeeOnlyIdpTenants_whenSignInUsingTenantIdp() {
        // given
        var multiTenantUser = createMultiTenantUser();
        var idpUser = idpRealmClient.createVerifiedUser(multiTenantUser.getKey());

        var idpTenant1 = multiTenantUser.getValue().get(1);
        var idpTenant2 = multiTenantUser.getValue().get(2);
        idpAlias = createFirstLoginMembershipCreationIdp(Set.of(idpTenant1.getId(), idpTenant2.getId()));

        // when
        var nextPage = AccountPage.open()
                .signIn()
                .signInWith(idpAlias)
                .fillCredentials(idpUser.getUserData().getEmail(), idpUser.getUserData().getPassword())
                .signIn();

        // then
        assertThat(nextPage).isInstanceOf(SelectTenantPage.class);
        assertThat(((SelectTenantPage) nextPage).availableOptions()).containsExactlyInAnyOrder(idpTenant1.getName(), idpTenant2.getName());
    }

    @Test
    void shouldAutomaticallySignIn_whenSignInUsingTenantIdpConfiguredWithOneTenant() {
        // given
        var multiTenantUser = createMultiTenantUser();
        var idpUser = idpRealmClient.createVerifiedUser(multiTenantUser.getKey());

        var idpTenant = multiTenantUser.getValue().get(0);
        idpAlias = createFirstLoginMembershipCreationIdp(Set.of(idpTenant.getId()));

        // when
        var nextPage = AccountPage.open()
                .signIn()
                .signInWith(idpAlias)
                .fillCredentials(idpUser.getUserData().getEmail(), idpUser.getUserData().getPassword())
                .signIn();

        // then
        assertThat(nextPage).isInstanceOf(AccountPage.class);
    }

    private Map.Entry<UserData, List<TenantRepresentation>> createMultiTenantUser() {
        var user = mainRealmClient.createVerifiedUser();
        var tenants = new ArrayList<TenantRepresentation>();

        for (int i = 0; i < 3; i++) {
            var inviter = mainRealmClient.createVerifiedUser();
            var tenantResource = inviter.createTenant();

            var invitation = new TenantInvitationRepresentation();
            invitation.setEmail(user.getUserData().getEmail());
            try (var response = tenantResource.invitations().createInvitation(invitation)) {
                assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
            }
            tenants.add(tenantResource.toRepresentation());
        }

        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(user.getUserData().getEmail(), user.getUserData().getPassword())
                .signIn();
        ((ReviewInvitationsPage) nextPage).accept();
        return new AbstractMap.SimpleImmutableEntry<>(user.getUserData(), tenants);
    }

    private String createFirstLoginMembershipCreationIdp(Set<String> tenantIds) {
        return createIdentityProvider(FIRST_LOGIN_FLOW_WITH_MEMBERSHIP_CREATION, null, tenantIds);
    }

    private String createPostLoginMembershipCreationIdp(Set<String> tenantIds) {
        return createIdentityProvider(FIRST_LOGIN_FLOW_WITHOUT_MEMBERSHIP_CREATION, POST_LOGIN_MEMBERSHIP_CREATION, tenantIds);
    }

    private String createIdpWithoutMembershipCreation(Set<String> tenantIds) {
        return createIdentityProvider(FIRST_LOGIN_FLOW_WITHOUT_MEMBERSHIP_CREATION, null, tenantIds);
    }

    private String createIdentityProvider(String firstLoginFlow, @Nullable String postLoginFlow, Set<String> tenantIds) {
        var idpAlias = FakerProvider.getFaker().internet().domainWord();

        var provider = new IdentityProviderRepresentation();
        provider.setProviderId("oidc");
        provider.setAlias(idpAlias);
        provider.setTrustEmail(true);
        provider.setFirstBrokerLoginFlowAlias(firstLoginFlow);
        if (postLoginFlow != null) {
            provider.setPostBrokerLoginFlowAlias(postLoginFlow);
        }

        Map<String, String> config = new HashMap<>();
        config.put("clientId", "test-client");
        config.put("clientSecret", "jXqZRhLLkcuCE0EeRnSimblUiqmC8rMR");
        config.put("authorizationUrl", IntegrationTestContextHolder.getContext().keycloakUrl() + "/realms/identity-provider/protocol/openid-connect/auth");
        config.put("tokenUrl", "http://0.0.0.0:8080/realms/identity-provider/protocol/openid-connect/token");
        config.put("jwksUrl", "http://0.0.0.0:8080/realms/identity-provider/protocol/openid-connect/certs");
        config.put("useJwksUrl", "true");
        config.put("multi-tenancy.tenants", String.join(",", tenantIds));
        provider.setConfig(config);

        try (var response = mainRealmClient.getRealmResource().identityProviders().create(provider)) {
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    private void deleteIdentityProvider(String alias) {
        mainRealmClient.getRealmResource().identityProviders().get(alias).remove();
    }
}
