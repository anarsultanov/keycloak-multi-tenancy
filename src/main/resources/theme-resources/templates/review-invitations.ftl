<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true displayInfo=true; section>
  <#if section == "header">
    ${kcSanitize(msg("reviewInvitationsHeader")!"Review Invitations")?no_esc}
  <#elseif section == "form">
    <#-- Define default value for tenantId if it doesn't exist -->
    <#assign tenantId = tenantId!"" />
    
    <div id="kc-form" class="tenant-invitations-container">
      <div id="kc-form-wrapper" class="tenant-invitations-wrapper">
        <p>${msg("reviewInvitationsInfo")!"Please review your tenant invitations."}</p>

        <#if messages?has_content>
          <div class="${properties.kcAlertClass!} ${properties.kcAlertErrorClass!}">
            <span>${kcSanitize(messages.error!"An error occurred.")?no_esc}</span>
          </div>
        </#if>

        <#if data.tenants?has_content>
          <#list data.tenants as tenant>
            <div class="tenant-invitation-card" data-tenant-id="${tenant.id?js_string}">
              <div class="tenant-details">
                <#if tenant.logoUrl?? && tenant.logoUrl?length gt 0>
                  <img src="${tenant.logoUrl}" alt="${kcSanitize(tenant.name!"Tenant")} Logo" class="tenant-logo" />
                <#else>
                  <img src="${url.resourcesPath}/img/default-logo.png" alt="Default Logo" class="tenant-logo" />
                </#if>
                <div class="tenant-info">
                  <p><strong>${kcSanitize(tenant.name!"Unknown Tenant")}</strong> has invited you to join their tenant.</p>
                  <#if tenant.roles?has_content>
                    <p>Roles: ${kcSanitize(tenant.roles?join(", "))}</p>
                  <#else>
                    <p>Roles: None</p>
                  </#if>
                  <div class="invitation-status">
                    <span class="status-badge status-pending" data-status="pending">Pending</span>
                  </div>
                </div>
              </div>
              <div class="tenant-actions">
                <button
                  type="button"
                  class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} accept-button"
                  onclick="handleTenantAction('${tenant.id?js_string}', 'accept')"
                >
                  Accept
                </button>
                <button
      type="button"
      id="reject-btn-${tenant.id}"
      class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} reject-button"
      onclick="handleTenantAction('${tenant.id?js_string}', 'reject')"
    >
                  Reject
                </button>
              </div>
            </div>
          </#list>

          <form id="proceed-invitations-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <input type="hidden" name="action" value="proceed" />
            <input type="hidden" name="acceptedTenants" id="acceptedTenants" value="" />
            <input type="hidden" name="rejectedTenants" id="rejectedTenants" value="" />
            <div class="tenant-actions proceed-form">
              <button
                type="submit"
                class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} proceed-button"
                disabled
              >
                Proceed
              </button>
            </div>
          </form>
        <#else>
          <div class="${properties.kcFormGroupClass!}">
            <p>No pending invitations available.</p>
          </div>
        </#if>
      </div>
    </div>

    <style>
      .tenant-invitations-container {
        background-color: #2c2f33;
        padding: 20px;
        border-radius: 8px;
        color: #ffffff;
        max-width: 600px;
        margin: 0 auto;
        box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
      }

      .tenant-invitations-wrapper p {
        font-size: 0.9em;
        margin-bottom: 15px;
        color: #b9bbbe;
      }

      .tenant-invitation-card {
        background-color: #23272a;
        padding: 15px;
        border-radius: 5px;
        margin-bottom: 15px;
      }

	.reject-button {
	    background-color: #f04747 !important; /* red */
	    color: white !important;
	    border: none;
	    cursor: pointer;
	  }
	
	  .reject-button[disabled] {
	    background-color: #444548 !important; /* grey when disabled */
	    color: #aaabac !important;
	    cursor: not-allowed;
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
        gap: 10px;
        justify-content: flex-end;
      }

      .invitation-status {
        margin-top: 5px;
      }

      .status-badge {
        display: inline-block;
        padding: 3px 8px;
        border-radius: 3px;
        font-size: 0.8em;
        font-weight: bold;
      }

      .status-accepted {
        background-color: #43b581;
        color: #ffffff;
      }

      .status-rejected {
        background-color: #f04747;
        color: #ffffff;
      }

      .status-pending {
        background-color: #747f8d;
        color: #ffffff;
      }

      .proceed-form {
        text-align: center;
        margin-top: 20px;
      }

      .proceed-button {
        min-width: 150px;
      }

      .proceed-button:disabled {
        background-color: #4a4a4a;
        cursor: not-allowed;
      }

      @media (max-width: 480px) {
        .tenant-invitations-container {
          padding: 15px;
          max-width: 100%;
        }

        .tenant-details {
          flex-direction: column;
          align-items: flex-start;
        }

        .tenant-logo {
          margin-bottom: 10px;
        }

        .tenant-actions {
          flex-direction: column;
          gap: 5px;
        }

        button {
          width: 100%;
        }
      }
    </style>

<script>
  var tenantStates = {};

  document.addEventListener("DOMContentLoaded", function () {
    console.log("DOM loaded, initializing tenant invitations");

    try {
      var savedStates = sessionStorage.getItem('tenantStates');
      if (savedStates) {
        tenantStates = JSON.parse(savedStates);
        console.log("Loaded tenant states:", tenantStates);
        for (var tenantId in tenantStates) {
          console.log("Restoring UI for tenantId:", tenantId, "action:", tenantStates[tenantId]);
          updateTenantCardUI(tenantId, tenantStates[tenantId]);
        }
      } else {
        console.log("No tenant states in sessionStorage");
      }
    } catch (e) {
      console.error("Error parsing tenant states:", e);
      sessionStorage.removeItem('tenantStates');
      tenantStates = {};
    }

    updateProceedButtonState();
  });

  function updateTenantCardUI(tenantId, action) {
    console.log("Updating UI for tenantId:", tenantId, "action:", action);

    var card = document.querySelector('.tenant-invitation-card[data-tenant-id="' + tenantId + '"]');
    if (!card) {
      console.error("Card not found for tenantId:", tenantId);
      return;
    }

    var statusBadge = card.querySelector('.status-badge');
    var acceptButton = card.querySelector('.accept-button');
    var rejectButton = card.querySelector('.reject-button');

    if (!statusBadge || !acceptButton || !rejectButton) {
      console.error("Elements not found for tenantId:", tenantId,
        { statusBadge: !!statusBadge, acceptButton: !!acceptButton, rejectButton: !!rejectButton });
      return;
    }

    console.log("Before update - Status:", statusBadge.textContent,
      "Accept disabled:", acceptButton.disabled,
      "Reject disabled:", rejectButton.disabled);

    if (action === 'accept') {
      statusBadge.textContent = 'Accepted';
      statusBadge.className = 'status-badge status-accepted';
      statusBadge.dataset.status = 'accepted';
      acceptButton.disabled = true;
      rejectButton.disabled = false;
    } else if (action === 'reject') {
      statusBadge.textContent = 'Rejected';
      statusBadge.className = 'status-badge status-rejected';
      statusBadge.dataset.status = 'rejected';
      acceptButton.disabled = false;
      rejectButton.disabled = true;
    } else {
      statusBadge.textContent = 'Pending';
      statusBadge.className = 'status-badge status-pending';
      statusBadge.dataset.status = 'pending';
      acceptButton.disabled = false;
      rejectButton.disabled = false;
    }

    console.log("After update - Status:", statusBadge.textContent,
      "Accept disabled:", acceptButton.disabled,
      "Reject disabled:", rejectButton.disabled);
  }

  function handleTenantAction(tenantId, action) {
    console.log("Handling action:", action, "for tenantId:", tenantId);

    tenantStates[tenantId] = action;
    console.log("Updated tenantStates:", tenantStates);

    try {
      sessionStorage.setItem('tenantStates', JSON.stringify(tenantStates));
      console.log("Saved to sessionStorage:", tenantStates);
    } catch (e) {
      console.error("Error saving to sessionStorage:", e);
    }

    updateTenantCardUI(tenantId, action);
    updateProceedButtonState();
  }

  // Proceed button is always enabled
  function updateProceedButtonState() {
    console.log("Proceed button will always be enabled");

    var proceedBtn = document.querySelector('.proceed-button');
    if (!proceedBtn) {
      console.error("Proceed button not found");
      return;
    }

    proceedBtn.disabled = false;
    console.log("Proceed button disabled:", proceedBtn.disabled);
  }

  document.addEventListener("DOMContentLoaded", function () {
    var form = document.getElementById('proceed-invitations-form');
    if (form) {
      form.addEventListener('submit', function (e) {
        console.log("Form submitted");

        var accepted = [];
        var rejected = [];

        for (var tenantId in tenantStates) {
          if (tenantStates[tenantId] === 'accept') {
            accepted.push(tenantId);
          } else if (tenantStates[tenantId] === 'reject') {
            rejected.push(tenantId);
          }
        }

        var acceptedInput = document.getElementById('acceptedTenants');
        var rejectedInput = document.getElementById('rejectedTenants');
        if (acceptedInput && rejectedInput) {
          acceptedInput.value = accepted.join(',');
          rejectedInput.value = rejected.join(',');
          console.log("Form fields - acceptedTenants:", accepted.join(','), "rejectedTenants:", rejected.join(','));
        } else {
          console.error("Form inputs not found");
        }

        try {
          sessionStorage.removeItem('tenantStates');
          console.log("Cleared sessionStorage");
        } catch (e) {
          console.error("Error clearing sessionStorage:", e);
        }
      });
    } else {
      console.error("Form not found");
    }
  });
</script>


  </#if>
</@layout.registrationLayout>