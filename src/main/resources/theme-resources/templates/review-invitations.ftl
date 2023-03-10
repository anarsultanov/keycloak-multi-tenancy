<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false displayInfo=true; section>
    <#if section = "header">
        ${kcSanitize(msg("reviewInvitationsHeader"))?no_esc}
    <#elseif section = "form">
        <form class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcFormOptionsClass!}">
                    <div class="${properties.kcFormOptionsWrapperClass!}">
                        <#list data.tenants as tenant>
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
    <#elseif section = "info" >
        ${msg("reviewInvitationsInfo")}
    </#if>
</@layout.registrationLayout>
