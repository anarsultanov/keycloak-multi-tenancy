<#import "template.ftl" as layout>
<@layout.emailLayout>
    ${kcSanitize(msg("invitationDeclinedEmailBodyHtml", inviteeEmail, tenantName))?no_esc}
</@layout.emailLayout>