package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.dto.BusinessStatus;
import dev.sultanov.keycloak.multitenancy.dto.BusinessStatusEntry;
import dev.sultanov.keycloak.multitenancy.dto.BusinessStatusRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.jboss.logging.Logger;
import org.keycloak.util.JsonSerialization;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class UserServiceRestClient {

    private static final Logger log = Logger.getLogger(UserServiceRestClient.class);
    private static final String USER_SERVICE_URL = "https://dev-usms.bizzupapp.com/user-service/v1/business/updateStatus";

    private final HttpClient httpClient;

    public UserServiceRestClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        log.debug("Initialized UserServiceRestClient with connect timeout: 10 seconds");
    }

    public void updateUserTenantInvitationStatuses(String userId, List<String> accepted, List<String> rejected) {
        log.infof("Starting user service update for userId: %s, accepted: %s, rejected: %s", 
                  userId, accepted, rejected);

        if (ObjectUtils.isEmpty(accepted) && ObjectUtils.isEmpty(rejected)) {
            log.infof("No tenant invitations to update for userId: %s, skipping request", userId);
            return;
        }

        List<BusinessStatusEntry> businessStatusList = new ArrayList<>();

        if (!ObjectUtils.isEmpty(accepted)) {
            businessStatusList.add(new BusinessStatusEntry(BusinessStatus.ACTIVE.name(), accepted));
            log.debugf("Added Active status for userId: %s with businessIds: %s", userId, accepted);
        }

        if (!ObjectUtils.isEmpty(rejected)) {
            businessStatusList.add(new BusinessStatusEntry(BusinessStatus.INACTIVE.name(), rejected));
            log.debugf("Added Reject status for userId: %s with businessIds: %s", userId, rejected);
        }

        BusinessStatusRequest finalPayload = new BusinessStatusRequest(businessStatusList);

        try {
            String json = JsonSerialization.writeValueAsString(finalPayload);
            log.infof("Prepared payload for userId: %s: %s", userId, json);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(USER_SERVICE_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("userId", userId)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();

            log.debugf("Constructed HTTP PATCH request for userId: %s, URL: %s", 
                       userId, USER_SERVICE_URL);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.infof("Received response from user service for userId: %s, status: %d, body: %s", 
                      userId, response.statusCode(), response.body());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.infof("User service status update succeeded for userId: %s. Response: %s", userId, response.body());
            } else {
                String errorMsg = String.format("User service update failed for userId: %s with status %d. Response: %s",
                        userId, response.statusCode(), response.body());
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

        } catch (Exception ex) {
            log.errorf(ex, "Exception while processing user service update for userId: %s", userId);
            throw new RuntimeException("Error during user service status update: " + ex.getMessage(), ex);
        }
    }
}
