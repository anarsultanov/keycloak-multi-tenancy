# Keycloak Multi-Tenancy

[![CodeQL](https://github.com/anarsultanov/keycloak-multi-tenancy/actions/workflows/codeql.yml/badge.svg)](https://github.com/anarsultanov/keycloak-multi-tenancy/actions/workflows/codeql.yml)

Keycloak extension for creating multi-tenant IAM for B2B SaaS applications.

## Table of Contents

- [Features](#features)
- [Compatibility](#compatibility)
- [Installation](#installation)
- [Configuration](#configuration)
    - [Tenant Creation and Management](#tenant-creation-and-management)
    - [Token claims](#token-claims)
    - [IDP and SSO Integration](#idp-and-sso-integration)
- [API](#api)

## License

This project is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0). You can find the full text of the license in the [LICENSE](LICENSE) file.

## Features

- Creation of a tenant during registration (Required action)
- Invitations for users to join the tenant (API)
- Review of pending invitations (Required action)
- Selection of active tenant on login (Required action)

### IDP Integration

- Tenant-specific IDP configuration (API)
- Automatic tenant membership creation upon login using a tenant-specific IDP (Authenticator)
- Alternative login method that redirects to an IDP using an alias (Authenticator)

## Compatibility

This extension aims to support the most recent version of Keycloak.
The major version of the extension is tested and designed to work with the corresponding major version of Keycloak.

## Installation

1. Download the latest prebuilt JAR from the [Releases](https://github.com/anarsultanov/keycloak-multi-tenancy/releases) section of this repository.
2. Or, generate a JAR by cloning the repository and running `mvn package` in the project directory. You'll find the JAR in the `target/` directory.
3. Copy the JAR to the Keycloak `providers/` directory.
4. Run `bin/kc.[sh|bat] build` to complete the installation.

## Configuration

### Tenant Creation and Management

Various combinations of providers can be used depending on the needs. We will consider only one configuration option, using all the providers presented in the
extension.

Open the Keycloak admin console and select the realm you want to configure.
In your realms admin console, go to `Authentication` > `Required actions` and enable all the following actions (note that they must be placed exactly in this
order):

* Review tenant invitations
* Create tenant
* Select active tenant

Now go to `Realm Settings` > `Login` and turn on `Verify email` as only users with verified email can view their invitations.
<br/>Depending on your onboarding model, you may also want to enable `User registration`.

Each new user will be prompted to review pending invitations, if any, and/or create a new tenant if there is no invitation, or they do not accept any of them.
If they accept the invitation, they can still create another tenant later using the [API](#api).
Users who is a member of more than one tenant will be prompted to select an active tenant when they log in.

### Token Claims

In order to use information about tenants in your application, you need to add it to the token claims.
To do this, go to `Clients`, select a client you are using, open `Client scopes` tab and click on `dedicated scope` for this client.
Here click `Add mapper` > `By configuration`, select `Active tenant`, configure and save.
Now information about the selected tenant will be added to token in the following format:

```
"active_tenant": {
    "tenant_id": "e2b004fd-04c3-4b7a-acd0-4d997eb5cefa",
    "tenant_name": "Wonka Industries",
    "roles": [
      "tenant-admin"
    ]
  }
```

In the same way, you can set up `All tenants` mapper that will add to the token claims all tenants that the user is a member of.

### IDP and SSO Integration

In a multi-tenant application, it's often necessary for tenants to use their own Identity Provider (IDP). 
While Keycloak supports identity brokering, it may not be fully compatible with the multi-tenant model introduced by this extension.

One of the issues arises from the lack of a connection between tenants and their respective IDPs. 
This prevents the onboarding of users during their initial login using the IDP, since if users haven't been pre-created and added to the required tenant, 
the created users will be tenantless and asked to create a new tenant. 
To address this issue, this extension introduces the concept of `tenant-specific IDPs` and an additional authenticator that facilitates the creation of required memberships.

To configure an IDP as tenant-specific, tenants' IDs should be added to the `multi-tenancy.tenants` configuration attribute of the IDP as a **comma-separated list**. 
This can be achieved using the standard [Keycloak REST API](https://www.keycloak.org/docs-api/23.0.1/rest-api/index.html#_identity_providers).

> **_Note_**
> - _With tenant-specific IDP configuration, the IDP limits access to only the tenants listed in the configuration. 
> If a user logs in with the IDP but isn't a member of any of these specified tenants, and automatic membership creation isn't configured, an error will occur._
> - _IDPs that lack the `multi-tenancy.tenants` configuration attribute are considered public. 
> These public IDPs grant access to any tenants for users who are members of those tenants. This ensures compatibility with existing setups and doesn't disrupt previous configurations._

To automatically add users as members of all the configured tenants during their initial login, the `Create tenant membership` authenticator should be added to the IDP's `first login flow`. 
Alternatively, this authenticator can be added to the `post-login flow`, allowing memberships to be created even for tenants added to the IDP after the user has already been onboarded.
Any memberships created by this authenticator will automatically have the default `tenant-user` role assigned to them.

In order to enhance privacy and avoid listing all tenant-specific IDPs on the login page, a custom `Login with SSO` authenticator has been introduced.
It can be added as an alternative to the password and other authentication methods in the `browser flow`.
This will either include a `Try Another Way` link on the login page or, if it already exists, add an additional `Single Sign-on (SSO)` option to the available login methods.
If the user selects `Single Sign-on (SSO)`, they will be prompted to enter an IDP alias and then redirected to the corresponding IDP login page if a match is found.

If you'd like to include a direct link to `Single Sign-On (SSO)` on the login page, you can achieve this by modifying the login page template and adding the following code:
```html

<script type="text/javascript">
    function loginWithSso() {
        document.getElementById('authexec-hidden-input').value = "${authenticationSelection.authExecId}";
        document.getElementById('kc-select-credential-form').submit();
    }
</script>

<a tabindex="5" class="${properties.kcLinkClass!}" href="#" id="login-with-sso" onclick="loginWithSso()">Sign in via SSO</a>

<form id="kc-select-credential-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
    <input type="hidden" id="authexec-hidden-input" name="authenticationExecution"/>
</form>
```

## API

The user who creates a tenant is automatically granted the `tenant-admin` role on that tenant, which gives them access to the tenant management API:

- [API documentation](http://sultanov.dev/keycloak-multi-tenancy/)
