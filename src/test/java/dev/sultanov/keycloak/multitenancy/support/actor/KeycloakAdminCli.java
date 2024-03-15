package dev.sultanov.keycloak.multitenancy.support.actor;

import dev.sultanov.keycloak.multitenancy.support.IntegrationTestContextHolder;
import dev.sultanov.keycloak.multitenancy.support.data.UserData;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

public class KeycloakAdminCli {

    private static final String MAIN_REALM_NAME = "multi-tenant";
    private static final String IDP_REALM_NAME = "identity-provider";
    private static final String ADMIN_CLIENT_ID = "admin-cli";
    private static final String ADMIN_CLIENT_SECRET = "74c2b6f6-6109-4c29-b364-7b0943b5e724";

    private final Keycloak keycloak;
    private final String realm;

    private KeycloakAdminCli(Keycloak keycloak, String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    public static KeycloakAdminCli forMainRealm() {
        var integrationTestContext = IntegrationTestContextHolder.getContext();
        var keycloak = KeycloakBuilder.builder()
                .resteasyClient(integrationTestContext.httpClient())
                .serverUrl(integrationTestContext.keycloakUrl())
                .realm(MAIN_REALM_NAME)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(ADMIN_CLIENT_ID)
                .clientSecret(ADMIN_CLIENT_SECRET)
                .build();
        return new KeycloakAdminCli(keycloak, MAIN_REALM_NAME);
    }

    public static KeycloakAdminCli forIdpRealm() {
        var integrationTestContext = IntegrationTestContextHolder.getContext();
        var keycloak = KeycloakBuilder.builder()
                .resteasyClient(integrationTestContext.httpClient())
                .serverUrl(integrationTestContext.keycloakUrl())
                .realm(IDP_REALM_NAME)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(ADMIN_CLIENT_ID)
                .clientSecret(ADMIN_CLIENT_SECRET)
                .build();
        return new KeycloakAdminCli(keycloak, IDP_REALM_NAME);
    }

    public KeycloakUser createVerifiedUser() {
        return createVerifiedUser(Map.of());
    }

    public KeycloakUser createVerifiedUser(UserData userData) {
        return createVerifiedUser(userData, Map.of());
    }

    public KeycloakUser createVerifiedUser(Map<String, List<String>> attributes) {
        var userData = UserData.random();
        return createVerifiedUser(userData, attributes);
    }

    public KeycloakUser createVerifiedUser(UserData userData, Map<String, List<String>> attributes) {
        var userRepresentation = new UserRepresentation();
        userRepresentation.setFirstName(userData.getFirstName());
        userRepresentation.setLastName(userData.getLastName());
        userRepresentation.setUsername(userData.getEmail());
        userRepresentation.setEmail(userData.getEmail());
        userRepresentation.setEmailVerified(true);
        userRepresentation.setEnabled(true);
        userRepresentation.setAttributes(attributes);
        var credentialRepresentation = new CredentialRepresentation();
        credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
        credentialRepresentation.setValue(userData.getPassword());
        credentialRepresentation.setTemporary(false);
        userRepresentation.setCredentials(List.of(credentialRepresentation));
        try (var response = keycloak.realm(realm).users().create(userRepresentation)) {
            var createdId = CreatedResponseUtil.getCreatedId(response);
            return KeycloakUser.from(createdId, userData);
        }
    }

    public void assignClientRoleToUser(String clientId, String role, String userId) {
        var userResource = keycloak.realm(realm).users().get(userId);

        var clientRepresentation = keycloak.realm(realm).clients().findAll()
                .stream()
                .filter(client -> client.getClientId().equals(clientId))
                .findFirst()
                .orElseThrow();
        var clientResource = keycloak.realm(realm).clients().get(clientRepresentation.getId());

        var roleRepresentation = clientResource.roles().list()
                .stream()
                .filter(element -> element.getName().equals(role))
                .findFirst()
                .orElseGet(() -> createRole(clientResource, role));

        userResource.roles().clientLevel(clientRepresentation.getId()).add(Collections.singletonList(roleRepresentation));
    }

    private RoleRepresentation createRole(ClientResource clientResource, String roleName) {
        var roleRepresentation = new RoleRepresentation();
        roleRepresentation.setName(roleName);
        clientResource.roles().create(roleRepresentation);
        return clientResource.roles().get(roleName).toRepresentation();
    }

    public RealmResource getRealmResource() {
        return keycloak.realm(realm);
    }
}
