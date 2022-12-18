package dev.sultanov.keycloak.multitenancy.models;

import java.util.Optional;
import java.util.stream.Stream;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;

public interface TenantProviderFactory extends ProviderFactory<TenantProvider> {

}
