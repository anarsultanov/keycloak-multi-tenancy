package dev.sultanov.keycloak.multitenancy.support.data;

import lombok.Value;

@Value
public class UserData {

    String firstName;
    String lastName;
    String email;
    String password;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
