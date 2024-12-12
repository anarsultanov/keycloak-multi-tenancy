<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false displayInfo=true; section>
    <#if section = "header">
        ${kcSanitize(msg("createTenantHeader"))?no_esc}
    <#elseif section = "form">
        <form class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <p>${msg("createTenantInfo")}</p>
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="tenantName" class="${properties.kcLabelClass!}">${msg("tenantName")}</label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <input type="text" id="tenantName" name="tenantName" class="${properties.kcInputClass!}" autocomplete="off"
                           aria-invalid="<#if messagesPerField.existsError('tenantName')>true</#if>"
                    />

                    <#if messagesPerField.existsError('tenantName')>
                        <span id="input-error-tenantName" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('tenantName'))?no_esc}
                        </span>
                    </#if>
                </div>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                           type="submit" value="${msg("doSubmit")}"/>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
