package dev.sultanov.keycloak.multitenancy.support.mail;

public record EmailContent(String sender, String recipient, String subject, String body) {

}