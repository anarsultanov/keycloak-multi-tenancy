package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.utils.SearchQueryUtils;

public class TenantsResource extends AbstractAdminResource<TenantAdminAuth> {

    public TenantsResource(KeycloakSession session) {
        super(session);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createTenant", summary = "Create a tenant")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Created"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden")
    })
    public Response createTenant(@RequestBody(required = true) TenantRepresentation request) {

        var requiredRole = realm.getAttribute("requiredRoleForTenantCreation");
        if (requiredRole != null && !auth.hasAppRole(auth.getClient(), requiredRole)) {
            throw new ForbiddenException(String.format("Missing required role for tenant creation: %s", requiredRole));
        }

        validateAttributes(request.getAttributes());

        TenantModel model = tenantProvider.createTenant(realm, request.getName(), auth.getUser());

        if (request.getAttributes() != null) {
            request.getAttributes().forEach((key, values) -> {
                if (values != null && !values.isEmpty()) {
                    model.setAttribute(key, values);
                }
            });
        }

        TenantRepresentation representation = ModelMapper.toRepresentation(model);

        adminEvent.operation(OperationType.CREATE)
                .resourcePath(session.getContext().getUri(), representation.getId())
                .representation(representation)
                .success();

        return Response.created(session.getContext().getUri().getAbsolutePathBuilder().path(representation.getId()).build()).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listTenants", summary = "List tenants")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TenantRepresentation.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized")
    })
    public Stream<TenantRepresentation> listTenants(
            @Parameter(description = "Tenant name") @QueryParam("search") String searchQuery,
            @Parameter(description = "Tenant attribute query") @QueryParam("q") String attributeQuery,
            @Parameter(description = "Pagination offset") @QueryParam("first") Integer firstResult,
            @Parameter(description = "Maximum results size (defaults to 100)") @QueryParam("max") Integer maxResults) {
        firstResult = firstResult != null ? firstResult : 0;
        maxResults = maxResults != null ? maxResults : Constants.DEFAULT_MAX_RESULTS;

        Map<String, String> searchAttributes = attributeQuery == null
                ? Collections.emptyMap()
                : SearchQueryUtils.getFields(attributeQuery);

        Stream<TenantModel> tenantStream = tenantProvider.getTenantsStream(realm, searchQuery, searchAttributes,
                firstResult, maxResults);

        return tenantStream
                .filter(tenant -> auth.isTenantsManager() || auth.isTenantMember(tenant))
                .map(ModelMapper::toRepresentation);
    }

    @Path("{tenantId}")
    public TenantResource getTenantResource(@PathParam("tenantId") String tenantId) {
        TenantModel model = tenantProvider.getTenantById(realm, tenantId)
                .orElseThrow(() -> new NotFoundException(String.format("%s not found", tenantId)));
        if (auth.isTenantsManager() || auth.isTenantAdmin(model)) {
            return new TenantResource(this, model);
        } else {
            throw new ForbiddenException(String.format("Insufficient permission to access %s", tenantId));
        }
    }

    private void validateAttributes(Map<String, List<String>> attributes) {
        if (attributes != null) {
            attributes.forEach((key, values) -> {
                if (key == null || key.trim().isEmpty()) {
                    throw new BadRequestException("Attribute name cannot be null or empty");
                }
                if (values == null || values.isEmpty()) {
                    throw new BadRequestException("Attribute values cannot be null or empty for key: " + key);
                }
                values.forEach(value -> {
                    if (value == null || value.trim().isEmpty()) {
                        throw new BadRequestException("Attribute value cannot be null or empty for key: " + key);
                    }
                });
            });
        }
    }
}
