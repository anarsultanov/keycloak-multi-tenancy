package dev.sultanov.keycloak.multitenancy.support.browser;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;

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
}
