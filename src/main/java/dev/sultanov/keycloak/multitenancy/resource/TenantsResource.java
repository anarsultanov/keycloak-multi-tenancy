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

import org.apache.commons.lang3.ObjectUtils;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Stream;

public class TenantsResource extends AbstractAdminResource<TenantAdminAuth> {

    public TenantsResource(KeycloakSession session) {
        super(session);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createTenant", summary = "Create a tenant")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = TenantRepresentation.class))),
            @APIResponse(responseCode = "400", description = "Bad Request"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "409", description = "Conflict (Tenant with same mobile number and country code already exists)")
    })
    public Response createTenant(@RequestBody(required = true) TenantRepresentation request) {
        // Validate required fields
        if (ObjectUtils.isEmpty(request.getName()) || ObjectUtils.isEmpty(request.getName().trim())) {
            throw new BadRequestException("Tenant name cannot be null or empty");
        }
        if (ObjectUtils.isEmpty(request.getMobileNumber()) || ObjectUtils.isEmpty(request.getMobileNumber().trim())) {
            throw new BadRequestException("Mobile number is required");
        }
        if (ObjectUtils.isEmpty(request.getCountryCode()) || ObjectUtils.isEmpty(request.getCountryCode().trim())) {
            throw new BadRequestException("Country code is required");
        }
        if (ObjectUtils.isEmpty(request.getStatus()) || ObjectUtils.isEmpty(request.getStatus().trim())) {
            throw new BadRequestException("Status is required");
        }

        // Check if tenant exists with mobile number and country code
        if (tenantProvider.getTenantByMobileNumberAndCountryCode(realm, request.getMobileNumber(), request.getCountryCode()).isPresent()) {
            throw new jakarta.ws.rs.WebApplicationException(
                "Tenant with this mobile number and country code already exists.", 
                Response.Status.CONFLICT
            );
        }

        // Check role-based access
        var requiredRole = realm.getAttribute("requiredRoleForTenantCreation");
        if (!ObjectUtils.isEmpty(requiredRole) && !auth.hasAppRole(auth.getClient(), requiredRole)) {
            throw new ForbiddenException(String.format("Missing required role for tenant creation: %s", requiredRole));
        }

        // Validate any additional attributes (beyond mobileNumber, countryCode, status)
        validateAttributes(request.getAttributes());

        // The createTenant method in JpaTenantProvider already sets mobileNumber, countryCode, and status.
        // There's no need to set them again on the 'model' object here, as the model is an adapter to the entity.
        TenantModel model = tenantProvider.createTenant(realm, request.getName(), request.getMobileNumber(), request.getCountryCode(), request.getStatus(), auth.getUser());

        // Set other attributes (if any, separate from mobileNumber, countryCode, status)
        if (!ObjectUtils.isEmpty(request.getAttributes())) {
            request.getAttributes().forEach((key, values) -> {
                if (!"mobileNumber".equalsIgnoreCase(key) &&
                    !"countryCode".equalsIgnoreCase(key) &&
                    !"status".equalsIgnoreCase(key) &&
                    !ObjectUtils.isEmpty(values)) {
                    model.setAttribute(key, values);
                }
            });
        }

        // Convert the updated TenantModel to a TenantRepresentation for the response
        TenantRepresentation representation = ModelMapper.toRepresentation(model);

        // Log admin event
        adminEvent.operation(OperationType.CREATE)
                .resourcePath(session.getContext().getUri(), representation.getId())
                .representation(representation)
                .success();

        return Response.created(session.getContext().getUri().getAbsolutePathBuilder().path(representation.getId()).build())
                       .entity(representation)
                       .build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listTenants", summary = "List tenants")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TenantRepresentation.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized")
    })
    public Stream<TenantRepresentation> listTenants(
            @Parameter(description = "Tenant name or ID search keyword (partial match for name, exact for ID)") @QueryParam("search") String searchQuery,
            @Parameter(description = "Tenant mobile number (exact match)") @QueryParam("mobileNumber") String mobileNumber,
            @Parameter(description = "Tenant country code (exact match)") @QueryParam("countryCode") String countryCode,
            @Parameter(description = "Tenant status (exact match)") @QueryParam("status") String status,
            @Parameter(description = "Tenant attribute query (e.g., q=city:London)") @QueryParam("q") String attributeQuery,
            @Parameter(description = "Pagination offset") @QueryParam("first") Integer firstResult,
            @Parameter(description = "Maximum results size (defaults to 100)") @QueryParam("max") Integer maxResults) {
        firstResult = !ObjectUtils.isEmpty(firstResult) ? firstResult : 0;
        maxResults = !ObjectUtils.isEmpty(maxResults) ? maxResults : Constants.DEFAULT_MAX_RESULTS;

        // Parse general attribute query
        Map<String, String> searchAttributes = ObjectUtils.isEmpty(attributeQuery)
                ? new HashMap<>()
                : new HashMap<>(SearchQueryUtils.getFields(attributeQuery));

        // Add mobileNumber, countryCode, and status to the map if provided
        if (!ObjectUtils.isEmpty(mobileNumber)) {
            searchAttributes.put("mobileNumber", mobileNumber);
        }
        if (!ObjectUtils.isEmpty(countryCode)) {
            searchAttributes.put("countryCode", countryCode);
        }
        if (!ObjectUtils.isEmpty(status)) {
            searchAttributes.put("status", status);
        }

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
    
    private boolean isNullOrWhitespace(String str) {
        return ObjectUtils.isEmpty(str) || ObjectUtils.isEmpty(str.trim());
    }

    private void validateAttributes(Map<String, List<String>> attributes) {
        if (!ObjectUtils.isEmpty(attributes)) {
            attributes.forEach((key, values) -> {
                if (ObjectUtils.isEmpty(key) || ObjectUtils.isEmpty(key.trim())) {
                    throw new BadRequestException("Attribute name cannot be null or empty");
                }
                if ("mobileNumber".equalsIgnoreCase(key) ||
                    "countryCode".equalsIgnoreCase(key) ||
                    "status".equalsIgnoreCase(key)) {
                    throw new BadRequestException("Mobile number, country code, and status cannot be passed as generic attributes.");
                }
                if (ObjectUtils.isEmpty(values)) {
                    throw new BadRequestException("Attribute values cannot be null or empty for key: " + key);
                }
                values.forEach(value -> {
                    if (ObjectUtils.isEmpty(value) || ObjectUtils.isEmpty(value.trim())) {
                        throw new BadRequestException("Attribute value cannot be null or empty for key: " + key);
                    }
                });
            });
        }
    }
}