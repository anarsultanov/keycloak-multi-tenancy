package dev.sultanov.keycloak.multitenancy.support.api;

import dev.sultanov.keycloak.multitenancy.support.data.UserData;
import jakarta.ws.rs.client.Client;
import java.net.URI;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;

public class KeycloakClient {

    private static final String REALM_NAME = "multi-tenant";
    private static final String CLIENT_ID = "multi-tenant";
    private static final String ADMIN_CLIENT_ID = "admin-cli";
    private static final String ADMIN_CLIENT_SECRET = "74c2b6f6-6109-4c29-b364-7b0943b5e724";

    private final Keycloak keycloak;
    private final String keycloakUrl;

    private KeycloakClient(Keycloak keycloak, String keycloakUrl) {
        this.keycloak = keycloak;
        this.keycloakUrl = keycloakUrl;
    }

    static KeycloakClient forAdmin(Client client, String keycloakUrl) {
        var keycloak = KeycloakBuilder.builder()
                .resteasyClient(client)
                .serverUrl(keycloakUrl)
                .realm(REALM_NAME)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(ADMIN_CLIENT_ID)
                .clientSecret(ADMIN_CLIENT_SECRET)
                .build();
        return new KeycloakClient(keycloak, keycloakUrl);
    }

    static KeycloakClient forUser(Client client, String keycloakUrl, UserData userData) {
        var keycloak = KeycloakBuilder.builder()
                .resteasyClient(client)
                .serverUrl(keycloakUrl)
                .realm(REALM_NAME)
                .grantType(OAuth2Constants.PASSWORD)
                .clientId(CLIENT_ID)
                .username(userData.getEmail())
                .password(userData.getPassword())
                .build();
        return new KeycloakClient(keycloak, keycloakUrl);
    }

    public RealmResource realmResource() {
        return keycloak.realm(REALM_NAME);
    }

    public TenantsResource tenantsResource() {
        return keycloak.proxy(TenantsResource.class, URI.create(keycloakUrl + "/realms/" + REALM_NAME));
    }
}
