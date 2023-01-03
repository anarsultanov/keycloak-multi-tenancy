package dev.sultanov.keycloak.multitenancy.support.api;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantMembershipRepresentation;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public interface TenantMembershipsResource {

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    List<TenantMembershipRepresentation> listMemberships(@QueryParam("first") Integer firstResult, @QueryParam("max") Integer maxResults);

    @PATCH
    @Path("{membershipId}/roles")
    @Produces(MediaType.APPLICATION_JSON)
    Response updateRoles(@PathParam("membershipId") String membershipId, @QueryParam("roles") Set<String> roles);
    @PATCH
    @Path("{membershipId}/roles/grant")
    @Produces(MediaType.APPLICATION_JSON)
    Response grantRoles(@PathParam("membershipId") String membershipId, @QueryParam("roles") Set<String> roles);

    @PATCH
    @Path("{membershipId}/roles/revoke")
    @Produces(MediaType.APPLICATION_JSON)
    Response revokeRoles(@PathParam("membershipId") String membershipId, @QueryParam("roles") Set<String> roles);

    @DELETE
    @Path("{membershipId}")
    Response deleteMembership(@PathParam("membershipId") String membershipId);
}
