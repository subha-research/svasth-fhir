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
            logger.debug("JWT token does not have 3 parts, got: {}", parts.length);
            return false;
        }
        
        // Validate each part is not empty
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null || parts[i].trim().isEmpty()) {
                logger.debug("JWT token part {} is empty", i);
                return false;
            }
        }
        
        // Validate header (first part)
        if (!isValidJwtPart(parts[0], "header")) {
            return false;
        }
        
        // Validate payload (second part) 
        if (!isValidJwtPart(parts[1], "payload")) {
            return false;
        }
        
        // Validate signature format (third part)
        if (!isValidSignaturePart(parts[2])) {
            return false;
        }
        
        // Validate header contains required JWT fields
        if (!validateJwtHeader(parts[0])) {
            return false;
        }
        
        // Validate payload contains required claims
        if (!validateJwtPayload(parts[1])) {
            return false;
        }
        
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

private boolean isValidJwtPart(String part, String partName) {
    try {
        // Check if it's valid Base64
        byte[] decoded = Base64.getUrlDecoder().decode(part);
        String decodedStr = new String(decoded);
        
        // Must be valid JSON (starts with { and ends with })
        if (!decodedStr.trim().startsWith("{") || !decodedStr.trim().endsWith("}")) {
            logger.debug("JWT {} is not valid JSON format", partName);
            return false;
        }
        
        return true;
    } catch (IllegalArgumentException e) {
        logger.debug("JWT {} is not valid Base64: {}", partName, e.getMessage());
        return false;
    } catch (Exception e) {
        logger.debug("JWT {} validation failed: {}", partName, e.getMessage());
        return false;
    }
}

private boolean isValidSignaturePart(String signature) {
    // Signature should not be just numbers or simple text
    if (signature.matches("^[0-9.]+$")) {
        logger.debug("JWT signature appears to be just numbers: {}", signature);
        return false;
    }
    
    // Signature should have minimum length
    if (signature.length() < 10) {
        logger.debug("JWT signature too short: {}", signature.length());
        return false;
    }
    
    return true;
}

private boolean validateJwtHeader(String headerPart) {
    try {
        String headerJson = new String(Base64.getUrlDecoder().decode(headerPart));
        
        // Must contain "alg" field
        if (!headerJson.contains("\"alg\"")) {
            logger.debug("JWT header missing 'alg' field");
            return false;
        }
        
        // Must contain "typ" field with value "JWT"
        if (!headerJson.contains("\"typ\"") || !headerJson.contains("\"JWT\"")) {
            logger.debug("JWT header missing 'typ' field or not JWT type");
            return false;
        }
        
        return true;
    } catch (Exception e) {
        logger.debug("JWT header validation failed: {}", e.getMessage());
        return false;
    }
}

	 private boolean validateJwtPayload(String payloadPart) {
	     try {
	         String payloadJson = new String(Base64.getUrlDecoder().decode(payloadPart));
		 
	         // Must contain at least "sub" (subject) claim
	         if (!payloadJson.contains("\"sub\"")) {
	             logger.debug("JWT payload missing 'sub' claim");
	             return false;
	         }
		   
	         // Should contain "iat" (issued at) or "exp" (expiration)
	         if (!payloadJson.contains("\"iat\"") && !payloadJson.contains("\"exp\"")) {
	             logger.debug("JWT payload missing time claims (iat/exp)");
	             return false;
	         }
		   
	         return true;
	     } catch (Exception e) {
	         logger.debug("JWT payload validation failed: {}", e.getMessage());
	         return false;
	     }
	 }
}