package dev.sultanov.keycloak.multitenancy.authentication.requiredactions;

import dev.sultanov.keycloak.multitenancy.authentication.TenantsBean;
import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.email.EmailSender;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserModel;

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
        var selectedTenantIds = formData.get("selected-tenants") != null ? formData.get("selected-tenants") : List.of();
        var rejectedTenantIds = formData.get("rejected-tenants") != null ? formData.get("rejected-tenants") : List.of();
        provider.getTenantInvitationsStream(realm, user).forEach(invitation -> processInvitation(context, user, selectedTenantIds, rejectedTenantIds, invitation));

        // This action changes user memberships, so we need to re-evaluate required actions.
        if (provider.getTenantMembershipsStream(realm, user).findAny().isPresent()) {
            user.removeRequiredAction(CreateTenant.ID);
            user.addRequiredAction(SelectActiveTenant.ID);
        } else {
        	context.success();
        }
        
    }

    private static void processInvitation(RequiredActionContext context, UserModel user, List<?> selectedTenantIds,  List<?> rejectedTenantIds, TenantInvitationModel invitation) {
        var inviter = invitation.getInvitedBy();
        if (selectedTenantIds.contains(invitation.getTenant().getId())) {
            log.debugf("%s invitation accepted, granting membership", invitation.getTenant().getName());
            invitation.getTenant().grantMembership(user, invitation.getRoles());
            if (inviter != null) {
                EmailSender.sendInvitationAcceptedEmail(context.getSession(), inviter, invitation.getEmail(), invitation.getTenant().getName());
            }
        } else if (rejectedTenantIds.contains(invitation.getTenant().getId())) {
            log.debugf("%s invitation declined and will be revoked", invitation.getTenant().getName());
            if (inviter != null) {
                EmailSender.sendInvitationDeclinedEmail(context.getSession(), inviter, invitation.getEmail(), invitation.getTenant().getName());
            }
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
