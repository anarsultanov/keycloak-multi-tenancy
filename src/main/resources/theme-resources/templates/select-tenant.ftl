<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false displayInfo=true; section>
    <#if section == "header">
        ${kcSanitize(msg("selectTenantHeader"))?no_esc}
    <#elseif section == "form">
        <div class="tenant-selection-container">
            <h2>${kcSanitize(msg("selectTenantHeader"))}</h2>
            <p>${msg("selectTenantInfo")}</p>

            <#if data.tenants?has_content>
                <form class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
                    <div class="tenant-selection-wrapper">
                        <#list data.tenants as tenant>
                            <div class="tenant-selection-card">
                                <div class="tenant-details">
                                    <#if tenant.logoUrl?? && tenant.logoUrl?length gt 0>
                                        <img src="${tenant.logoUrl}" alt="${kcSanitize(tenant.name)} Logo" class="tenant-logo" />
                                    <#else>
                                        <img src="${url.resourcesPath}/img/default-logo.png" alt="Default Logo" class="tenant-logo" />
                                    </#if>
                                    <div class="tenant-info">
                                        <p><strong>${kcSanitize(tenant.name)}</strong></p>
                                        <#if tenant.roles?has_content>
                                            <p>Roles: ${kcSanitize(tenant.roles?join(", "))}</p>
                                        <#else>
                                            <p>Roles: None</p>
                                        </#if>
                                    </div>
                                </div>
                                <div class="tenant-actions">
                                    <button
                                        type="submit"
                                        name="tenant"
                                        value="${tenant.id}"
                                        class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!}"
                                    >
                                        Select
                                    </button>
                                </div>
                            </div>
                        </#list>
                    </div>
                </form>
            <#else>
                <p>No tenants available.</p>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>

<style>
    .tenant-selection-container {
        background-color: #2c2f33;
        padding: 20px;
        border-radius: 8px;
        color: #ffffff;
        max-width: 500px;
        margin: 0 auto;
        box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    }

    .tenant-selection-container h2 {
        font-size: 1.5em;
        margin-bottom: 10px;
        color: #00b4d8;
    }

    .tenant-selection-container p {
        font-size: 0.9em;
        margin-bottom: 15px;
        color: #b9bbbe;
    }

    .tenant-selection-card {
        background-color: #23272a;
        padding: 15px;
        border-radius: 5px;
        margin-bottom: 15px;
    }

    .tenant-details {
        display: flex;
        align-items: center;
        margin-bottom: 10px;
    }

    .tenant-logo {
        max-width: 60px;
        height: auto;
        margin-right: 15px;
        border-radius: 5px;
        object-fit: contain;
    }

    .tenant-info p {
        margin: 0;
        color: #ffffff;
    }

    .tenant-info p strong {
        color: #00b4d8;
    }

    .tenant-actions {
        display: flex;
        justify-content: flex-end;
    }

    .kc-button {
        padding: 8px 16px;
        border: none;
        border-radius: 4px;
        cursor: pointer;
        font-size: 0.9em;
        transition: background-color 0.3s;
    }

    .kc-button-primary {
        background-color: #00b4d8;
        color: #ffffff;
    }

    .kc-button-primary:hover {
        background-color: #0097c6;
    }
</style>