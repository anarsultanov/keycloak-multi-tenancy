package dev.sultanov.keycloak.multitenancy.support.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import dev.sultanov.keycloak.multitenancy.support.data.UserData;

public class RegistrationPage extends AbstractPage {

    RegistrationPage(Page page) {
        super(page);
    }

    public RegistrationPage fillUserData(UserData userData) {
        page.getByLabel("First name").fill(userData.getFirstName());
        page.getByLabel("Last name").fill(userData.getLastName());
        page.getByLabel("Email").fill(userData.getEmail());
        page.locator("#password").fill(userData.getPassword());
        page.getByLabel("Confirm password").fill(userData.getPassword());
        return this;
    }

    public AbstractPage register() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Register")).click();
        return PageResolver.resolve(page);
    }
}
