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

        // Get accepted and rejected tenants from form data
        String acceptedTenantsStr = formData.getFirst("acceptedTenants");
        String rejectedTenantsStr = formData.getFirst("rejectedTenants");
        List<String> acceptedTenants = acceptedTenantsStr != null && !acceptedTenantsStr.isEmpty()
                ? Arrays.asList(acceptedTenantsStr.split(","))
                : Collections.emptyList();
        List<String> rejectedTenants = rejectedTenantsStr != null && !rejectedTenantsStr.isEmpty()
                ? Arrays.asList(rejectedTenantsStr.split(","))
                : Collections.emptyList();

        boolean hasMemberships = provider.getTenantMembershipsStream(realm, user).findAny().isPresent();

     // Process accepted and rejected tenants
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