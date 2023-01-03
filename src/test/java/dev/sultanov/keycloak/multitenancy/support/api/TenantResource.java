package dev.sultanov.keycloak.multitenancy.support.api;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public interface TenantResource {

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    TenantRepresentation toRepresentation();

    @DELETE
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteTenant();

    @Path("invitations")
    TenantInvitationsResource invitations();

    @Path("memberships")
    TenantMembershipsResource memberships();
}
