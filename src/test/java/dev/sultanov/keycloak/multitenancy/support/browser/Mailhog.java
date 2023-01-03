package dev.sultanov.keycloak.multitenancy.support.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

public class Mailhog extends AbstractPage {

    Mailhog(Page page) {
        super(page);
    }

    public static Mailhog openIn(Browser browser) {
        var browserContext = browser.newContext();
        var page = browserContext.newPage();
        page.navigate("http://localhost:8025/");
        return new Mailhog(page);
    }

    public void verifyEmail(String email) {
        page.getByText("no-reply@localhost " + email + " Verify email").click();
        page.waitForPopup(() -> {
            page.frameLocator("#preview-html").getByRole(
                    AriaRole.LINK,
                    new FrameLocator.GetByRoleOptions().setName("Link to e-mail address verification")
            ).click();
        });
    }
}
