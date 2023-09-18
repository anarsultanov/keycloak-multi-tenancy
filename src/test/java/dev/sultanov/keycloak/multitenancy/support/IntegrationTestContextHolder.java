package dev.sultanov.keycloak.multitenancy.support;

import java.util.Objects;

public class IntegrationTestContextHolder {

    private static final ThreadLocal<IntegrationTestContext> contextThreadLocal = new ThreadLocal<>();

    public static void setContext(IntegrationTestContext context) {
        Objects.requireNonNull(context);
        contextThreadLocal.set(context);
    }

    public static IntegrationTestContext getContext() {
        return contextThreadLocal.get();
    }


    public static void clearContext() {
        contextThreadLocal.remove();
    }
}
