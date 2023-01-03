package dev.sultanov.keycloak.multitenancy.support.data;


import com.github.javafaker.Faker;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestDataFactory {

    private static final Faker faker = new Faker();

    public static UserData userData() {
        var firstName = faker.name().firstName();
        var lastName = faker.name().lastName();
        var email = faker.internet().emailAddress(firstName + "." + lastName);
        var password = faker.internet().password();
        return new UserData(firstName, lastName, email, password);
    }

    public static TenantData tenantData() {
        var name = faker.company().name();
        return new TenantData(name);
    }
}
