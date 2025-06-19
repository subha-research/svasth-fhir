package com.svasamm.fhir.interceptor;

import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.svasamm.fhir.exception.Exception;
import com.svasamm.fhir.exception.Exception.ErrorType;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;

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

		// Check for missing Authorization header
		if (authHeader == null || authHeader.trim().isEmpty()) {
			logger.warn("Missing Authorization header for: {}", requestPath);
			throw Exception.unauthorized("Authorization header is required");
		}

		// Check for proper Bearer token format
		if (!authHeader.startsWith("Bearer ")) {
			logger.warn("Invalid Authorization header format for: {}", requestPath);
			throw Exception.unauthorized("Authorization header must use Bearer token format");
		}

		String token = authHeader.substring(7).trim();

		// Check for empty token
		if (token.isEmpty()) {
			logger.warn("Empty JWT token for: {}", requestPath);
			throw Exception.unauthorized("JWT token cannot be empty");
		}

		// Validate JWT token
		try {
			validateJwtTokenStructure(token);
			logger.info("✅ JWT validation successful for: {}", requestPath);
			return true;
		} catch (Exception e) {
			logger.error("JWT validation failed for: {} - {}", requestPath, e.getMessage());
			throw e; // Re-throw our custom exception
		} catch (java.lang.Exception e) {
			logger.error("Unexpected error during JWT validation for: {} - {}", requestPath, e.getMessage());
			throw Exception.unauthorized("JWT token validation failed");
		}
	}

	private boolean shouldSkipValidation(String requestPath) {
		return requestPath != null && (requestPath.contains("/metadata") ||
				requestPath.contains("/$meta") ||
				requestPath.endsWith("/fhir") ||
				requestPath.contains("CapabilityStatement"));
	}

	private void validateJwtTokenStructure(String token) {
		try {
			// Basic JWT format validation
			String[] parts = token.split("\\.");
			if (parts.length != 3) {
				throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR, "Invalid JWT token format")
						.withErrorCode("INVALID_JWT_STRUCTURE")
						.withDetail("JWT token must have exactly 3 parts (header.payload.signature)")
						.withDetail("Found " + parts.length + " parts")
						.build();
			}

			// Validate each part is not empty
			for (int i = 0; i < parts.length; i++) {
				if (parts[i] == null || parts[i].trim().isEmpty()) {
					String partName = getJwtPartName(i);
					throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR, "JWT token has empty part")
							.withErrorCode("EMPTY_JWT_PART")
							.withDetail("JWT " + partName + " cannot be empty")
							.build();
				}
			}

			// Validate header (first part)
			validateJwtPart(parts[0], "header");

			// Validate payload (second part)
			validateJwtPart(parts[1], "payload");

			// Validate signature format (third part)
			validateSignaturePart(parts[2]);

			// Validate header contains required JWT fields
			validateJwtHeader(parts[0]);

			// Validate payload contains required claims
			validateJwtPayload(parts[1]);

			logger.debug("JWT token structure validation passed");

		} catch (Exception e) {
			// Re-throw our custom exceptions
			throw e;
		} catch (IllegalArgumentException e) {
			throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR, "JWT token encoding is invalid")
					.withErrorCode("INVALID_JWT_ENCODING")
					.withDetail("Base64 decoding failed: " + e.getMessage())
					.build();
		} catch (java.lang.Exception e) {
			throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR, "JWT token validation failed")
					.withErrorCode("JWT_VALIDATION_ERROR")
					.withDetail("Unexpected error: " + e.getMessage())
					.build();
		}
	}

	private void validateJwtPart(String part, String partName) {
		try {
			// Check if it's valid Base64
			byte[] decoded = Base64.getUrlDecoder().decode(part);
			String decodedStr = new String(decoded);

			// Must be valid JSON (starts with { and ends with })
			if (!decodedStr.trim().startsWith("{") || !decodedStr.trim().endsWith("}")) {
				throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR, "JWT " + partName + " is not valid JSON")
						.withErrorCode("INVALID_JWT_JSON")
						.withDetail("JWT " + partName + " must be valid JSON format")
						.withDetail("Expected JSON object starting with '{' and ending with '}'")
						.build();
			}

		} catch (Exception e) {
			// Re-throw our custom exceptions
			throw e;
		} catch (IllegalArgumentException e) {
			throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR, "JWT " + partName + " encoding is invalid")
					.withErrorCode("INVALID_BASE64_ENCODING")
					.withDetail("JWT " + partName + " is not valid Base64: " + e.getMessage())
					.build();
		} catch (java.lang.Exception e) {
			throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR, "JWT " + partName + " validation failed")
					.withErrorCode("JWT_PART_VALIDATION_ERROR")
					.withDetail("Unexpected error validating " + partName + ": " + e.getMessage())
					.build();
		}
	}

	private void validateSignaturePart(String signature) {
		// Signature should not be just numbers or simple text
		if (signature.matches("^[0-9.]+$")) {
			throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR, "JWT signature format is invalid")
					.withErrorCode("INVALID_SIGNATURE_FORMAT")
					.withDetail("JWT signature cannot be just numbers")
					.withDetail(
							"Provided signature: " + signature.substring(0, Math.min(10, signature.length())) + "...")
					.build();
		}

		// Signature should have minimum length
		if (signature.length() < 10) {
			throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR, "JWT signature is too short")
					.withErrorCode("SHORT_SIGNATURE")
					.withDetail("JWT signature must be at least 10 characters")
					.withDetail("Current length: " + signature.length())
					.build();
		}
	}

	private void validateJwtHeader(String headerPart) {
		try {
			String headerJson = new String(Base64.getUrlDecoder().decode(headerPart));

			// Must contain "alg" field
			if (!headerJson.contains("\"alg\"")) {
				throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR,
						"JWT header is missing required algorithm field")
						.withErrorCode("MISSING_ALG_FIELD")
						.withDetail("JWT header must contain 'alg' field")
						.withDetail("Valid algorithms: HS256, RS256, etc.")
						.build();
			}

			// Must contain "typ" field with value "JWT"
			if (!headerJson.contains("\"typ\"") || !headerJson.contains("\"JWT\"")) {
				throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR,
						"JWT header is missing or has invalid type field")
						.withErrorCode("MISSING_OR_INVALID_TYP_FIELD")
						.withDetail("JWT header must contain 'typ' field with value 'JWT'")
						.build();
			}

		} catch (Exception e) {
			// Re-throw our custom exceptions
			throw e;
		} catch (java.lang.Exception e) {
			throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR, "JWT header validation failed")
					.withErrorCode("HEADER_VALIDATION_ERROR")
					.withDetail("Error parsing JWT header: " + e.getMessage())
					.build();
		}
	}

	private void validateJwtPayload(String payloadPart) {
		try {
			String payloadJson = new String(Base64.getUrlDecoder().decode(payloadPart));

			// Must contain at least "sub" (subject) claim
			if (!payloadJson.contains("\"sub\"")) {
				throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR,
						"JWT payload is missing required subject claim")
						.withErrorCode("MISSING_SUB_CLAIM")
						.withDetail("JWT payload must contain 'sub' (subject) claim")
						.withDetail("The 'sub' claim identifies the principal that is the subject of the JWT")
						.build();
			}

			// Should contain "iat" (issued at) or "exp" (expiration)
			if (!payloadJson.contains("\"iat\"") && !payloadJson.contains("\"exp\"")) {
				throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR,
						"JWT payload is missing time-related claims")
						.withErrorCode("MISSING_TIME_CLAIMS")
						.withDetail("JWT payload must contain either 'iat' (issued at) or 'exp' (expiration) claim")
						.withDetail("Time claims are required for token validity verification")
						.build();
			}

		} catch (Exception e) {
			// Re-throw our custom exceptions
			throw e;
		} catch (java.lang.Exception e) {
			throw new Exception.Builder(ErrorType.AUTHENTICATION_ERROR, "JWT payload validation failed")
					.withErrorCode("PAYLOAD_VALIDATION_ERROR")
					.withDetail("Error parsing JWT payload: " + e.getMessage())
					.build();
		}
	}

	private String getJwtPartName(int index) {
		switch (index) {
			case 0:
				return "header";
			case 1:
				return "payload";
			case 2:
				return "signature";
			default:
				return "part " + index;
		}
	}
}