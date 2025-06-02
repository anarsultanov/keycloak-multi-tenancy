package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Slf4j
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
            @APIResponse(responseCode = "409", description = "Conflict"),
            @APIResponse(responseCode = "500", description = "Internal Server Error")
    })
    public Response createTenant(@RequestBody(required = true) TenantRepresentation request) {
        if (ObjectUtils.isEmpty(request)) {
            log.error("Tenant representation cannot be null");
            throw new BadRequestException("Tenant representation cannot be null");
        }

        log.debug("Creating tenant with name: {}, mobileNumber: {}, countryCode: {}",
                request.getName(), request.getMobileNumber(), request.getCountryCode());

        if (isNullOrWhitespace(request.getName())) {
            log.error("Tenant name cannot be null or empty");
            throw new BadRequestException("Tenant name cannot be null or empty");
        }
        if (isNullOrWhitespace(request.getMobileNumber())) {
            log.error("Mobile number is required");
            throw new BadRequestException("Mobile number is required");
        }
        if (isNullOrWhitespace(request.getCountryCode())) {
            log.error("Country code is required");
            throw new BadRequestException("Country code is required");
        }
        if (isNullOrWhitespace(request.getStatus())) {
            log.error("Status is required");
            throw new BadRequestException("Status is required");
        }

        if (tenantProvider.getTenantByMobileNumberAndCountryCode(realm, request.getMobileNumber(), request.getCountryCode()).isPresent()) {
            log.error("Tenant with mobile number {} and country code {} already exists",
                    request.getMobileNumber(), request.getCountryCode());
            throw new WebApplicationException("Tenant with this mobile number and country code already exists", Response.Status.CONFLICT);
        }

        var requiredRole = realm.getAttribute("requiredRoleForTenantCreation");
        if (ObjectUtils.isNotEmpty(requiredRole) && !auth.hasAppRole(auth.getClient(), requiredRole)) {
            log.error("Missing required role for tenant creation: {}", requiredRole);
            throw new ForbiddenException(String.format("Missing required role for tenant creation: %s", requiredRole));
        }

        validateAttributes(request.getAttributes());
        
        TenantModel model = tenantProvider.createTenant(realm, request.getName(), request.getMobileNumber(),
                request.getCountryCode(), request.getStatus(), auth.getUser());

        if (ObjectUtils.isNotEmpty(request.getAttributes())) {
            request.getAttributes().forEach((key, values) -> {
                if (!isReservedAttribute(key) && ObjectUtils.isNotEmpty(values)) {
                    model.setAttribute(key, values);
                }
            });
        }

        TenantRepresentation response = ModelMapper.toRepresentation(model);

        adminEvent.operation(OperationType.CREATE)
                .resourcePath(session.getContext().getUri(), response.getId())
                .representation(response)
                .success();

        log.info("Tenant created successfully: {}", response.getName());
        return Response.created(session.getContext().getUri().getAbsolutePathBuilder().path(response.getId()).build())
                .entity(response)
                .build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listTenants", summary = "List tenants")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TenantRepresentation.class))),
            @APIResponse(responseCode = "400", description = "Bad Request"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "500", description = "Internal Server Error")
    })
    public Stream<TenantRepresentation> listTenants(
            @Parameter(description = "Tenant name or ID search keyword (partial match for name, exact for ID)") @QueryParam("search") String searchQuery,
            @Parameter(description = "Tenant name or ID search keyword (alternative)") @QueryParam("keyword") String keyword,
            @Parameter(description = "Tenant mobile number (exact match)") @QueryParam("mobileNumber") String mobileNumber,
            @Parameter(description = "Tenant country code (exact match, e.g., 91)") @QueryParam("countryCode") String countryCode,
            @Parameter(description = "Tenant status (exact match)") @QueryParam("status") String status,
            @Parameter(description = "Tenant attribute query (e.g., q=city:London)") @QueryParam("q") String attributeQuery,
            @Parameter(description = "Require exact name match") @QueryParam("exactMatch") Boolean exactMatch,
            @Parameter(description = "Pagination offset") @QueryParam("first") Integer firstResult,
            @Parameter(description = "Maximum results size (defaults to 100)") @QueryParam("max") Integer maxResults) {
        log.debug("Listing tenants with search: {}, keyword: {}, mobileNumber: {}, countryCode: {}, status: {}, attributeQuery: {}, exactMatch: {}",
                searchQuery, keyword, mobileNumber, countryCode, status, attributeQuery, exactMatch);

        String effectiveSearchQuery = ObjectUtils.isNotEmpty(searchQuery) ? searchQuery : keyword;
        firstResult = firstResult != null ? firstResult : 0;
        maxResults = maxResults != null ? maxResults : Constants.DEFAULT_MAX_RESULTS;

        Map<String, String> searchAttributes = new HashMap<>();
        if (ObjectUtils.isNotEmpty(attributeQuery)) {
            searchAttributes.putAll(SearchQueryUtils.getFields(attributeQuery));
        }
        if (ObjectUtils.isNotEmpty(mobileNumber)) {
            searchAttributes.put("mobileNumber", mobileNumber);
        }
        if (ObjectUtils.isNotEmpty(countryCode)) {
            searchAttributes.put("countryCode", countryCode);
        }
        if (ObjectUtils.isNotEmpty(status)) {
            searchAttributes.put("status", status);
        }
        if (ObjectUtils.isNotEmpty(exactMatch)) {
            searchAttributes.put("exactMatch", String.valueOf(exactMatch));
        }

        Stream<TenantModel> tenantStream = tenantProvider.getTenantsStream(realm, effectiveSearchQuery, searchAttributes,
                firstResult, maxResults);

        Stream<TenantRepresentation> result = tenantStream
                .filter(tenant -> auth.isTenantsManager() || auth.isTenantMember(tenant))
                .map(ModelMapper::toRepresentation);

        log.debug("Returning tenant stream for query: {}", effectiveSearchQuery);
        return result;
    }

    @Path("{tenantId}")
    public TenantResource getTenantResource(@PathParam("tenantId") String tenantId) {
        log.debug("Fetching tenant resource for ID: {}", tenantId);
        if (isNullOrWhitespace(tenantId)) {
            log.error("Tenant ID cannot be null or empty");
            throw new BadRequestException("Tenant ID cannot be null or empty");
        }

        TenantModel model = tenantProvider.getTenantById(realm, tenantId)
                .orElseThrow(() -> {
                    log.error("Tenant not found with ID: {}", tenantId);
                    return new NotFoundException(String.format("Tenant %s not found", tenantId));
                });

        if (auth.isTenantsManager() || auth.isTenantAdmin(model)) {
            log.debug("Access granted for tenant ID: {}", tenantId);
            return new TenantResource(this, model);
        } else {
            log.error("Insufficient permissions for tenant ID: {}", tenantId);
            throw new ForbiddenException(String.format("Insufficient permission to access tenant %s", tenantId));
        }
    }

    private boolean isNullOrWhitespace(String str) {
        return ObjectUtils.isEmpty(str) || str.trim().isEmpty();
    }

    private boolean isReservedAttribute(String key) {
        return "mobileNumber".equalsIgnoreCase(key) || "countryCode".equalsIgnoreCase(key) || "status".equalsIgnoreCase(key);
    }

    private void validateAttributes(Map<String, List<String>> attributes) {
        if (ObjectUtils.isNotEmpty(attributes)) {
            attributes.forEach((key, values) -> {
                if (isNullOrWhitespace(key)) {
                    log.error("Attribute name cannot be null or empty");
                    throw new BadRequestException("Attribute name cannot be null or empty");
                }
            });
        }
    }
}