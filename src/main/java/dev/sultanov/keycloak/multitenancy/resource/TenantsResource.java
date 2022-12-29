package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import java.util.Optional;
import java.util.stream.Stream;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.Constants;
import org.keycloak.models.RealmModel;

public class TenantsResource extends AbstractAdminResource<TenantAdminAuth> {

    public TenantsResource(RealmModel realm) {
        super(realm);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createTenant(TenantRepresentation request) {

        TenantModel model = tenantProvider.createTenant(realm, request.getName(), auth.getUser());
        TenantRepresentation representation = ModelMapper.toRepresentation(model);

        adminEvent.operation(OperationType.CREATE)
                .resourcePath(session.getContext().getUri(), representation.getId())
                .representation(representation)
                .success();

        return Response.created(session.getContext().getUri().getAbsolutePathBuilder().path(representation.getId()).build()).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Stream<TenantRepresentation> listTenants(
            @QueryParam("search") String searchQuery,
            @QueryParam("first") Integer firstResult,
            @QueryParam("max") Integer maxResults) {
        Optional<String> search = Optional.ofNullable(searchQuery);
        firstResult = firstResult != null ? firstResult : 0;
        maxResults = maxResults != null ? maxResults : Constants.DEFAULT_MAX_RESULTS;
        return tenantProvider.getTenantsStream(realm)
                .filter(tenant -> auth.isTenantMember(tenant))
                .filter(tenant -> search.isEmpty() || tenant.getName().contains(search.get()))
                .skip(firstResult)
                .limit(maxResults)
                .map(ModelMapper::toRepresentation);
    }

    @Path("{tenantId}")
    public TenantResource getTenantResource(@PathParam("tenantId") String tenantId) {
        TenantModel model = tenantProvider.getTenantById(realm, tenantId)
                .orElseThrow(() -> new NotFoundException(String.format("%s not found", tenantId)));
        if (!auth.isTenantAdmin(model)) {
            throw new NotAuthorizedException(String.format("Insufficient permission to access %s", tenantId));
        } else {
            TenantResource resource = new TenantResource(realm, model);
            ResteasyProviderFactory.getInstance().injectProperties(resource);
            resource.setup();
            return resource;
        }
    }
}
