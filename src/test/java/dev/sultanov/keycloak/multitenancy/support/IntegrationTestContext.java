package dev.sultanov.keycloak.multitenancy.support;

import com.microsoft.playwright.Browser;
import jakarta.ws.rs.client.Client;

public record IntegrationTestContext(Client httpClient, Browser browser, String keycloakUrl, String mailhogUrl) {

}
