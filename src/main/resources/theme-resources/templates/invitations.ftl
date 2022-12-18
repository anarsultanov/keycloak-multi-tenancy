<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${kcSanitize(msg("multiTenancyInvitationsHeader"))?no_esc}
    <#elseif section = "form">
    <div id="kc-terms-text">
        ${kcSanitize(msg("multiTenancyInvitationsInstruction"))?no_esc}
    </div>
    <form class="form-actions" action="${url.loginAction}" method="POST">
    <#list data.invitations as invitation>
      <div class="checkbox">
        <label>
          <input id="tenant-${invitation.id}" name="invitations" type="checkbox" value="${invitation.id}" checked> ${invitation.name}
        </label>
      </div>
    </#list>
      <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" name="accept" id="kc-accept" type="submit" value="${msg("doAccept")}"/>
    </form>
    <div class="clearfix"></div>
    </#if>
</@layout.registrationLayout>
