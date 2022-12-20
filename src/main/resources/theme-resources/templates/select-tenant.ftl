<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${kcSanitize(msg("multiTenancySelectTenantHeader"))?no_esc}
    <#elseif section = "form">
        <div id="kc-terms-text">
            ${kcSanitize(msg("multiTenancySelectTenantInstruction"))?no_esc}
        </div>
        <form class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <label class="${properties.kcLabelWrapperClass!}">
                    <select class="${properties.kcFormOptionsClass!}" name="tenant" required>
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
        <div class="clearfix"></div>
    </#if>
</@layout.registrationLayout>
