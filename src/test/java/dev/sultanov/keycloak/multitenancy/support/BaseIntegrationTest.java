package dev.sultanov.keycloak.multitenancy.support;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class BaseIntegrationTest {

    private static final Integer MAILHOG_HTTP_PORT = 8025;

    private static final Network network = Network.newNetwork();
    private static final KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.2.5")
            .withRealmImportFiles("/realm-export.json", "/idp-realm-export.json")
            .withProviderClassesFrom("target/classes")
            .withNetwork(network)
            .withNetworkAliases("keycloak")
            .withEnv("KC_LOGLEVEL", "DEBUG")
            .withAccessToHost(true);

    private static final GenericContainer<?> mailhog = new GenericContainer<>("mailhog/mailhog")
            .withExposedPorts(MAILHOG_HTTP_PORT)
            .waitingFor(Wait.forHttp("/"))
            .withNetwork(network)
            .withNetworkAliases("mailhog")
            .withAccessToHost(true);

    private static Client client;
    private static Playwright playwright;

    @BeforeAll
    static void beforeAll() {
        keycloak.start();
        mailhog.start();

        client = ClientBuilder.newClient();
        playwright = Playwright.create();
        var browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

        var keycloakUrl = keycloak.getAuthServerUrl();
        var mailhogUrl = "http://%s:%d/".formatted(mailhog.getHost(), mailhog.getMappedPort(MAILHOG_HTTP_PORT));

        IntegrationTestContextHolder.setContext(new IntegrationTestContext(client, browser, keycloakUrl, mailhogUrl));
    }

    @AfterAll
    static void afterAll() {
        client.close();
        playwright.close();
        IntegrationTestContextHolder.clearContext();
    }
}
