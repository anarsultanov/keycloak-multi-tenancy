package dev.sultanov.keycloak.multitenancy.support.data;

import com.github.javafaker.Faker;
import lombok.experimental.UtilityClass;

@UtilityClass
class FakerProvider {

    private static final Faker faker = new Faker();

    static Faker getFaker() {
        return faker;
    }
}
