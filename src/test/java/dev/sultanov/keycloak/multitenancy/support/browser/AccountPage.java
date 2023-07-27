package dev.sultanov.keycloak.multitenancy.support.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.Optional;

public class AccountPage extends AbstractPage {

    AccountPage(Page page) {
        super(page);
    }

    public static AccountPage open(Browser browser, String keycloakUrl) {
        var browserContext = browser.newContext();
        var page = browserContext.newPage();
        page.navigate(keycloakUrl + "/realms/multi-tenant/account/#/");
        return new AccountPage(page);
    }

    public SignInPage signIn() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
        return new SignInPage(page);
    }

    public Optional<String> getLoggedInUser() {
        var locator = page.locator("#landingLoggedInUser");
        return Optional.ofNullable(locator.textContent());
    }

}
