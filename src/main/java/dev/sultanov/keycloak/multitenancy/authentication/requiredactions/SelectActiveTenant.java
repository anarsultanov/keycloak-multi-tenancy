package dev.sultanov.keycloak.multitenancy.authentication.requiredactions;

import dev.sultanov.keycloak.multitenancy.authentication.IdentityProviderTenantsConfig;
import dev.sultanov.keycloak.multitenancy.authentication.TenantsBean;
import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.util.Constants;
import jakarta.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionConfigModel;
import org.keycloak.models.RequiredActionProviderModel;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.utils.RequiredActionHelper;

import java.util.List;
import java.util.Optional;

import static dev.sultanov.keycloak.multitenancy.util.Constants.IDENTITY_PROVIDER_SESSION_NOTE;


@JBossLog
public class SelectActiveTenant implements RequiredActionProvider, RequiredActionFactory {

    public static final String ID = "select-active-tenant";
    private static final int DEFAULT_MAX_AUTH_AGE = 365 * 24 * 60; // 365 days in minutes

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        log.debug("Evaluating triggers for select active tenant action");
        if (getSessionNote(context, Constants.ACTIVE_TENANT_ID_SESSION_NOTE).isPresent()) {
            return;
        }

        log.debug("No active tenant session note found");
        var tenantMemberships = getFilteredTenantMemberships(context);
        if (tenantMemberships.size() == 1) {
            log.debug("User is a member of a single tenant, setting active tenant automatically");
            context.getAuthenticationSession().setUserSessionNote(Constants.ACTIVE_TENANT_ID_SESSION_NOTE, tenantMemberships.get(0).getTenant().getId());
        } else if (tenantMemberships.size() > 1) {
            log.debug("Tenant selection is required, adding required action");
            context.getUser().addRequiredAction(ID);
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        var tenantMemberships = getFilteredTenantMemberships(context);
        if (tenantMemberships.isEmpty()) {
            context.success();
        } else if (tenantMemberships.size() == 1) {
            log.debugf("User is a member of a single tenant, setting active tenant automatically");
            context.getAuthenticationSession().setUserSessionNote(Constants.ACTIVE_TENANT_ID_SESSION_NOTE, tenantMemberships.get(0).getTenant().getId());
            context.success();
        } else {
            log.debug("Initializing challenge to select an active tenant");
            Response challenge = context.form().setAttribute("data", TenantsBean.fromMembership(tenantMemberships)).createForm("select-tenant.ftl");
            context.challenge(challenge);
        }
    }

    @Override
    public void processAction(RequiredActionContext context) {
        var realm = context.getRealm();
        var user = context.getUser();
        var provider = context.getSession().getProvider(TenantProvider.class);
        var memberships = provider.getTenantMembershipsStream(realm, user).toList();

        var formData = context.getHttpRequest().getDecodedFormParameters();
        var selectedTenant = formData.getFirst("tenant");

        if (memberships.stream().anyMatch(membership -> membership.getTenant().getId().equals(selectedTenant))) {
            log.debugf("Active tenant selected %s, setting session note", selectedTenant);
            context.getAuthenticationSession().setUserSessionNote(Constants.ACTIVE_TENANT_ID_SESSION_NOTE, selectedTenant);
            context.success();
        } else {
            log.warnf("User %s is not a member of the selected tenant %s", user.getId(), selectedTenant);
            context.failure();
        }
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return this;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayText() {
        return "Select active tenant";
    }

    @Override
    public InitiatedActionSupport initiatedActionSupport() {
        return InitiatedActionSupport.SUPPORTED;
    }

    @Override
    @Deprecated(since = "26.3.0", forRemoval = true)
    public int getMaxAuthAge() {
        return getMaxAuthAge(null);
    }

    @Override
    public int getMaxAuthAge(KeycloakSession session) {
        if (session == null) {
            return DEFAULT_MAX_AUTH_AGE;
        }

        KeycloakContext keycloakContext = session.getContext();
        RealmModel realm = keycloakContext.getRealm();
        int maxAge;

        // try required action config
        AuthenticationSessionModel authSession = keycloakContext.getAuthenticationSession();
        if (authSession != null) {

            // we need to figure out the alias for the current required action
            String providerId = authSession.getClientNote(org.keycloak.models.Constants.KC_ACTION);
            RequiredActionProviderModel requiredAction = RequiredActionHelper.getRequiredActionByProviderId(realm, providerId);

            if (requiredAction != null) {
                RequiredActionConfigModel configModel = realm.getRequiredActionConfigByAlias(requiredAction.getAlias());
                if (configModel != null && configModel.containsConfigKey(org.keycloak.models.Constants.MAX_AUTH_AGE_KEY)) {
                    maxAge = RequiredActionFactory.parseMaxAuthAge(configModel);
                    if (maxAge >= 0) {
                        return maxAge;
                    }
                }
            }
        }

        return DEFAULT_MAX_AUTH_AGE;
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

    /**
     * Retrieves and filters the tenant memberships based on Identity Provider configuration.
     */
    private List<TenantMembershipModel> getFilteredTenantMemberships(RequiredActionContext context) {
        var idpTenantsConfig = getIdentityProviderTenantsConfig(context);
        var provider = context.getSession().getProvider(TenantProvider.class);
        var tenantMembershipsStream = provider.getTenantMembershipsStream(context.getRealm(), context.getUser());
        if (idpTenantsConfig.isPresent() && idpTenantsConfig.get().isTenantsSpecific()) {
            log.debug("Filtering tenant memberships based on Identity Provider configuration");
            var tenantMembershipModels = tenantMembershipsStream.filter(
                            membership -> idpTenantsConfig.get().getAccessibleTenantIds().contains(membership.getTenant().getId()))
                    .toList();
            if (tenantMembershipModels.isEmpty()) {
                throw new AuthenticationFlowException("User does not have access to any of IDP tenants", AuthenticationFlowError.ACCESS_DENIED);
            }
            return tenantMembershipModels;
        } else {
            log.debug("Filtering not required based on Identity Provider configuration");
            return tenantMembershipsStream.toList();
        }
    }

    /**
     * Retrieves the optional Identity Provider based on the available session data and returns its configuration.
     *
     * @see <a href="https://www.keycloak.org/docs/latest/server_admin/#available-user-session-data">Keycloak Documentation - Available User Session Data</a>
     */
    private Optional<IdentityProviderTenantsConfig> getIdentityProviderTenantsConfig(RequiredActionContext context) {
        return getSessionNote(context, IDENTITY_PROVIDER_SESSION_NOTE)
                .map(context.getSession().identityProviders()::getByAlias)
                .map(IdentityProviderTenantsConfig::of);
    }

    /**
     * When a user first opens a browser and wants to authenticate, an authentication session is created. This auth session is used throughout the entire
     * authentication process. After successful authentication, a user session is created, and the notes from authentication session are set to the user
     * session.
     * <p>
     * When the same user wants to authenticate again in the same browser session, automatic Single Sign-On (SSO) takes place. This results in the creation of a
     * completely new authentication session. However, the user session remains the same and is shared among all authentication sessions in the same browser
     * session.
     * <p>
     * Therefore, for subsequent SSO authentications, it's necessary to retrieve the notes from the user session.
     */
    private Optional<String> getSessionNote(RequiredActionContext context, String key) {
        var authSessionNote = Optional.ofNullable(context.getAuthenticationSession().getUserSessionNotes().get(key));
        var userSessionNote = Optional.ofNullable(AuthenticationManager.authenticateIdentityCookie(context.getSession(), context.getRealm(), true))
                .map(authResult -> authResult.getSession().getNote(key));

        return authSessionNote.or(() -> userSessionNote);
    }
}
