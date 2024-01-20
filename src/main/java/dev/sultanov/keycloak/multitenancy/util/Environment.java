package dev.sultanov.keycloak.multitenancy.util;

import java.util.Optional;

public class Environment {

    private Environment() {
        throw new AssertionError("Not for instantiation");
    }

    public static Optional<String> getVariable(String name) {
        return Optional.ofNullable(System.getenv(name));
    }
}
