<#import "template.ftl" as layout>
<@layout.emailLayout>
    ${kcSanitize(msg("invitationAcceptedEmailBodyHtml", inviteeEmail, tenantName))?no_esc}
</@layout.emailLayout>