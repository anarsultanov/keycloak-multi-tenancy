<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${kcSanitize(msg("multiTenancyInvitationsHeader"))?no_esc}
    <#elseif section = "form">
        <div id="kc-terms-text">
            ${kcSanitize(msg("multiTenancyInvitationsInstruction"))?no_esc}
        </div>
        <form class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcFormOptionsClass!}">
                    <div class="${properties.kcFormOptionsWrapperClass!}">
                        <#list invitations.tenants as tenant>
                            <div class="checkbox">
                                <label class="${properties.kcLabelClass!}">
                                    <input id="tenant-${tenant.id}" name="tenants" type="checkbox" value="${tenant.id}" checked> ${tenant.name}
                                </label>
                            </div>
                        </#list>
                    </div>
                </div>
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                           name="accept" id="kc-accept" type="submit" value="${msg("doAccept")}"/>
                </div>
            </div>
        </form>
        <div class="clearfix"></div>
    </#if>
</@layout.registrationLayout>
