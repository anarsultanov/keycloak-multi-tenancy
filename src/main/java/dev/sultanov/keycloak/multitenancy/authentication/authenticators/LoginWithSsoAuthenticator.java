package dev.sultanov.keycloak.multitenancy.authentication.authenticators;

import static java.util.function.Predicate.not;
import static org.keycloak.services.resources.IdentityBrokerService.getIdentityProviderFactory;

import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationProcessor;
import org.keycloak.authentication.Authenticator;
import org.keycloak.broker.provider.AuthenticationRequest;
import org.keycloak.broker.provider.util.IdentityBrokerState;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.Urls;
import org.keycloak.services.managers.ClientSessionCode;
import org.keycloak.sessions.AuthenticationSessionModel;

public class LoginWithSsoAuthenticator implements Authenticator {

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        var challenge = context.form().createForm("login-with-sso.ftl");
        context.challenge(challenge);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        var ssoId = formData.getFirst("sso-id");
        var identityProviderModel = context.getRealm().getIdentityProvidersStream()
                .filter(idp -> idp.getAlias().equals(ssoId))
                .filter(IdentityProviderModel::isEnabled)
                .filter(not(IdentityProviderModel::isLinkOnly))
                .findFirst();
        if (identityProviderModel.isPresent()) {
            performLogin(context, identityProviderModel.get());
        } else {
            var response = context.form()
                    .addError(new FormMessage("sso-id", "ssoError"))
                    .createForm("login-with-sso.ftl");
            context.challenge(response);
        }
    }

    private void performLogin(AuthenticationFlowContext context, IdentityProviderModel idp) {
        String providerAlias = idp.getAlias();

        var keycloakSession = context.getSession();
        var identityProvider = getIdentityProviderFactory(keycloakSession, idp).create(keycloakSession, idp);
        var authenticationRequest = createAuthenticationRequest(context, providerAlias);
        var response = identityProvider.performLogin(authenticationRequest);
        context.forceChallenge(response);
    }

    private AuthenticationRequest createAuthenticationRequest(AuthenticationFlowContext context, String providerId) {
        var realm = context.getRealm();
        var keycloakSession = context.getSession();
        var keycloakUriInfo = keycloakSession.getContext().getUri();
        var redirectUri = Urls.identityProviderAuthnResponse(keycloakUriInfo.getBaseUri(), providerId, realm.getName()).toString();

        var clientSessionCode = new ClientSessionCode<>(keycloakSession, context.getRealm(), context.getAuthenticationSession());
        clientSessionCode.setAction(AuthenticationSessionModel.Action.AUTHENTICATE.name());
        var authSession = clientSessionCode.getClientSession();
        var brokerState = IdentityBrokerState.decoded(
                clientSessionCode.getOrGenerateCode(),
                authSession.getClient().getId(),
                authSession.getClient().getClientId(),
                authSession.getTabId(),
                AuthenticationProcessor.getClientData(keycloakSession, authSession)
        );

        return new AuthenticationRequest(keycloakSession, realm, authSession, context.getHttpRequest(), keycloakUriInfo, brokerState, redirectUri);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {

    }

    @Override
    public void close() {

    }
}
