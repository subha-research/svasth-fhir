package com.svasamm.fhir.interceptor;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;

@Component
@Interceptor
public class JwtAuthorizationInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthorizationInterceptor.class);

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
    public boolean validateToken(RequestDetails requestDetails) {
        String authHeader = requestDetails.getHeader("Authorization");

        // Skip validation for metadata and capability statement requests
        String requestPath = requestDetails.getRequestPath();
        if (requestPath != null && (requestPath.contains("metadata") || requestPath.contains("$meta"))) {
            return true;
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("❌ Missing or invalid Authorization header for: {}", requestPath);
            throw new AuthenticationException("Missing Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            // Simple JWT decode without verification (for basic implementation)
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new AuthenticationException("Invalid JWT format");
            }

            // Decode payload
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            logger.debug("📋 JWT Payload: {}", payload);

            // Extract claims (simple string parsing)
            String userId = extractClaim(payload, "sub");
            String rolesStr = extractClaim(payload, "roles");

            // Store in request attributes
            requestDetails.setAttribute("userId", userId != null ? userId : "unknown");

            if (rolesStr != null) {
                // Handle both array format and comma-separated format
                List<String> roles;
                if (rolesStr.startsWith("[") && rolesStr.endsWith("]")) {
                    // Array format: ["ADMIN","USER"]
                    rolesStr = rolesStr.substring(1, rolesStr.length() - 1).replace("\"", "");
                    roles = Arrays.asList(rolesStr.split(","));
                } else {
                    // Comma-separated: "ADMIN,USER"
                    roles = Arrays.asList(rolesStr.split(","));
                }
                requestDetails.setAttribute("userRoles", roles);
                logger.info("✅ User '{}' authenticated with roles: {}", userId, roles);
            } else {
                requestDetails.setAttribute("userRoles", Arrays.asList("USER")); // Default role
                logger.info("✅ User '{}' authenticated with default role", userId);
            }

        } catch (Exception e) {
            logger.error("❌ Token validation failed: {}", e.getMessage());
            throw new AuthenticationException("Invalid token: " + e.getMessage());
        }

        return true;
    }

    private String extractClaim(String payload, String claim) {
        try {
            String searchStr = "\"" + claim + "\":\"";
            int start = payload.indexOf(searchStr);
            if (start == -1) {
                // Try without quotes (for non-string values)
                searchStr = "\"" + claim + "\":";
                start = payload.indexOf(searchStr);
                if (start == -1) {
                    return null;
                }
                start += searchStr.length();
                int end = payload.indexOf(",", start);
                if (end == -1) {
                    end = payload.indexOf("}", start);
                }
                return payload.substring(start, end).trim();
            } else {
                start += searchStr.length();
                int end = payload.indexOf("\"", start);
                return payload.substring(start, end);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
