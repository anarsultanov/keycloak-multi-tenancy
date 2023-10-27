package dev.sultanov.keycloak.multitenancy.authentication.authenticators;

import dev.sultanov.keycloak.multitenancy.authentication.IdentityProviderTenantsConfig;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.util.Constants;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.Optional;
import java.util.Set;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.broker.util.PostBrokerLoginConstants;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

@JBossLog
public class IdpTenantMembershipsCreatingAuthenticator implements Authenticator {

    public void authenticate(AuthenticationFlowContext context) {
        var authSession = context.getAuthenticationSession();

        var firstLoginCtx = SerializedBrokeredIdentityContext.readFromAuthenticationSession(authSession, AbstractIdpAuthenticator.BROKERED_CONTEXT_NOTE);
        var postLoginCtx = SerializedBrokeredIdentityContext.readFromAuthenticationSession(authSession, PostBrokerLoginConstants.PBL_BROKERED_IDENTITY_CONTEXT);
        var serializedCtx = Optional.ofNullable(firstLoginCtx).orElse(postLoginCtx);

        if (serializedCtx == null) {
            throw new AuthenticationFlowException("Not found serialized context in clientSession", AuthenticationFlowError.IDENTITY_PROVIDER_ERROR);
        } else {
            BrokeredIdentityContext brokerContext = serializedCtx.deserialize(context.getSession(), authSession);
            if (!brokerContext.getIdpConfig().isEnabled()) {
                context.getEvent().user(context.getUser()).error("identity_provider_error");
                var challengeResponse = context.form().setError("identityProviderUnexpectedErrorMessage").createErrorPage(Status.BAD_REQUEST);
                context.failureChallenge(AuthenticationFlowError.IDENTITY_PROVIDER_ERROR, challengeResponse);
            }

            this.doAuthenticate(context, brokerContext);
        }
    }

    private void doAuthenticate(AuthenticationFlowContext context, BrokeredIdentityContext brokerContext) {
        log.debug("Evaluating the requirement to create tenant memberships");

        var idpTenantsConfig = IdentityProviderTenantsConfig.of(brokerContext.getIdpConfig());
        if (idpTenantsConfig.isTenantsSpecific()) {
            log.debug("Creating memberships for the following tenants: " + idpTenantsConfig.getAccessibleTenantIds());

            var realm = context.getRealm();
            var user = context.getUser();
            var provider = context.getSession().getProvider(TenantProvider.class);
            for (String tenantId : idpTenantsConfig.getAccessibleTenantIds()) {
                var tenantById = provider.getTenantById(realm, tenantId);
                if (tenantById.isEmpty()) {
                    log.warn("Tenant with ID %s, configured in IDP with alias %s, does not exist. Skipping membership creation."
                            .formatted(tenantId, brokerContext.getIdpConfig().getAlias()));
                } else if (tenantById.get().getMembership(user).isPresent()) {
                    log.debug("User is already a member of tenant with ID %s. Skipping membership creation.".formatted(tenantId));
                } else {
                    tenantById.get().grantMembership(user, Set.of(Constants.TENANT_USER_ROLE));
                    log.debug("Membership created in tenant with ID %s".formatted(tenantId));
                }
            }
        } else {
            log.debug("The Identity Provider is not tenant-specific, so there's no need to create memberships.");
        }
        context.success();
    }

    public void action(AuthenticationFlowContext context) {
        authenticate(context);
    }

    public boolean requiresUser() {
        return true;
    }

    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel) {

    }

    @Override
    public void close() {

    }
}
