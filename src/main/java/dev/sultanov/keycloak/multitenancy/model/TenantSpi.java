package dev.sultanov.keycloak.multitenancy.model;

import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

public class TenantSpi implements Spi {

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return "tenant-provider-spi";
    }

    @Override
    public Class<TenantProvider> getProviderClass() {
        return TenantProvider.class;
    }

    @Override
    public Class<? extends ProviderFactory<TenantProvider>> getProviderFactoryClass() {
        return TenantProviderFactory.class;
    }

}