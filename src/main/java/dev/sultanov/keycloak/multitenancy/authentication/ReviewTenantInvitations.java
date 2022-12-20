package dev.sultanov.keycloak.multitenancy.authentication;

import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import java.util.List;
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

@JBossLog
public class ReviewTenantInvitations implements RequiredActionProvider, RequiredActionFactory {

    public static final String ID = "review-tenant-invitations";

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        log.debug("Evaluating triggers for review tenant invitations action");
        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();
        TenantProvider provider = context.getSession().getProvider(TenantProvider.class);
        if (provider.getTenantInvitationsStream(realm, user).findAny().isPresent()) {
            log.debug("Pending invitation(s) found, adding required action");
            user.addRequiredAction(ID);
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();
        TenantProvider provider = context.getSession().getProvider(TenantProvider.class);
        if (user.getEmail() != null && user.isEmailVerified()) {
            log.debug("User email is missing or not verified, skipping challenge");
            List<TenantInvitationModel> invitations = provider.getTenantInvitationsStream(realm, user).collect(Collectors.toList());
            if (!invitations.isEmpty()) {
                log.debug("Invitations found, initializing challenge");
                Response challenge = context.form().setAttribute("data", TenantsBean.fromInvitations(invitations)).createForm("review-invitations.ftl");
                context.challenge(challenge);
                return;
            }
            log.debug("No invitations found, challenge not required");
        }
        context.ignore();
    }

    @Override
    public void processAction(RequiredActionContext context) {
        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        TenantProvider provider = context.getSession().getProvider(TenantProvider.class);
        List<String> selectedTenantIds = formData.get("tenants");
        provider.getTenantInvitationsStream(realm, user).forEach(
                invitation -> {
                    if (selectedTenantIds.contains(invitation.getTenant().getId())) {
                        log.debugf("%s invitation accepted, granting membership", invitation.getTenant().getName());
                        invitation.getTenant().grantMembership(user, invitation.getRoles());
                    }
                    log.debugf("%s invitation declined, revoking invitation", invitation.getTenant().getName());
                    invitation.getTenant().revokeInvitation(invitation.getId());
                });

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
        return "Review tenant invitations";
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
