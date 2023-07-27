package dev.sultanov.keycloak.multitenancy.support.api;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public interface TenantResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    TenantRepresentation toRepresentation();

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteTenant();

    @Path("invitations")
    TenantInvitationsResource invitations();

    @Path("memberships")
    TenantMembershipsResource memberships();
}
