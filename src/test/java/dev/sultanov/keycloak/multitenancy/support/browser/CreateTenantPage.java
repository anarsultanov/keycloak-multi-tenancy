package dev.sultanov.keycloak.multitenancy.support.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import dev.sultanov.keycloak.multitenancy.support.data.TenantData;

public class CreateTenantPage extends AbstractPage {

    CreateTenantPage(Page page) {
        super(page);
    }

    public CreateTenantPage fillTenantData(TenantData tenantData) {
        page.getByLabel("Tenant name").fill(tenantData.getName());
        return this;
    }

    public AbstractPage submit() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit")).click();
        return PageResolver.resolve(page);
    }
}
