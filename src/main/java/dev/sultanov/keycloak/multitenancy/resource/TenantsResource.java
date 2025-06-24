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
import org.keycloak.models.KeycloakSession;
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

        // Check for existing tenant with mobile number and country code
        if (tenantProvider.getTenantsStream(realm, null, Map.of(), request.getMobileNumber(), request.getCountryCode())
                .findAny()
                .isPresent()) {
            log.error("Tenant with mobile number {} and country code {} already exists",
                    request.getMobileNumber(), request.getCountryCode());
            throw new WebApplicationException("Tenant with this mobile number and country code already exists", Response.Status.CONFLICT);
        }

        validateAttributes(request.getAttributes());

        TenantModel model = tenantProvider.createTenant(
                realm,
                request.getName(),
                request.getMobileNumber(),
                request.getCountryCode(),
                request.getStatus(),
                auth.getUser()
        );

        if (ObjectUtils.isNotEmpty(request.getAttributes())) {
            request.getAttributes().forEach((key, values) -> {
                model.setAttribute(key, values);
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
            @Parameter(description = "Tenant mobile number (exact match)") @QueryParam("mobileNumber") String mobileNumber,
            @Parameter(description = "Tenant country code (exact match, e.g., 91)") @QueryParam("countryCode") String countryCode) {

        log.debug("Listing tenants with mobileNumber: {}, countryCode: {}", mobileNumber, countryCode);

        Stream<TenantModel> tenantStream = tenantProvider.getTenantsStream(
                realm,
                null,     
                Map.of(),
                mobileNumber,
                countryCode
        );

        Stream<TenantRepresentation> result = tenantStream
                .filter(tenant -> auth.isTenantsManager() || auth.isTenantMember(tenant))
                .map(ModelMapper::toRepresentation);

        log.debug("Returning tenant stream for mobileNumber: {}, countryCode: {}", mobileNumber, countryCode);
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
    
    @GET
    @Path("users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listTenantsByUserId", summary = "List tenants for a specific user ID")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TenantRepresentation.class))),
            @APIResponse(responseCode = "400", description = "Bad Request"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "404", description = "User not found"),
            @APIResponse(responseCode = "500", description = "Internal Server Error")
    })
    public Stream<TenantRepresentation> listTenantsByUserId(
            @Parameter(description = "User ID to fetch associated tenants") @PathParam("userId") String userId) {
        log.debug("Listing tenants for user ID: {}", userId);

        if (isNullOrWhitespace(userId)) {
            log.error("User ID cannot be null or empty");
            throw new BadRequestException("User ID cannot be null or empty");
        }

        // Verify user exists
        if (ObjectUtils.isEmpty(session.users().getUserById(realm, userId))) {
            log.error("User not found with ID: {}", userId);
            throw new NotFoundException(String.format("User %s not found", userId));
        }

        Stream<TenantModel> tenantStream = tenantProvider.getUserTenantsStream(realm, session.users().getUserById(realm, userId));
        Stream<TenantRepresentation> result = tenantStream.map(ModelMapper::toRepresentation);

        log.info("Successfully fetched tenants for user ID: {}", userId);
        return result;
    }

    private boolean isNullOrWhitespace(String str) {
        return ObjectUtils.isEmpty(str) || str.trim().isEmpty();
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