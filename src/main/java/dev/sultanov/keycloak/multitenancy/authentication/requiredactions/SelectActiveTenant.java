package dev.sultanov.keycloak.multitenancy.authentication.requiredactions;

import dev.sultanov.keycloak.multitenancy.authentication.TenantsBean;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.util.Constants;
import jakarta.ws.rs.core.Response;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.managers.AuthenticationManager;

@JBossLog
public class SelectActiveTenant implements RequiredActionProvider, RequiredActionFactory {

    public static final String ID = "select-active-tenant";

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        log.debug("Evaluating triggers for select active tenant action");
        var realm = context.getRealm();
        var user = context.getUser();
        var authSessionNote = context.getAuthenticationSession().getUserSessionNotes().get(Constants.ACTIVE_TENANT_ID_SESSION_NOTE);
        var authResult = AuthenticationManager.authenticateIdentityCookie(context.getSession(), context.getRealm(), true);
        var userSessionNote = authResult != null ? authResult.getSession().getNote(Constants.ACTIVE_TENANT_ID_SESSION_NOTE) : null;
        if (authSessionNote == null && userSessionNote == null) {
            log.debugf("No active tenant session note found");
            TenantProvider provider = context.getSession().getProvider(TenantProvider.class);
            var tenantMemberships = provider.getTenantMembershipsStream(realm, user).collect(Collectors.toList());
            if (tenantMemberships.size() == 1) {
                log.debugf("User is a member of a single tenant, setting active tenant automatically");
                context.getAuthenticationSession().setUserSessionNote(Constants.ACTIVE_TENANT_ID_SESSION_NOTE, tenantMemberships.get(0).getTenant().getId());
            } else if (tenantMemberships.size() > 1) {
                log.debugf("Tenant selection is required, adding required action");
                user.addRequiredAction(ID);
            }
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        var realm = context.getRealm();
        var user = context.getUser();
        var provider = context.getSession().getProvider(TenantProvider.class);
        var tenantMemberships = provider.getTenantMembershipsStream(realm, user).collect(Collectors.toList());
        if (tenantMemberships.size() == 0) {
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
        var memberships = provider.getTenantMembershipsStream(realm, user).collect(Collectors.toList());

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
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

}
