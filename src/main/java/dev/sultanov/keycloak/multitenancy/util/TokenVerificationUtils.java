package dev.sultanov.keycloak.multitenancy.util;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.TokenVerifier;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.AccessToken;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.util.Collections;

public class TokenVerificationUtils {

    /**
     * Verifies the Authorization header and retrieves the AccessToken and UserModel.
     * 
     * @param session  KeycloakSession to access realm and user data
     * @param headers  HttpHeaders containing the Authorization header
     * @return TokenVerificationResult containing the AccessToken and UserModel, or an error Response
     */
    public static TokenVerificationResult verifyToken(KeycloakSession session, HttpHeaders headers) {
        RealmModel realm = session.getContext().getRealm();

        // Extract and validate Authorization header
        String authHeader = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (StringUtils.isEmpty(authHeader) || !authHeader.startsWith("Bearer ")) {
            return TokenVerificationResult.error(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity(Collections.singletonMap("message", "Missing or invalid Authorization header"))
                            .build()
            );
        }

        // Extract token
        String tokenString = authHeader.substring("Bearer ".length());
        AccessToken token;
        try {
            token = TokenVerifier.create(tokenString, AccessToken.class).getToken();
        } catch (Exception e) {
            return TokenVerificationResult.error(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity(Collections.singletonMap("message", "Invalid token"))
                            .build()
            );
        }

        // Get user from token
        String userId = token.getSubject();
        UserModel user = session.users().getUserById(realm, userId);
        if (ObjectUtils.isEmpty(user)) {
            return TokenVerificationResult.error(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity(Collections.singletonMap("message", "User not found"))
                            .build()
            );
        }

        return TokenVerificationResult.success(token, user);
    }

    /**
     * Result class to hold the outcome of token verification.
     */
    public static class TokenVerificationResult {
        private final AccessToken token;
        private final UserModel user;
        private final Response errorResponse;

        private TokenVerificationResult(AccessToken token, UserModel user, Response errorResponse) {
            this.token = token;
            this.user = user;
            this.errorResponse = errorResponse;
        }

        public static TokenVerificationResult success(AccessToken token, UserModel user) {
            return new TokenVerificationResult(token, user, null);
        }

        public static TokenVerificationResult error(Response errorResponse) {
            return new TokenVerificationResult(null, null, errorResponse);
        }

        public boolean isSuccess() {
            return errorResponse == null;
        }

        public AccessToken getToken() {
            return token;
        }

        public UserModel getUser() {
            return user;
        }

        public Response getErrorResponse() {
            return errorResponse;
        }
    }
}