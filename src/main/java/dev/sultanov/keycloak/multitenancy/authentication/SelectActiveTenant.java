package dev.sultanov.keycloak.multitenancy.authentication;

import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import java.util.stream.Collectors;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AuthenticationManager;

@JBossLog
public class SelectActiveTenant implements RequiredActionProvider, RequiredActionFactory {

    public static final String ID = "select-active-tenant";

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        log.debug("Evaluating triggers for select active tenant action");
        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();

        AuthenticationManager.AuthResult authResult = AuthenticationManager.authenticateIdentityCookie(context.getSession(), context.getRealm(), true);
        if (context.getAuthenticationSession().getUserSessionNotes().get("active-tenant") == null
                && (authResult == null || authResult.getSession().getNote("active-tenant") == null)) {

            log.debugf("No active tenant session note found");
            TenantProvider provider = context.getSession().getProvider(TenantProvider.class);
            var tenantMemberships = provider.getTenantMembershipsStream(realm, user).collect(Collectors.toList());
            if (tenantMemberships.size() == 1) {
                log.debugf("User is a member of a single tenant, setting active tenant automatically");
                context.getAuthenticationSession().setUserSessionNote("active-tenant", tenantMemberships.get(0).getTenant().getId());
            } else if (tenantMemberships.size() > 1) {
                log.debugf("Tenant selection is required, adding required action");
                user.addRequiredAction(ID);
            }
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();
        TenantProvider provider = context.getSession().getProvider(TenantProvider.class);
        var memberships = provider.getTenantMembershipsStream(realm, user).collect(Collectors.toList());
        log.debug("Initializing challenge to select an active tenant");
        Response challenge = context.form().setAttribute("data", TenantsBean.fromMembership(memberships)).createForm("select-tenant.ftl");
        context.challenge(challenge);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String selectedTenant = formData.getFirst("tenant");
        log.debugf("Active tenant selected %s, setting session note", selectedTenant);
        context.getAuthenticationSession().setUserSessionNote("active-tenant", selectedTenant);
        context.success();
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
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

}
