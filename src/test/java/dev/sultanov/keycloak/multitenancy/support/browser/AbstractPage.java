package dev.sultanov.keycloak.multitenancy.support.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractPage {

    protected final Page page;

    protected AbstractPage(Page page) {
        this.page = page;
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractPage> T as(Class<T> clazz) {
        assertThat(this).isInstanceOf(clazz);
        return ((T) this);
    }

    public Page getPage() {
        return page;
    }
}
