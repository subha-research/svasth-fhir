package com.svasamm.fhir.exception;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.OperationOutcome;

import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;

/**
 * Generic FHIR Exception that can be used throughout the project
 * Supports multiple error types, custom messages, and FHIR-compliant error
 * responses
 */
public class Exception extends BaseServerResponseException {

	private final ErrorType errorType;
	private final String errorCode;
	private final List<String> details;
	private final String location;

	// Enum for common error types
	public enum ErrorType {
		VALIDATION_ERROR(400, "invalid", "Validation Error"),
		RESOURCE_NOT_FOUND(404, "not-found", "Resource Not Found"),
		BUSINESS_RULE_VIOLATION(422, "business-rule", "Business Rule Violation"),
		AUTHENTICATION_ERROR(401, "security", "Authentication Error"),
		AUTHORIZATION_ERROR(403, "forbidden", "Authorization Error"),
		INTERNAL_ERROR(500, "exception", "Internal Server Error"),
		CONFLICT(409, "conflict", "Resource Conflict"),
		BAD_REQUEST(400, "invalid", "Bad Request"),
		TIMEOUT(408, "timeout", "Request Timeout"),
		SERVICE_UNAVAILABLE(503, "transient", "Service Unavailable");

		private final int httpStatus;
		private final String fhirCode;
		private final String description;

		ErrorType(int httpStatus, String fhirCode, String description) {
			this.httpStatus = httpStatus;
			this.fhirCode = fhirCode;
			this.description = description;
		}

		public int getHttpStatus() {
			return httpStatus;
		}

		public String getFhirCode() {
			return fhirCode;
		}

		public String getDescription() {
			return description;
		}
	}

	// Constructor with custom message only (most flexible)
	public Exception(String customMessage) {
		super(500, customMessage); // Default to internal server error
		this.errorType = ErrorType.INTERNAL_ERROR;
		this.errorCode = null;
		this.details = new ArrayList<>();
		this.location = null;
		buildOperationOutcome();
	}

	// Constructor with custom message and specific HTTP status
	public Exception(int httpStatus, String customMessage) {
		super(httpStatus, customMessage);
		this.errorType = getErrorTypeByStatus(httpStatus);
		this.errorCode = null;
		this.details = new ArrayList<>();
		this.location = null;
		buildOperationOutcome();
	}

	// Constructor with just error type and message
	public Exception(ErrorType errorType, String message) {
		super(errorType.getHttpStatus(), message);
		this.errorType = errorType;
		this.errorCode = null;
		this.details = new ArrayList<>();
		this.location = null;
		buildOperationOutcome();
	}

	// Constructor with error type, message, and cause
	public Exception(ErrorType errorType, String message, Throwable cause) {
		super(errorType.getHttpStatus(), message, cause);
		this.errorType = errorType;
		this.errorCode = null;
		this.details = new ArrayList<>();
		this.location = null;
		buildOperationOutcome();
	}

	// Constructor with custom error code
	public Exception(ErrorType errorType, String errorCode, String message) {
		super(errorType.getHttpStatus(), message);
		this.errorType = errorType;
		this.errorCode = errorCode;
		this.details = new ArrayList<>();
		this.location = null;
		buildOperationOutcome();
	}

	// Full constructor with all parameters
	public Exception(ErrorType errorType, String errorCode, String message,
			List<String> details, String location, Throwable cause) {
		super(errorType.getHttpStatus(), message, cause);
		this.errorType = errorType;
		this.errorCode = errorCode;
		this.details = details != null ? new ArrayList<>(details) : new ArrayList<>();
		this.location = location;
		buildOperationOutcome();
	}

	// Builder pattern for complex exception creation
	public static class Builder {
		private ErrorType errorType;
		private String errorCode;
		private String message;
		private List<String> details = new ArrayList<>();
		private String location;
		private Throwable cause;

		public Builder(ErrorType errorType, String message) {
			this.errorType = errorType;
			this.message = message;
		}

		public Builder withErrorCode(String errorCode) {
			this.errorCode = errorCode;
			return this;
		}

		public Builder withDetail(String detail) {
			this.details.add(detail);
			return this;
		}

		public Builder withDetails(List<String> details) {
			this.details.addAll(details);
			return this;
		}

		public Builder withLocation(String location) {
			this.location = location;
			return this;
		}

		public Builder withCause(Throwable cause) {
			this.cause = cause;
			return this;
		}

		public Exception build() {
			return new Exception(errorType, errorCode, message, details, location, cause);
		}
	}

	// Helper method to determine ErrorType by HTTP status
	private ErrorType getErrorTypeByStatus(int httpStatus) {
		for (ErrorType type : ErrorType.values()) {
			if (type.getHttpStatus() == httpStatus) {
				return type;
			}
		}
		return ErrorType.INTERNAL_ERROR; // Default fallback
	}

	// Build FHIR OperationOutcome - Simple format
	private void buildOperationOutcome() {
		OperationOutcome outcome = new OperationOutcome();

		OperationOutcome.OperationOutcomeIssueComponent issue = outcome.addIssue();
		issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);

		// Set the IssueType directly from the FHIR enum
		OperationOutcome.IssueType issueType = mapToFhirIssueType(errorType);
		issue.setCode(issueType);

		// Set diagnostics message
		issue.setDiagnostics(getMessage());

		// Add location if provided
		if (location != null) {
			issue.addLocation(location);
		}

		setOperationOutcome(outcome);
	}

	// Map our ErrorType to FHIR IssueType enum
	private OperationOutcome.IssueType mapToFhirIssueType(ErrorType errorType) {
		switch (errorType) {
			case VALIDATION_ERROR:
			case BAD_REQUEST:
				return OperationOutcome.IssueType.INVALID;
			case RESOURCE_NOT_FOUND:
				return OperationOutcome.IssueType.NOTFOUND;
			case BUSINESS_RULE_VIOLATION:
				return OperationOutcome.IssueType.BUSINESSRULE;
			case AUTHENTICATION_ERROR:
			case AUTHORIZATION_ERROR:
				return OperationOutcome.IssueType.SECURITY;
			case CONFLICT:
				return OperationOutcome.IssueType.CONFLICT;
			case TIMEOUT:
				return OperationOutcome.IssueType.TIMEOUT;
			case SERVICE_UNAVAILABLE:
				return OperationOutcome.IssueType.TRANSIENT;
			case INTERNAL_ERROR:
			default:
				return OperationOutcome.IssueType.EXCEPTION;
		}
	}

	// Static factory methods for common scenarios
	public static Exception notFound(String resourceType, String id) {
		return new Exception(
				ErrorType.RESOURCE_NOT_FOUND,
				"RESOURCE_NOT_FOUND",
				String.format("%s with id '%s' was not found", resourceType, id));
	}

	public static Exception validationError(String message, List<String> validationErrors) {
		return new Builder(ErrorType.VALIDATION_ERROR, message)
				.withErrorCode("VALIDATION_FAILED")
				.withDetails(validationErrors)
				.build();
	}

	public static Exception businessRuleViolation(String rule, String message) {
		return new Builder(ErrorType.BUSINESS_RULE_VIOLATION, message)
				.withErrorCode("BUSINESS_RULE_VIOLATION")
				.withDetail("Rule: " + rule)
				.build();
	}

	public static Exception internalError(String message, Throwable cause) {
		return new Exception(ErrorType.INTERNAL_ERROR, message, cause);
	}

	public static Exception unauthorized(String message) {
		return new Exception(ErrorType.AUTHENTICATION_ERROR, "UNAUTHORIZED", message);
	}

	public static Exception forbidden(String resource, String action) {
		return new Exception(
				ErrorType.AUTHORIZATION_ERROR,
				"FORBIDDEN",
				String.format("Access denied for action '%s' on resource '%s'", action, resource));
	}

	public static Exception conflict(String message) {
		return new Exception(ErrorType.CONFLICT, "RESOURCE_CONFLICT", message);
	}

	// Quick factory methods for custom messages
	public static Exception badRequest(String message) {
		return new Exception(ErrorType.BAD_REQUEST, message);
	}

	public static Exception serverError(String message) {
		return new Exception(ErrorType.INTERNAL_ERROR, message);
	}

	public static Exception customError(int httpStatus, String message) {
		return new Exception(httpStatus, message);
	}

	// Getters
	public ErrorType getErrorType() {
		return errorType;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public List<String> getDetails() {
		return new ArrayList<>(details);
	}

	public String getLocation() {
		return location;
	}
}