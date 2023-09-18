package dev.sultanov.keycloak.multitenancy.support.data;

import com.github.javafaker.Faker;
import lombok.Value;

@Value
public class TenantData {

    private static final Faker faker = FakerProvider.getFaker();

    String name;

    public static TenantData random() {
        var name = faker.company().name();
        return new TenantData(name);
    }
}
