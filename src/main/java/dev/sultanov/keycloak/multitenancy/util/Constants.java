package dev.sultanov.keycloak.multitenancy.util;

public class Constants {

    public static final String TENANT_ADMIN_ROLE = "tenant-admin";
    public static final String TENANT_USER_ROLE = "tenant-user";

    public static final String ACTIVE_TENANT_ID_SESSION_NOTE = "active-tenant-id";
    public static final String IDENTITY_PROVIDER_SESSION_NOTE = "identity_provider";

    private Constants() {
        throw new AssertionError();
    }
}
