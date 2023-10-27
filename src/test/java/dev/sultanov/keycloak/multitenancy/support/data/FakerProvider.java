package dev.sultanov.keycloak.multitenancy.support.data;

import com.github.javafaker.Faker;
import lombok.experimental.UtilityClass;

@UtilityClass
public class FakerProvider {

    private static final Faker faker = new Faker();

    public static Faker getFaker() {
        return faker;
    }
}
