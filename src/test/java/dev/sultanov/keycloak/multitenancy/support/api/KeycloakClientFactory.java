package dev.sultanov.keycloak.multitenancy.support.api;

import dev.sultanov.keycloak.multitenancy.support.data.UserData;
import jakarta.ws.rs.client.Client;
import org.keycloak.admin.client.spi.ResteasyClientClassicProvider;

public class KeycloakClientFactory {

    private final Client resteasyClient;
    private final String keycloakUrl;

    public KeycloakClientFactory(String keycloakUrl) {
        this.keycloakUrl = keycloakUrl;
        this.resteasyClient = new ResteasyClientClassicProvider().newRestEasyClient(null, null, false);
    }

    public KeycloakClient createAdminClient() {
        return KeycloakClient.forAdmin(resteasyClient, keycloakUrl);
    }

    public KeycloakClient createUserClient(UserData userData) {
        return KeycloakClient.forUser(resteasyClient, keycloakUrl, userData);
    }
}
