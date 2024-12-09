package dev.sultanov.keycloak.multitenancy.support.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
import java.util.List;

public class SelectTenantPage extends AbstractPage {

    SelectTenantPage(Page page) {
        super(page);
    }

    public List<String> availableOptions() {
        return page.locator("select[name='tenant']").locator("option").allTextContents();
    }

    public SelectTenantPage select(String tenantName) {
        page.locator("select[name='tenant']").selectOption(new SelectOption().setLabel(tenantName));
        return this;
    }

    public AbstractPage signIn() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
        return PageResolver.resolve(page);
    }
}
