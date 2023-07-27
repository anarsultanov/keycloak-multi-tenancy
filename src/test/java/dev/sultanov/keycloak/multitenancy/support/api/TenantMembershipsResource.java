package dev.sultanov.keycloak.multitenancy.support.api;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantMembershipRepresentation;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

public interface TenantMembershipsResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<TenantMembershipRepresentation> listMemberships(
            @QueryParam("search") String searchQuery,
            @QueryParam("first") Integer firstResult,
            @QueryParam("max") Integer maxResults);

    @PATCH
    @Path("{membershipId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response update(@PathParam("membershipId") String membershipId, TenantMembershipRepresentation request);

    @DELETE
    @Path("{membershipId}")
    Response revokeMembership(@PathParam("membershipId") String membershipId);
}
