package dev.sultanov.keycloak.multitenancy.support.browser;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

public class SingleSignOnPage extends AbstractPage {

    SingleSignOnPage(Page page) {
        super(page);
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Initiate Single Sign-on (SSO)")).isVisible()).isTrue();
    }

    public SingleSignOnPage fillAlias(String alias) {
        page.getByLabel("SSO name").fill(alias);
        return this;
    }

    public AbstractPage proceed() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Continue")).click();
        if (hasError()) {
            return this;
        } else {
            assertThat(page.getByText("IDENTITY-PROVIDER").isVisible()).isTrue();
            return new SignInPage(page);
        }
    }

    public boolean hasError() {
        return page.getByText("Could not find an identity provider with this SSO name.").isVisible();
    }
}
