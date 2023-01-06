<#import "template.ftl" as layout>
<@layout.emailLayout>
    ${kcSanitize(msg("invitationEmailBodyHtml", tenantName, accountPageUri))?no_esc}
</@layout.emailLayout>