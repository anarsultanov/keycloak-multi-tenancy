package dev.sultanov.keycloak.multitenancy.authentication.requiredactions;

import dev.sultanov.keycloak.multitenancy.authentication.TenantsBean;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.util.EmailUtil;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

@JBossLog
public class ReviewTenantInvitations implements RequiredActionProvider, RequiredActionFactory {

    public static final String ID = "review-tenant-invitations";

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        log.debug("Evaluating triggers for review tenant invitations action");
        var realm = context.getRealm();
        var user = context.getUser();
        var provider = context.getSession().getProvider(TenantProvider.class);
        if (provider.getTenantInvitationsStream(realm, user).findAny().isPresent()) {
            log.debug("Pending invitation(s) found, adding required action");
            user.addRequiredAction(ID);
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        var realm = context.getRealm();
        var user = context.getUser();
        var provider = context.getSession().getProvider(TenantProvider.class);
        if (user.getEmail() != null && user.isEmailVerified()) {
            log.debug("User email is missing or not verified, skipping challenge");
            var invitations = provider.getTenantInvitationsStream(realm, user).collect(Collectors.toList());
            if (invitations.isEmpty()) {
                log.debug("No invitations found, challenge not required");
                context.success();
            } else {
                log.debug("Invitations found, initializing challenge");
                var challenge = context.form().setAttribute("data", TenantsBean.fromInvitations(invitations)).createForm("review-invitations.ftl");
                context.challenge(challenge);
            }
        }
    }

    @Override
    public void processAction(RequiredActionContext context) {
        var realm = context.getRealm();
        var user = context.getUser();
        var formData = context.getHttpRequest().getDecodedFormParameters();
        var provider = context.getSession().getProvider(TenantProvider.class);
        var selectedTenantIds = formData.get("tenants") != null ? formData.get("tenants") : List.of();
        provider.getTenantInvitationsStream(realm, user).forEach(
                invitation -> {
                    if (selectedTenantIds.contains(invitation.getTenant().getId())) {
                        log.debugf("%s invitation accepted, granting membership", invitation.getTenant().getName());
                        invitation.getTenant().grantMembership(user, invitation.getRoles());
                        EmailUtil.sendInvitationAcceptedEmail(context.getSession(), invitation.getInvitedBy().getEmail(), invitation.getEmail(), invitation.getTenant().getName());
                    } else {
                        log.debugf("%s invitation declined and will be revoked", invitation.getTenant().getName());
                        EmailUtil.sendInvitationDeclinedEmail(context.getSession(), invitation.getInvitedBy().getEmail(), invitation.getEmail(), invitation.getTenant().getName());
                    }
                    invitation.getTenant().revokeInvitation(invitation.getId());
                });

        // This action changes user memberships, so we need to re-evaluate required actions.
        if (provider.getTenantMembershipsStream(realm, user).findAny().isPresent()) {
            user.removeRequiredAction(CreateTenant.ID);
            user.addRequiredAction(SelectActiveTenant.ID);
        }
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
