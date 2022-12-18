package dev.sultanov.keycloak.multitenancy.authentication;

import dev.sultanov.keycloak.multitenancy.models.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.models.jpa.JpaTenantProvider;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class InvitationRequiredAction implements RequiredActionProvider {

    public InvitationRequiredAction() {
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();
        JpaTenantProvider provider = context.getSession().getProvider(JpaTenantProvider.class);
        if (provider.getTenantInvitationsStream(realm, user).findAny().isPresent()) {
            user.addRequiredAction(InvitationRequiredActionFactory.ID);
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();
        JpaTenantProvider provider = context.getSession().getProvider(JpaTenantProvider.class);
        if (user.isEmailVerified() && user.getEmail() != null) {
            List<TenantInvitationModel> invitations = provider.getTenantInvitationsStream(realm, user).collect(Collectors.toList());
            if (!invitations.isEmpty()) {
                InvitationsData invitationsData = new InvitationsData(realm, invitations);
                Response challenge = context.form().setAttribute("data", invitationsData).createForm("invitations.ftl");
                context.challenge(challenge);
                return;
            }
        }
        context.ignore();
    }

    @Override
    public void processAction(RequiredActionContext context) {
        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        JpaTenantProvider provider = context.getSession().getProvider(JpaTenantProvider.class);
        List<String> selectedTenantIds = formData.get("invitations");
        provider.getTenantInvitationsStream(realm, user).forEach(
                invitation -> {
                    if (selectedTenantIds.contains(invitation.getTenant().getId())) {
                        invitation.getTenant().grantMembership(user, invitation.getRoles());
                    } else {
                        invitation.getTenant().revokeInvitation(invitation.getId());
                    }
                });

        context.success();
    }

    @Override
    public void close() {
    }
}
