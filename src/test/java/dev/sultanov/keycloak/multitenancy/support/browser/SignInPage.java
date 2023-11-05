package dev.sultanov.keycloak.multitenancy.support.browser;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

public class SignInPage extends AbstractPage {

    SignInPage(Page page) {
        super(page);
    }

    public SignInPage signInWith(String identityProvider) {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(identityProvider)).click();
        assertThat(page.getByText("IDENTITY-PROVIDER").isVisible()).isTrue();
        return this;
    }

    public SelectLoginMethodPage tryAnotherWay() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Try Another Way")).click();
        return new SelectLoginMethodPage(page);
    }

    public SignInPage fillCredentials(String email, String password) {
        page.getByLabel("Email").fill(email);
        page.getByLabel("Password").fill(password);
        return this;
    }

    public AbstractPage signIn() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
        return PageResolver.resolve(page);
    }

    public RegistrationPage register() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Register")).click();
        return new RegistrationPage(page);
    }
}
