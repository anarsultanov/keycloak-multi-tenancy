package dev.sultanov.keycloak.multitenancy.support.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import dev.sultanov.keycloak.multitenancy.support.IntegrationTestContextHolder;
import java.util.Optional;

public class AccountPage extends AbstractPage {

    AccountPage(Page page) {
        super(page);
    }

    @SuppressWarnings("resource")
    public static AccountPage open() {
        var integrationTestContext = IntegrationTestContextHolder.getContext();
        var browserContext = integrationTestContext.browser().newContext();
        var page = browserContext.newPage();
        page.navigate(integrationTestContext.keycloakUrl() + "/realms/multi-tenant/account/#/");
        return new AccountPage(page);
    }

    public SignInPage signIn() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
        return new SignInPage(page);
    }

    public Optional<String> getLoggedInUser() {
        var locator = page.getByTestId("username");
        return Optional.ofNullable(locator.inputValue());
    }

}
