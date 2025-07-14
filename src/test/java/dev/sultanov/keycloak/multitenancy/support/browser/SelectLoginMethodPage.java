package dev.sultanov.keycloak.multitenancy.support.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;

public class SelectLoginMethodPage extends AbstractPage {

    SelectLoginMethodPage(Page page) {
        super(page);
        Locator heading = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Select login method"));
        PlaywrightAssertions.assertThat(heading).isVisible();
    }

    public SingleSignOnPage selectSingleSignOn() {
        page.getByText("Single Sign-on (SSO)", new Page.GetByTextOptions().setExact(true)).click();
        return new SingleSignOnPage(page);
    }
}
