package dev.sultanov.keycloak.multitenancy.support.actor;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import dev.sultanov.keycloak.multitenancy.support.IntegrationTestContextHolder;
import dev.sultanov.keycloak.multitenancy.support.api.TenantResource;
import dev.sultanov.keycloak.multitenancy.support.api.TenantsResource;
import dev.sultanov.keycloak.multitenancy.support.browser.AccountPage;
import dev.sultanov.keycloak.multitenancy.support.browser.CreateTenantPage;
import dev.sultanov.keycloak.multitenancy.support.data.TenantData;
import dev.sultanov.keycloak.multitenancy.support.data.UserData;
import java.net.URI;
import java.util.Objects;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

public class KeycloakUser {

    private static final String REALM_NAME = "multi-tenant";
    private static final String CLIENT_ID = "multi-tenant";

    private final String userId;
    private final UserData userData;
    private TenantData tenantData;

    private KeycloakUser(String userId, UserData userData) {
        this.userId = Objects.requireNonNull(userId);
        this.userData = Objects.requireNonNull(userData);
    }

    static KeycloakUser from(String userId, UserData userData) {
        return new KeycloakUser(userId, userData);
    }

    public String getUserId() {
        return userId;
    }

    public UserData getUserData() {
        return userData;
    }

    public TenantData getTenantData() {
        return tenantData;
    }

    public TenantResource createTenant() {
        if (tenantData != null) {
            throw new IllegalStateException("The user is already a member of a tenant");
        }
        tenantData = TenantData.random();

        ((CreateTenantPage) AccountPage.open()
                .signIn()
                .fillCredentials(userData.getEmail(), userData.getPassword())
                .signIn())
                .fillTenantData(tenantData)
                .submit();
        var tenantsResource = tenantsResource();
        return tenantsResource.listTenants(null, null, null)
                .stream()
                .findFirst()
                .map(TenantRepresentation::getId)
                .map(tenantsResource::getTenantResource)
                .orElseThrow();
    }

    private TenantsResource tenantsResource() {
        return createClient().proxy(TenantsResource.class, URI.create(IntegrationTestContextHolder.getContext().keycloakUrl() + "/realms/" + REALM_NAME));
    }

    private Keycloak createClient() {
        var integrationTestContext = IntegrationTestContextHolder.getContext();
        return KeycloakBuilder.builder()
                .resteasyClient(integrationTestContext.httpClient())
                .serverUrl(integrationTestContext.keycloakUrl())
                .realm(REALM_NAME)
                .grantType(OAuth2Constants.PASSWORD)
                .clientId(CLIENT_ID)
                .username(userData.getEmail())
                .password(userData.getPassword())
                .build();
    }
}
