package com.svasamm.fhir.interceptor;

import java.util.Base64;

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
public class JwtValidationInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(JwtValidationInterceptor.class);

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
    public boolean validateJwtToken(RequestDetails requestDetails) {
        
        if (requestDetails == null) {
            logger.debug("RequestDetails is null, skipping validation");
            return true;
        }
        
        String requestPath = requestDetails.getRequestPath();
        
        // Skip validation for metadata endpoints
        if (shouldSkipValidation(requestPath)) {
            logger.debug("Skipping JWT validation for: {}", requestPath);
            return true;
        }
        
        String authHeader = requestDetails.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("Missing Authorization header for: {}", requestPath);
            throw new AuthenticationException("Missing Authorization header");
        }
        
        String token = authHeader.substring(7);
        
        if (!isValidJwtToken(token)) {
            logger.error("Invalid JWT token for: {}", requestPath);
            throw new AuthenticationException("Invalid JWT token");
        }
        
        logger.info("✅ JWT validation successful for: {}", requestPath);
        return true;
    }
    
    private boolean shouldSkipValidation(String requestPath) {
        return requestPath != null && (
            requestPath.contains("/metadata") ||
            requestPath.contains("/$meta") ||
            requestPath.endsWith("/fhir") ||
            requestPath.contains("CapabilityStatement")
        );
    }
    
    private boolean isValidJwtToken(String token) {
        try {
            // Basic JWT format validation
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                logger.debug("JWT token does not have 3 parts");
                return false;
            }
            
            // Try to decode payload to verify it's valid Base64
            Base64.getUrlDecoder().decode(parts[1]);
            
            logger.debug("JWT token format is valid");
            return true;
            
        } catch (IllegalArgumentException e) {
            logger.debug("JWT token Base64 decoding failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.debug("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }
}