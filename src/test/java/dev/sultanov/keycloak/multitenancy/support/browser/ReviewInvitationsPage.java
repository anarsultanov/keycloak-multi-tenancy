package dev.sultanov.keycloak.multitenancy.support.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

public class ReviewInvitationsPage extends AbstractPage {

    ReviewInvitationsPage(Page page) {
        super(page);
    }

    public ReviewInvitationsPage uncheckInvitation(String tenantName) {
        page.getByLabel(tenantName).uncheck();
        return this;
    }

    public AbstractPage accept() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Accept")).click();
        return PageResolver.resolve(page);
    }
}
