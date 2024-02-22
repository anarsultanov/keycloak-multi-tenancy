package dev.sultanov.keycloak.multitenancy.support.api;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

public interface TenantResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    TenantRepresentation toRepresentation();

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(value = MediaType.APPLICATION_JSON)
    Response updateTenant(TenantRepresentation request);

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteTenant();

    @Path("invitations")
    TenantInvitationsResource invitations();

    @Path("memberships")
    TenantMembershipsResource memberships();
}
