package dev.sultanov.keycloak.multitenancy.resource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import org.apache.commons.lang3.ObjectUtils;
import org.jboss.logging.Logger;
import org.keycloak.util.JsonSerialization;

public class UserServiceRestClient {

    private static final Logger log = Logger.getLogger(UserServiceRestClient.class);
    private static final String USER_SERVICE_URL = "https://dev-usms.bizzupapp.com/user-service/v1/business/updateStatus"; // Change as needed

    private final HttpClient httpClient;

    public UserServiceRestClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        log.debug("Initialized UserServiceRestClient with connect timeout: 10 seconds");
    }

    public void updateUserTenantInvitationStatuses(String userId, List<String> accepted, List<String> rejected) throws Exception {
        log.infof("Starting user service update for userId: %s, accepted: %s, rejected: %s", 
                  userId, accepted, rejected);
        
        if ((ObjectUtils.isEmpty(accepted)) && (ObjectUtils.isEmpty(rejected))) {
            log.infof("No tenant invitations to update for userId: %s, skipping request", userId);
            return; // Nothing to update, just return
        }

        List<Map<String, Object>> businessStatusList = new ArrayList<>();

        if (!ObjectUtils.isEmpty(accepted)) {
            Map<String, Object> activeStatus = new HashMap<>();
            activeStatus.put("status", "Active");
            activeStatus.put("businessId", accepted);
            businessStatusList.add(activeStatus);
            log.debugf("Added Active status for userId: %s with businessIds: %s", userId, accepted);
        }

        if (!ObjectUtils.isEmpty(rejected)) {
            Map<String, Object> rejectStatus = new HashMap<>();
            rejectStatus.put("status", "Reject");
            rejectStatus.put("businessId", rejected);
            businessStatusList.add(rejectStatus);
            log.debugf("Added Reject status for userId: %s with businessIds: %s", userId, rejected);
        }

        Map<String, Object> finalPayload = new HashMap<>();
        finalPayload.put("businessStatusList", businessStatusList);

        String json = JsonSerialization.writeValueAsString(finalPayload);
        log.infof("Prepared payload for userId: %s: %s", userId, json);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(USER_SERVICE_URL))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .header("userId", userId)
            .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
            .build();
        log.debugf("Constructed HTTP PATCH request for userId: %s, URL: %s, headers: %s", 
                   userId, USER_SERVICE_URL, request.headers().map());

        HttpResponse<String> response;
        try {
            log.infof("Sending request to user service for userId: %s", userId);
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.infof("Received response from user service for userId: %s, status: %d, body: %s", 
                      userId, response.statusCode(), response.body());
        } catch (Exception ex) {
            log.errorf(ex, "Exception while calling user service for userId: %s", userId);
            throw new RuntimeException("Unable to connect to user service: " + ex.getMessage(), ex);
        }

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.infof("User service status update succeeded for userId: %s. Response: %s", userId, response.body());
        } else {
            String errorMsg = String.format("User service update failed for userId: %s with status %d. Response: %s",
                    userId, response.statusCode(), response.body());
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
    }
}