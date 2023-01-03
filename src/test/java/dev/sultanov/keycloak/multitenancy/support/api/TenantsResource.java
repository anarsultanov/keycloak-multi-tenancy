package dev.sultanov.keycloak.multitenancy.support.api;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/tenants")
public interface TenantsResource {

    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response createTenant(TenantRepresentation tenantRequest);

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    List<TenantRepresentation> listTenants(
            @QueryParam("search") String searchQuery,
            @QueryParam("first") Integer firstResult,
            @QueryParam("max") Integer maxResults);

    @Path("{tenantId}")
    TenantResource getTenantResource(@PathParam("tenantId") String tenantId);
}
