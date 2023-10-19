package dev.sultanov.keycloak.multitenancy.util;

public class Constants {

    public static final String TENANT_ADMIN_ROLE = "tenant-admin";
    public static final String ACTIVE_TENANT_ID_SESSION_NOTE = "active-tenant-id";
    public static final String ACTIVE_TENANT_PROVIDER_SESSION_NOTE = "active-tenant-provider";
    public static final String KEYCLOAK_TENANT_PROVIDER_CLAIM = "keycloak";

    private Constants() {
        throw new AssertionError();
    }
}
