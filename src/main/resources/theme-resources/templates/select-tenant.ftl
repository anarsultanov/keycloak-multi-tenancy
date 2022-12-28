<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false displayInfo=true; section>
    <#if section = "header">
        ${kcSanitize(msg("selectTenantHeader"))?no_esc}
    <#elseif section = "form">
        <form class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <label class="${properties.kcLabelWrapperClass!}">
                    <select class="${properties.kcInputClass!}" name="tenant" required>
                        <#list data.tenants as tenant>
                            <option value="${tenant.id}">${tenant.name}</option>
                        </#list>
                    </select>
                </label>
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                           name="accept" id="kc-accept" type="submit" value="${msg("doLogIn")}"/>
                </div>
            </div>
        </form>
    <#elseif section = "info" >
        ${msg("selectTenantInfo")}
    </#if>
</@layout.registrationLayout>
