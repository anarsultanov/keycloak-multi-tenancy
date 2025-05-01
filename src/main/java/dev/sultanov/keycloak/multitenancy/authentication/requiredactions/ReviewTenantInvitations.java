package dev.sultanov.keycloak.multitenancy.authentication.requiredactions;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import dev.sultanov.keycloak.multitenancy.authentication.TenantsBean;
import dev.sultanov.keycloak.multitenancy.email.EmailSender;
import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class ReviewTenantInvitations implements RequiredActionProvider, RequiredActionFactory {

    public static final String ID = "review-tenant-invitations";
    private static final String ACCEPTED_TENANTS_ATTR = "acceptedTenants";
    private static final String REJECTED_TENANTS_ATTR = "rejectedTenants";

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
            var invitations = provider.getTenantInvitationsStream(realm, user).toList();
            if (invitations.isEmpty()) {
                log.debug("No invitations found, challenge not required");
                context.success();
            } else {
            	 var challenge = context.form()
                         .setAttribute("data", TenantsBean.fromInvitations(invitations))
                         .createForm("review-invitations.ftl");
                 context.challenge(challenge);
            }
        }
    }

    @Override
    public void processAction(RequiredActionContext context) {
        var realm = context.getRealm();
        var user = context.getUser();
        var provider = context.getSession().getProvider(TenantProvider.class);
        var formData = context.getHttpRequest().getDecodedFormParameters();
        // Get accepted and rejected tenants from form data
        String acceptedTenantsStr = formData.getFirst(ACCEPTED_TENANTS_ATTR);
        String rejectedTenantsStr = formData.getFirst(REJECTED_TENANTS_ATTR);
        List<String> acceptedTenants = acceptedTenantsStr != null && !acceptedTenantsStr.isEmpty()
                ? Arrays.asList(acceptedTenantsStr.split(","))
                : Collections.emptyList();
        List<String> rejectedTenants = rejectedTenantsStr != null && !rejectedTenantsStr.isEmpty()
                ? Arrays.asList(rejectedTenantsStr.split(","))
                : Collections.emptyList();

        boolean hasMemberships = provider.getTenantMembershipsStream(realm, user).findAny().isPresent();

        boolean hasUnprocessedInvitations = provider.getTenantInvitationsStream(realm, user)
                .anyMatch(inv -> !rejectedTenants.contains(inv.getTenant().getId()));
        
		if (!hasMemberships && acceptedTenants.isEmpty() && hasUnprocessedInvitations) {
			var invitations = provider.getTenantInvitationsStream(realm, user).collect(Collectors.toList());
			var challenge = context.form().setError(
					"You must accept at least one tenant invitation to proceed if you have no existing memberships.")
					.setAttribute("data", TenantsBean.fromInvitations(invitations))
					.createForm("review-invitations.ftl");
			context.challenge(challenge);
			return;
		}

        Set<String> allProcessedTenants = new HashSet<>();
        allProcessedTenants.addAll(acceptedTenants);
        allProcessedTenants.addAll(rejectedTenants);

        for (String tenantId : allProcessedTenants) {
            Optional<TenantInvitationModel> invitation = provider.getTenantInvitationsStream(realm, user)
                    .filter(inv -> inv.getTenant().getId().equals(tenantId))
                    .findFirst();
            if (invitation.isPresent()) {
                var inv = invitation.get();
                String tenantName = inv.getTenant().getName();

                if (acceptedTenants.contains(tenantId)) {
                    log.debugf("Accepting invitation for tenant %s", tenantName);
                    inv.getTenant().grantMembership(user, inv.getRoles());
                    if (inv.getInvitedBy() != null) {
                        EmailSender.sendInvitationAcceptedEmail(context.getSession(), inv.getInvitedBy(), inv.getEmail(), tenantName);
                        log.warnf("accept tenant adding");
                    }
                } else if (rejectedTenants.contains(tenantId)) {
                    log.debugf("Rejecting invitation for tenant %s", tenantName);
                    if (inv.getInvitedBy() != null) {
                        EmailSender.sendInvitationDeclinedEmail(context.getSession(), inv.getInvitedBy(), inv.getEmail(), tenantName);
                        log.warnf("reject tenant adding");
                    }
                }
                inv.getTenant().revokeInvitation(inv.getId());
            } else {
                log.warnf("No invitation found for tenant ID: %s", tenantId);
            }
        }

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