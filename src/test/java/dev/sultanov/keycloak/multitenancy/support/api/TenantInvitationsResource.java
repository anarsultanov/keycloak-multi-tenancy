package dev.sultanov.keycloak.multitenancy.support.api;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public interface TenantInvitationsResource {

    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response createInvitation(TenantInvitationRepresentation request);

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    List<TenantInvitationRepresentation> listInvitations(
            @QueryParam("search") String searchQuery,
            @QueryParam("first") Integer firstResult,
            @QueryParam("max") Integer maxResults);

    @DELETE
    @Path("{invitationId}")
    Response removeInvitation(@PathParam("invitationId") String invitationId);

}
