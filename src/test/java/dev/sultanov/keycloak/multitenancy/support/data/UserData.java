package dev.sultanov.keycloak.multitenancy.support.data;

import com.github.javafaker.Faker;
import lombok.Value;

@Value
public class UserData {

    private static final Faker faker = FakerProvider.getFaker();

    String firstName;
    String lastName;
    String email;
    String password;

    public static UserData random() {
        var firstName = faker.name().firstName();
        var lastName = faker.name().lastName();
        var email = faker.internet().emailAddress(firstName + "." + lastName.replaceAll("[^A-Za-z]", "")).toLowerCase();
        var password = faker.internet().password();
        return new UserData(firstName, lastName, email, password);
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
