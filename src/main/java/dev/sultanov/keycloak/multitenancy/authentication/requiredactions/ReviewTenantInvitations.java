package dev.sultanov.keycloak.multitenancy.authentication.requiredactions;

import dev.sultanov.keycloak.multitenancy.authentication.TenantsBean;
import dev.sultanov.keycloak.multitenancy.email.EmailSender;
import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserModel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

        if (user.getEmail() == null || !user.isEmailVerified()) {
            log.debug("User email is missing or not verified, skipping challenge");
            context.success();
            return;
        }

        var invitations = provider.getTenantInvitationsStream(realm, user).collect(Collectors.toList());
        if (invitations.isEmpty()) {
            log.debug("No invitations found, challenge not required");
            context.success();
            return;
        }

        var challenge = context.form()
                .setAttribute("data", TenantsBean.fromInvitations(invitations))
                .createForm("review-invitations.ftl");
        context.challenge(challenge);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        var realm = context.getRealm();
        var user = context.getUser();
        var provider = context.getSession().getProvider(TenantProvider.class);
        var formData = context.getHttpRequest().getDecodedFormParameters();
        var proceed = formData.getFirst("action");

        // Handle proceed action
        if (!"proceed".equalsIgnoreCase(proceed)) {
            log.debug("Invalid action, redisplaying challenge");
            requiredActionChallenge(context);
            return;
        }

        // Get accepted and rejected tenants from form data
        String acceptedTenantsStr = formData.getFirst("acceptedTenants");
        String rejectedTenantsStr = formData.getFirst("rejectedTenants");
        List<String> acceptedTenants = acceptedTenantsStr != null && !acceptedTenantsStr.isEmpty()
                ? Arrays.asList(acceptedTenantsStr.split(","))
                : Collections.emptyList();
        List<String> rejectedTenants = rejectedTenantsStr != null && !rejectedTenantsStr.isEmpty()
                ? Arrays.asList(rejectedTenantsStr.split(","))
                : Collections.emptyList();

        log.debugf("Received accepted tenants: %s", acceptedTenants);
        log.debugf("Received rejected tenants: %s", rejectedTenants);

//        // Store accepted and rejected tenants in backend before processing
        setProcessedTenants(user, ACCEPTED_TENANTS_ATTR, acceptedTenants);
        setProcessedTenants(user, REJECTED_TENANTS_ATTR, rejectedTenants);

        boolean hasMemberships = provider.getTenantMembershipsStream(realm, user).findAny().isPresent();

        // Process accepted tenants
        for (String tenantId : acceptedTenants) {
            Optional<TenantInvitationModel> invitation = provider.getTenantInvitationsStream(realm, user)
                    .filter(inv -> inv.getTenant().getId().equals(tenantId))
                    .findFirst();
            if (invitation.isPresent()) {
                var inv = invitation.get();
                log.debugf("Accepting invitation for tenant %s", inv.getTenant().getName());
                inv.getTenant().grantMembership(user, inv.getRoles());
                if (inv.getInvitedBy() != null) {
                    EmailSender.sendInvitationAcceptedEmail(context.getSession(), inv.getInvitedBy(), inv.getEmail(), inv.getTenant().getName());
                }
                inv.getTenant().revokeInvitation(inv.getId());
            } else {
                log.warnf("No invitation found for accepted tenant ID: %s", tenantId);
            }
        }

        // Process rejected tenants
        for (String tenantId : rejectedTenants) {
            Optional<TenantInvitationModel> invitation = provider.getTenantInvitationsStream(realm, user)
                    .filter(inv -> inv.getTenant().getId().equals(tenantId))
                    .findFirst();
            if (invitation.isPresent()) {
                var inv = invitation.get();
                log.debugf("Rejecting invitation for tenant %s", inv.getTenant().getName());
                if (inv.getInvitedBy() != null) {
                    EmailSender.sendInvitationDeclinedEmail(context.getSession(), inv.getInvitedBy(), inv.getEmail(), inv.getTenant().getName());
                }
                inv.getTenant().revokeInvitation(inv.getId());
            } else {
                log.warnf("No invitation found for rejected tenant ID: %s", tenantId);
            }
        }

        // Check for unprocessed invitations
        if (!hasMemberships && acceptedTenants.isEmpty()) {
            boolean hasUnprocessedInvitations = provider.getTenantInvitationsStream(realm, user)
                    .filter(inv -> !rejectedTenants.contains(inv.getTenant().getId()))
                    .findAny().isPresent();
            if (hasUnprocessedInvitations) {
                var invitations = provider.getTenantInvitationsStream(realm, user).collect(Collectors.toList());
                var challenge = context.form()
                        .setError("You must accept at least one tenant invitation to proceed if you have no existing memberships.")
                        .setAttribute("data", TenantsBean.fromInvitations(invitations))
                        .createForm("review-invitations.ftl");
                context.challenge(challenge);
                return;
            }
        }

        // Set next required action
        user.removeRequiredAction(ID);
        if (hasMemberships || !acceptedTenants.isEmpty()) {
            user.removeRequiredAction(CreateTenant.ID);
            user.addRequiredAction(SelectActiveTenant.ID);
        } else {
            user.removeRequiredAction(SelectActiveTenant.ID);
            user.addRequiredAction(CreateTenant.ID);
        }
        context.success();
    }

    private void setProcessedTenants(UserModel user, String attributeName, List<String> tenants) {
        user.setAttribute(attributeName, tenants);
        log.debugf("Stored %s: %s", attributeName, tenants);
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