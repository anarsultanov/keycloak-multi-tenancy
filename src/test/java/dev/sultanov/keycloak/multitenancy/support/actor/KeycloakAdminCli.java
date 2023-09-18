package dev.sultanov.keycloak.multitenancy.support.actor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sultanov.keycloak.multitenancy.support.IntegrationTestContextHolder;
import dev.sultanov.keycloak.multitenancy.support.data.UserData;
import java.util.List;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

public class KeycloakAdminCli {

    private static final String REALM_NAME = "multi-tenant";
    private static final String ADMIN_CLIENT_ID = "admin-cli";
    private static final String ADMIN_CLIENT_SECRET = "74c2b6f6-6109-4c29-b364-7b0943b5e724";

    private final Keycloak keycloak;

    private KeycloakAdminCli(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    public static KeycloakAdminCli create() {
        var integrationTestContext = IntegrationTestContextHolder.getContext();
        var keycloak = KeycloakBuilder.builder()
                .resteasyClient(integrationTestContext.httpClient())
                .serverUrl(integrationTestContext.keycloakUrl())
                .realm(REALM_NAME)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(ADMIN_CLIENT_ID)
                .clientSecret(ADMIN_CLIENT_SECRET)
                .build();
        return new KeycloakAdminCli(keycloak);
    }

    public KeycloakUser createVerifiedUser() {
        var userData = UserData.random();
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
        try (var response = keycloak.realm(REALM_NAME).users().create(userRepresentation)) {
            var createdId = CreatedResponseUtil.getCreatedId(response);
            assertThat(createdId).isNotNull();
            return KeycloakUser.from(userData);
        }
    }
}
