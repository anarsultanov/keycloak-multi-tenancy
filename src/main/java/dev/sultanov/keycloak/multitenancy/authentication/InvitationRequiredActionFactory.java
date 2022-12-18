package dev.sultanov.keycloak.multitenancy.authentication;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class InvitationRequiredActionFactory implements RequiredActionFactory {

    public static final String ID = "invitation-required-action";

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return new InvitationRequiredAction();
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayText() {
        return "Invitation";
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
