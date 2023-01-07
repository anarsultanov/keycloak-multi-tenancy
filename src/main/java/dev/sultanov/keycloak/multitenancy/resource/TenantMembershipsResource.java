package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantMembershipRepresentation;
import java.util.Optional;
import java.util.stream.Stream;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.PATCH;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.Constants;
import org.keycloak.models.RealmModel;

public class TenantMembershipsResource extends AbstractAdminResource<TenantAdminAuth> {

    private final TenantModel tenant;

    public TenantMembershipsResource(RealmModel realm, TenantModel tenant) {
        super(realm);
        this.tenant = tenant;
    }

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Stream<TenantMembershipRepresentation> listMemberships(
            @QueryParam("search") String searchQuery,
            @QueryParam("first") Integer firstResult,
            @QueryParam("max") Integer maxResults) {
        Optional<String> search = Optional.ofNullable(searchQuery);
        firstResult = firstResult != null ? firstResult : 0;
        maxResults = maxResults != null ? maxResults : Constants.DEFAULT_MAX_RESULTS;
        return tenant.getMembershipsStream()
                .filter(m -> search.isEmpty() || m.getUser().getEmail().contains(search.get()))
                .skip(firstResult)
                .limit(maxResults)
                .map(ModelMapper::toRepresentation);
    }

    @PATCH
    @Path("{membershipId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("membershipId") String membershipId, TenantMembershipRepresentation request) {
        var optionalMembership = tenant.getMembershipById(membershipId);
        if (optionalMembership.isEmpty()) {
            throw new NotFoundException("Membership not found");
        }

        optionalMembership.get().updateRoles(request.getRoles());
        adminEvent.operation(OperationType.UPDATE)
                .resourcePath(session.getContext().getUri())
                .representation(ModelMapper.toRepresentation(optionalMembership.get()))
                .success();

        return Response.noContent().build();
    }

    @DELETE
    @Path("{membershipId}")
    public Response deleteMembership(@PathParam("membershipId") String membershipId) {
        var revoked = tenant.revokeMembership(membershipId);
        if (revoked) {
            adminEvent.operation(OperationType.DELETE)
                    .resourcePath(session.getContext().getUri())
                    .success();
            return Response.status(204).build();
        } else {
            throw new NotFoundException(String.format("No membership with id %s", membershipId));
        }
    }
}
