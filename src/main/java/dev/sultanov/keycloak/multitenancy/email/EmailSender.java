package dev.sultanov.keycloak.multitenancy.email;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.Urls;

@UtilityClass
public class EmailSender {

    public static void sendInvitationEmail(KeycloakSession session, UserModel invitee, String tenantName) {
        var accountPageUri = Urls.accountBase(session.getContext().getUri().getBaseUri()).build(session.getContext().getRealm().getName());
        var bodyAttributes = new HashMap<String, Object>();
        bodyAttributes.put("tenantName", tenantName);
        bodyAttributes.put("accountPageUri", accountPageUri);
        sendEmail(session, invitee, "invitationEmailSubject", List.of(tenantName), "invitation-email.ftl", bodyAttributes);
    }

    public static void sendInvitationAcceptedEmail(KeycloakSession session, UserModel inviter, String inviteeEmail, String tenantName) {
        var bodyAttributes = new HashMap<String, Object>();
        bodyAttributes.put("inviteeEmail", inviteeEmail);
        bodyAttributes.put("tenantName", tenantName);
        sendEmail(session, inviter, "invitationAcceptedEmailSubject", List.of(), "invitation-accepted-email.ftl", bodyAttributes);
    }

    public static void sendInvitationDeclinedEmail(KeycloakSession session, UserModel inviter, String inviteeEmail, String tenantName) {
        var bodyAttributes = new HashMap<String, Object>();
        bodyAttributes.put("inviteeEmail", inviteeEmail);
        bodyAttributes.put("tenantName", tenantName);
        sendEmail(session, inviter, "invitationDeclinedEmailSubject", List.of(), "invitation-declined-email.ftl", bodyAttributes);
    }

    private static void sendEmail(KeycloakSession session, UserModel recipient, String subject, List<Object> subjectAttributes, String template,
            Map<String, Object> bodyAttributes) {
        try {
            session.getProvider(EmailTemplateProvider.class)
                    .setRealm(session.getContext().getRealm())
                    .setUser(recipient)
                    .send(subject, subjectAttributes, template, bodyAttributes);
        } catch (EmailException e) {
            ServicesLogger.LOGGER.failedToSendEmail(e);
        }
    }
}