package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;

import org.jboss.logging.Logger;
import org.keycloak.TokenVerifier;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.AccessToken;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TenantController {

    private static final Logger log = Logger.getLogger(TenantController.class);
    private final KeycloakSession session;

    public TenantController(KeycloakSession session) {
        this.session = session;
    }

    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    public String getUserInfo() {
        return "{\"status\": \"Tenant controller working!\"}";
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMyTenants(@Context HttpHeaders headers) {
        RealmModel realm = session.getContext().getRealm();

        String authHeader = headers.getRequestHeader("Authorization") != null
                ? headers.getRequestHeader("Authorization").get(0)
                : null;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Missing or invalid Authorization header").build();
        }

        String tokenString = authHeader.substring("Bearer ".length());

        AccessToken token;
        try {
            token = TokenVerifier.create(tokenString, AccessToken.class).getToken();
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Invalid token").build();
        }

        String userId = token.getSubject(); // Globally unique Keycloak user ID
        UserModel user = session.users().getUserById(realm, userId);

        log.debug("User ID from token: " + userId);
        log.debug("Realm: " + realm.getName());

        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("User not found").build();
        }

        TenantProvider tenantProvider = session.getProvider(TenantProvider.class);
        Stream<TenantModel> allTenants = tenantProvider.getAllTenantsStream(); // You need to have this method in TenantProvider

        List<Map<String, Object>> tenants = allTenants
                .map(tenant -> {
                    Optional<TenantMembershipModel> membershipOpt = tenant.getMembershipByUser(user);
                    if (membershipOpt.isPresent()) {
                        Map<String, Object> tenantData = new HashMap<>();
                        tenantData.put("id", tenant.getId());
                        tenantData.put("name", tenant.getName());
                        tenantData.put("realm", tenant.getRealm().getName());

                        Map<String, List<String>> attributes = new HashMap<>();
                        tenant.getAttributes().forEach((k, v) -> attributes.put(k, new ArrayList<>(v)));
                        tenantData.put("attributes", attributes);

                        return tenantData;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return Response.ok(tenants).build();
    }
}
