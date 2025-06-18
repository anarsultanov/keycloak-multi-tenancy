package dev.sultanov.keycloak.multitenancy.authentication.requiredactions;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import dev.sultanov.keycloak.multitenancy.resource.UserServiceRestClient;
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

    private final UserServiceRestClient userServiceRestClient = new UserServiceRestClient();

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        log.debugf("Evaluating triggers for user: %s in realm: %s", context.getUser().getId(), context.getRealm().getId());
        var realm = context.getRealm();
        var user = context.getUser();
        var provider = context.getSession().getProvider(TenantProvider.class);
        if (provider.getTenantInvitationsStream(realm, user).findAny().isPresent()) {
            log.infof("Pending invitations found for user: %s, adding required action: %s", user.getId(), ID);
            user.addRequiredAction(ID);
        } else {
            log.debugf("No pending invitations for user: %s", user.getId());
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        log.debugf("Initiating required action challenge for user: %s in realm: %s", 
                   context.getUser().getId(), context.getRealm().getId());
        var realm = context.getRealm();
        var user = context.getUser();
        var provider = context.getSession().getProvider(TenantProvider.class);

        if (ObjectUtils.isNotEmpty(user.getEmail()) && user.isEmailVerified()) {
            var invitations = provider.getTenantInvitationsStream(realm, user).toList();
            log.debugf("Found %d invitations for user: %s, email: %s", 
                       invitations.size(), user.getId(), user.getEmail());
            if (invitations.isEmpty()) {
                log.infof("No invitations to review for user: %s, marking as success", user.getId());
                context.success();
            } else {
                log.infof("Presenting %d invitations to user: %s for review", invitations.size(), user.getId());
                var challenge = context.form()
                        .setAttribute("data", TenantsBean.fromInvitations(invitations))
                        .createForm("review-invitations.ftl");
                context.challenge(challenge);
            }
        } else {
            log.warnf("User: %s has no email or email not verified, skipping challenge", user.getId());
        }
    }

    @Override
    public void processAction(RequiredActionContext context) {
        log.infof("Processing action for user: %s in realm: %s on button click", 
                  context.getUser().getId(), context.getRealm().getId());
        var realm = context.getRealm();
        var user = context.getUser();
        var provider = context.getSession().getProvider(TenantProvider.class);
        var formData = context.getHttpRequest().getDecodedFormParameters();

        log.debugf("Received form data for user: %s, parameters: %s", user.getId(), formData.toString());
        String acceptedTenantsStr = formData.getFirst(ACCEPTED_TENANTS_ATTR);
        String rejectedTenantsStr = formData.getFirst(REJECTED_TENANTS_ATTR);

        List<String> acceptedTenants = ObjectUtils.isNotEmpty(acceptedTenantsStr)
                ? Arrays.asList(acceptedTenantsStr.split(","))
                : Collections.emptyList();
        List<String> rejectedTenants = ObjectUtils.isNotEmpty(rejectedTenantsStr)
                ? Arrays.asList(rejectedTenantsStr.split(","))
                : Collections.emptyList();

        log.infof("User: %s - Accepted tenants: %s, Rejected tenants: %s", 
                  user.getId(), acceptedTenants, rejectedTenants);

        boolean hasMemberships = provider.getTenantMembershipsStream(realm, user).findAny().isPresent();
        log.debugf("User: %s has existing memberships: %b", user.getId(), hasMemberships);

        boolean hasUnprocessedInvitations = provider.getTenantInvitationsStream(realm, user)
                .anyMatch(inv -> !rejectedTenants.contains(inv.getTenant().getId()));
        log.debugf("User: %s has unprocessed invitations: %b", user.getId(), hasUnprocessedInvitations);

        if (!hasMemberships && acceptedTenants.isEmpty() && hasUnprocessedInvitations) {
            var invitations = provider.getTenantInvitationsStream(realm, user).collect(Collectors.toList());
            log.warnf("User: %s has no memberships and no accepted tenants, requiring at least one acceptance", user.getId());
            var challenge = context.form()
                    .setError("You must accept at least one tenant invitation to proceed if you have no existing memberships.")
                    .setAttribute("data", TenantsBean.fromInvitations(invitations))
                    .createForm("review-invitations.ftl");
            context.challenge(challenge);
            return;
        }

        Set<String> allProcessedTenants = new HashSet<>();
        allProcessedTenants.addAll(acceptedTenants);
        allProcessedTenants.addAll(rejectedTenants);
        log.debugf("Processing %d tenants for user: %s - %s", 
                   allProcessedTenants.size(), user.getId(), allProcessedTenants);

        // Call User Service API for bulk status update
        log.infof("Initiating user service call for user: %s with accepted: %s, rejected: %s", 
                  user.getId(), acceptedTenants, rejectedTenants);
        userServiceRestClient.updateUserTenantInvitationStatuses(user.getId(), acceptedTenants, rejectedTenants);
        log.infof("Successfully completed user service call for user: %s", user.getId());

        for (String tenantId : allProcessedTenants) {
            Optional<TenantInvitationModel> invitation = provider.getTenantInvitationsStream(realm, user)
                    .filter(inv -> inv.getTenant().getId().equals(tenantId))
                    .findFirst();
            if (invitation.isPresent()) {
                var inv = invitation.get();
                String tenantName = inv.getTenant().getName();
                log.debugf("Processing tenant: %s (ID: %s) for user: %s", tenantName, tenantId, user.getId());

                if (acceptedTenants.contains(tenantId)) {
                    // Update tenant status to Active if not already Active
                    if (!"ACTIVE".equalsIgnoreCase(inv.getTenant().getStatus())) {
                        log.infof("Updating tenant: %s (ID: %s) status to Active for user: %s", 
                                  tenantName, tenantId, user.getId());
                        inv.getTenant().setStatus("ACTIVE");
                    }

                    log.infof("Granting membership for user: %s to tenant: %s with roles: %s", 
                              user.getId(), tenantName, inv.getRoles());
                    inv.getTenant().grantMembership(user, inv.getRoles());
                    if (ObjectUtils.isNotEmpty(inv.getInvitedBy())) {
                        log.debugf("Sending acceptance email for user: %s, tenant: %s, invited by: %s", 
                                   user.getId(), tenantName, inv.getInvitedBy());
                        EmailSender.sendInvitationAcceptedEmail(context.getSession(), inv.getInvitedBy(), inv.getEmail(), tenantName);
                    }
                } else if (rejectedTenants.contains(tenantId)) {
                    log.infof("Rejecting invitation for user: %s to tenant: %s", user.getId(), tenantName);
                    if (ObjectUtils.isNotEmpty(inv.getInvitedBy())) {
                        log.debugf("Sending rejection email for user: %s, tenant: %s, invited by: %s", 
                                   user.getId(), tenantName, inv.getInvitedBy());
                        EmailSender.sendInvitationDeclinedEmail(context.getSession(), inv.getInvitedBy(), inv.getEmail(), tenantName);
                    }
                }
                log.debugf("Revoking invitation for user: %s, tenant: %s, invitation ID: %s", 
                           user.getId(), tenantName, inv.getId());
                inv.getTenant().revokeInvitation(inv.getId());
            } else {
                log.warnf("No invitation found for tenant ID: %s for user: %s", tenantId, user.getId());
            }
        }

        if (provider.getTenantMembershipsStream(realm, user).findAny().isPresent()) {
            log.debugf("User: %s has memberships, removing CreateTenant and adding SelectActiveTenant action", user.getId());
            user.removeRequiredAction(CreateTenant.ID);
            user.addRequiredAction(SelectActiveTenant.ID);
        } else {
            log.debugf("User: %s has no memberships after processing", user.getId());
        }
        log.infof("Action processing completed successfully for user: %s", user.getId());
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