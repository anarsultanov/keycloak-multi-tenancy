package dev.sultanov.keycloak.multitenancy.support.browser;

import com.microsoft.playwright.Page;

public abstract class AbstractPage {

    protected final Page page;

    protected AbstractPage(Page page) {
        this.page = page;
    }
}
