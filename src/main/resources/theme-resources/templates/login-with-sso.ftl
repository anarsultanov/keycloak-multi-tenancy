<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false displayInfo=true; section>
    <#if section = "header">
        ${kcSanitize(msg("ssoHeader"))?no_esc}
    <#elseif section = "form">
        <form class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <p>${msg("ssoInfo")}</p>
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="sso-id" class="${properties.kcLabelClass!}">${msg("ssoLabel")}</label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <input type="text" id="sso-id" name="sso-id" class="${properties.kcInputClass!}" autofocus/>

                    <#if messagesPerField.existsError('sso-id')>
                        <span id="input-error-sso-id" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('sso-id'))?no_esc}
                        </span>
                    </#if>
                </div>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                           type="submit" value="${msg("doContinue")}"/>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>