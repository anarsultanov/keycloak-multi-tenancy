package dev.sultanov.keycloak.multitenancy.support.browser;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

public class SelectLoginMethodPage extends AbstractPage {

    SelectLoginMethodPage(Page page) {
        super(page);
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Select login method")).isVisible()).isTrue();
    }

    public SingleSignOnPage selectSingleSignOn() {
        page.getByText("Single Sign-on (SSO)", new Page.GetByTextOptions().setExact(true)).click();
        return new SingleSignOnPage(page);
    }
}
