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
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
public class TenantsResource extends AbstractAdminResource<TenantAdminAuth> {

    private static final Pattern MOBILE_NUMBER_PATTERN = Pattern.compile("^[+]?[0-9]{7,15}$");
    private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("^[0-9]{1,3}$");

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
        try {
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
            if (!MOBILE_NUMBER_PATTERN.matcher(request.getMobileNumber()).matches()) {
                log.error("Invalid mobile number format: {}", request.getMobileNumber());
                throw new BadRequestException("Invalid mobile number format");
            }
            if (isNullOrWhitespace(request.getCountryCode())) {
                log.error("Country code is required");
                throw new BadRequestException("Country code is required");
            }
            if (!COUNTRY_CODE_PATTERN.matcher(request.getCountryCode()).matches()) {
                log.error("Invalid country code format: {}", request.getCountryCode());
                throw new BadRequestException("Country code must be a valid numeric code (e.g., 91, 1, 23)");
            }
            if (isNullOrWhitespace(request.getStatus())) {
                log.error("Status is required");
                throw new BadRequestException("Status is required");
            }

            if (tenantProvider.getTenantByMobileNumberAndCountryCode(realm, request.getMobileNumber(), request.getCountryCode()).isPresent()) {
                log.error("Tenant with mobile number {} and country code {} already exists",
                        request.getMobileNumber(), request.getCountryCode());
                throw new jakarta.ws.rs.WebApplicationException(
                        "Tenant with this mobile number and country code already exists.",
                        Response.Status.CONFLICT);
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
        } catch (Exception e) {
            log.error("Failed to create tenant with name: {}, mobileNumber: {}, countryCode: {}", 
                    request.getName(), request.getMobileNumber(), request.getCountryCode(), e);
            throw new jakarta.ws.rs.WebApplicationException("Failed to create tenant due to server error", 
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
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
        try {
            log.debug("Listing tenants with search: {}, keyword: {}, mobileNumber: {}, countryCode: {}, status: {}, attributeQuery: {}, exactMatch: {}",
                    searchQuery, keyword, mobileNumber, countryCode, status, attributeQuery, exactMatch);

            String effectiveSearchQuery = ObjectUtils.isNotEmpty(searchQuery) ? searchQuery : keyword;
            firstResult = firstResult != null ? firstResult : 0;
            maxResults = maxResults != null ? maxResults : Constants.DEFAULT_MAX_RESULTS;

            if (ObjectUtils.isNotEmpty(mobileNumber) && !MOBILE_NUMBER_PATTERN.matcher(mobileNumber).matches()) {
                log.warn("Invalid mobile number format: {}", mobileNumber);
                throw new BadRequestException("Invalid mobile number format");
            }
            if (ObjectUtils.isNotEmpty(countryCode) && !COUNTRY_CODE_PATTERN.matcher(countryCode).matches()) {
                log.warn("Invalid country code format: {}", countryCode);
                throw new BadRequestException("Invalid country code format");
            }

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
        } catch (Exception e) {
            log.error("Failed to list tenants with search: {}, keyword: {}, mobileNumber: {}, countryCode: {}", 
                    searchQuery, keyword, mobileNumber, countryCode, e);
            throw new jakarta.ws.rs.WebApplicationException("Failed to list tenants due to server error", 
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Path("{tenantId}")
    public TenantResource getTenantResource(@PathParam("tenantId") String tenantId) {
        try {
            log.debug("Fetching tenant resource for ID: {}", tenantId);
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
        } catch (Exception e) {
            log.error("Failed to fetch tenant with ID: {}", tenantId, e);
            throw new jakarta.ws.rs.WebApplicationException("Failed to fetch tenant due to server error", 
                    Response.Status.INTERNAL_SERVER_ERROR);
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
                if (isReservedAttribute(key)) {
                    log.error("Reserved attribute used: {}", key);
                    throw new BadRequestException("Mobile number, country code, and status cannot be passed as generic attributes");
                }
                if (ObjectUtils.isEmpty(values)) {
                    log.error("Attribute values cannot be null or empty for key: {}", key);
                    throw new BadRequestException("Attribute values cannot be null or empty for key: " + key);
                }
                values.forEach(value -> {
                    if (isNullOrWhitespace(value)) {
                        log.error("Attribute value cannot be null or empty for key: {}", key);
                        throw new BadRequestException("Attribute value cannot be null or empty for key: " + key);
                    }
                });
            });
        }
    }
}