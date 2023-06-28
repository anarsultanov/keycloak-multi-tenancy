# Keycloak Multi-Tenancy
[![CodeQL](https://github.com/anarsultanov/keycloak-multi-tenancy/actions/workflows/codeql.yml/badge.svg)](https://github.com/anarsultanov/keycloak-multi-tenancy/actions/workflows/codeql.yml)

Keycloak extension for creating multi-tenant IAM for B2B SaaS applications.

## Features
- Creation of a tenant during registration (Required action)
- Invitations for users to join the tenant (API)
- Review of pending invitations (Required action)
- Selection of active tenant on login (Required action)

## Build
Clone the repository and run `mvn package` command from the project directory.
This will produce a JAR in the `target/` directory.

## Install
Copy the JAR to the Keycloak `providers/` directory, then run `bin/kc.[sh|bat] build`.

## Configure
Various combinations of providers can be used depending on the needs. We will consider only one configuration option, using all the providers presented in the extension.

Open the Keycloak admin console and select the realm you want to configure.
In your realms admin console, go to `Authentication` > `Required actions` and enable all the following actions (note that they must be placed exactly in this order):
* Review tenant invitations
* Create tenant
* Select active tenant

Now go to `Realm Settings` > `Login` and turn on `Verify email` as only users with verified email can view their invitations.
<br/>Depending on your onboarding model, you may also want to enable `User registration`. 
Each new user will be prompted to review pending invitations, if any, and/or create a new tenant if there is no invitation, or they do not accept any of them. 
If they accept the invitation, they can still create another tenant later using the API.

Users who is a member of more than one tenant will be prompted to select an active tenant when they log in.
In order to use this information in your application, you need to add it to the token claims. 
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

## API
The user who creates a tenant is automatically granted the `tenant-admin` role on that tenant, which gives them access to the tenant management API:

- [API documentation](http://sultanov.dev/keycloak-multi-tenancy/)
