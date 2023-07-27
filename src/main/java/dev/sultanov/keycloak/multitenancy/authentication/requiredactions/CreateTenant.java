package dev.sultanov.keycloak.multitenancy.authentication.requiredactions;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import jakarta.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.validation.Validation;

@JBossLog
public class CreateTenant implements RequiredActionProvider, RequiredActionFactory {

    public static final String ID = "create-tenant";

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        log.debug("Evaluating triggers for create tenant action");
        var realm = context.getRealm();
        var user = context.getUser();
        var provider = context.getSession().getProvider(TenantProvider.class);
        if (provider.getTenantMembershipsStream(realm, user).findAny().isEmpty()) {
            log.debug("No tenant membership(s) found, adding required action");
            user.addRequiredAction(ID);
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        log.debug("Initializing challenge to create a tenant");
        Response challenge = context.form().createForm("create-tenant.ftl");
        context.challenge(challenge);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        var realm = context.getRealm();
        var user = context.getUser();
        var provider = context.getSession().getProvider(TenantProvider.class);
        var formData = context.getHttpRequest().getDecodedFormParameters();
        var tenantName = formData.getFirst("tenantName");

        if (Validation.isBlank(tenantName)) {
            Response challenge = context.form()
                    .addError(new FormMessage("tenantName", "tenantEmptyError"))
                    .createForm("create-tenant.ftl");
            context.challenge(challenge);
            return;
        } else if (provider.getTenantsStream(realm).map(TenantModel::getName).anyMatch(tenantName::equals)) {
            Response challenge = context.form()
                    .addError(new FormMessage("tenantName", "tenantExistsError"))
                    .createForm("create-tenant.ftl");
            context.challenge(challenge);
            return;
        }

        provider.createTenant(realm, tenantName, user);
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
        return "Create tenant";
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
