package dev.sultanov.keycloak.multitenancy.util;

public class Constants {

    public static final String TENANT_ADMIN_ROLE = "tenant-admin";
    public static final String ACTIVE_TENANT_ID_SESSION_NOTE = "active-tenant-id";

    private Constants() {
        throw new AssertionError();
    }
}
