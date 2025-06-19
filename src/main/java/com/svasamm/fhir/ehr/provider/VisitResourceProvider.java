package com.svasamm.fhir.ehr.provider;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.svasamm.fhir.ehr.service.VisitService;
import com.svasamm.fhir.ehr.mapper.VisitMapper;
import com.svasamm.fhir.ehr.dto.visits.VisitDto;
import com.svasamm.fhir.config.HospitalConfig;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Custom Visit Resource Provider that extends HAPI's JPA provider
 * This preserves all JPA functionality (versioning, locking, etc.) while adding
 * custom logic
 * Visits are implemented using the Encounter resource
 */
public class VisitResourceProvider extends BaseJpaResourceProvider<Encounter> {

	private static final Logger logger = LoggerFactory.getLogger(VisitResourceProvider.class);

	@Autowired
	private VisitService visitService;

	@Autowired
	private VisitMapper visitMapper;

	@Autowired
	private HospitalConfig hospitalConfig;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public VisitResourceProvider(IFhirResourceDao<Encounter> theDao) {
		super(theDao);
		logger.info("🚀 CUSTOM VisitResourceProvider CONSTRUCTOR CALLED with JPA DAO 🚀");
	}

	@Override
	public Class<Encounter> getResourceType() {
		logger.info("CUSTOM VisitResourceProvider.getResourceType() called");
		return Encounter.class;
	}

	@Create
	public MethodOutcome create(@ResourceParam Encounter theVisit, RequestDetails theRequestDetails) {
		logger.error("Custom VisitResourceProvider.create() called");
		logger.error("Visit class: {}", theVisit.getClass_() != null ? theVisit.getClass_().getCode() : "No class");

		try {
			// Apply custom EHR enrichment
			enrichVisitForEHR(theVisit);

			// Use DAO directly instead of super.create()
			MethodOutcome result = getDao().create(theVisit, theRequestDetails);

			logger.error("Visit created with ID: {} ", result.getId());
			return result;

		} catch (Exception e) {
			logger.error("Error in custom create visit provider: ", e);
			throw e;
		}
	}

	@Update
	public MethodOutcome update(
			HttpServletRequest theRequest,
			@IdParam IdType theId,
			@ResourceParam Encounter theVisit,
			@ConditionalUrlParam String theConditional,
			RequestDetails theRequestDetails) {

		logger.info("Custom VisitResourceProvider.update() called for ID: {}", theId);

		try {
			// Apply custom EHR enrichment
			enrichVisitForEHR(theVisit);

			// Call parent JPA implementation - this handles optimistic locking
			// automatically
			MethodOutcome result = super.update(theRequest, theVisit, theId, theConditional, theRequestDetails);

			// Custom post-processing
			if (visitService != null) {
				// Add any additional custom logic here
				logger.info("Visit updated successfully with ID: {}", result.getId());
			}

			return result;

		} catch (Exception e) {
			logger.error("Error in CUSTOM update: ", e);
			throw e;
		}
	}

	@Read
	public Encounter read(HttpServletRequest theRequest, @IdParam IdType theId, RequestDetails theRequestDetails) {
		logger.info("CUSTOM VisitResourceProvider.read() called for ID: {}", theId);

		try {
			// Use the parent JPA implementation for reading
			Encounter visit = super.read(theRequest, theId, theRequestDetails);

			// Add any custom post-read processing if needed
			if (visitService != null) {
				// Custom logic after reading
				logger.debug("Visit read successfully: {}", theId);
			}

			return visit;

		} catch (Exception e) {
			logger.error("Error reading visit {}: ", theId, e);
			throw new ResourceNotFoundException(theId);
		}
	}

	/**
	 * Custom operation to get visit in mapped format
	 * Usage: GET /Encounter/{id}/$mapped
	 */
	@Operation(name = "$mapped", idempotent = true, type = Encounter.class)
	public void getMappedVisit(
			@IdParam IdType theId,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom VisitResourceProvider.getMappedVisit() called for ID: {}", theId);

		try {
			// Get the visit using service
			Encounter visit = visitService.getVisitById(theId.getIdPart());

			if (visit == null) {
				sendErrorResponse(theResponse, HttpServletResponse.SC_NOT_FOUND, "Visit not found");
				return;
			}

			// Map to custom format
			VisitDto mappedVisit = visitMapper.mapToDTO(visit);
			String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mappedVisit);

			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Mapped visit response sent for ID: {}", theId);

		} catch (Exception e) {
			logger.error("Error getting mapped visit {}: ", theId, e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	/**
	 * Custom operation to search visits and return them in mapped format
	 * Usage: GET /Encounter/$search-mapped?patient=Patient/123&class=inpatient&status=finished
	 */
	@Operation(name = "$search-mapped", idempotent = true, type = Encounter.class)
	public void searchMappedVisits(
			@OperationParam(name = "subject") ReferenceParam theSubject,
			@OperationParam(name = "patient") ReferenceParam thePatient,
			@OperationParam(name = "class") TokenParam theClass,
			@OperationParam(name = "date") DateRangeParam theDate,
			@OperationParam(name = "status") TokenParam theStatus,
			@OperationParam(name = "type") TokenParam theType,
			@OperationParam(name = "location") ReferenceParam theLocation,
			@OperationParam(name = "_count") NumberParam theCount,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom VisitResourceProvider.searchMappedVisits() called");

		try {
			// Perform search using service
			List<Encounter> visits = visitService.searchVisits(
					theSubject, thePatient, theClass, theDate, theStatus, theType, theLocation, theCount);

			// Build bundle response
			String jsonResponse = buildVisitBundleResponse(visits, "searchset", null);
			
			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Mapped visits search response sent, {} visits found", visits.size());

		} catch (Exception e) {
			logger.error("Error in search mapped visits: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	/**
	 * Custom operation to get patient visits in mapped format
	 * Usage: GET /Encounter/$patient-mapped?patient=Patient/123&class=inpatient&start-date=2023-01-01
	 */
	@Operation(name = "$patient-mapped", idempotent = true, type = Encounter.class)
	public void getPatientMappedVisits(
			@OperationParam(name = "patient") ReferenceParam thePatient,
			@OperationParam(name = "class") TokenParam theClass,
			@OperationParam(name = "type") TokenParam theType,
			@OperationParam(name = "start-date") DateParam theStartDate,
			@OperationParam(name = "end-date") DateParam theEndDate,
			@OperationParam(name = "status") TokenParam theStatus,
			@OperationParam(name = "_count") NumberParam theCount,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom VisitResourceProvider.getPatientMappedVisits() called for patient: {}",
				thePatient != null ? thePatient.getValue() : "null");

		try {
			if (thePatient == null) {
				sendErrorResponse(theResponse, HttpServletResponse.SC_BAD_REQUEST, "Patient parameter is required");
				return;
			}

			// Create search parameters
			DateRangeParam dateRange = buildDateRange(theStartDate, theEndDate);
			ReferenceParam subjectParam = new ReferenceParam(thePatient.getValue());

			// Perform search using service
			List<Encounter> visits = visitService.searchVisits(
					subjectParam, thePatient, theClass, dateRange, theStatus, theType, null, theCount);

			// Build bundle response with patient info
			String jsonResponse = buildVisitBundleResponse(visits, "searchset", thePatient.getValue());
			
			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Mapped patient visits response sent, {} visits found for patient {}",
					visits.size(), thePatient.getValue());

		} catch (Exception e) {
			logger.error("Error in get patient mapped visits: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	/**
	 * Custom operation to get visit summary in mapped format
	 * Usage: GET /Encounter/{id}/$visit-summary-mapped
	 */
	@Operation(name = "$visit-summary-mapped", idempotent = true, type = Encounter.class)
	public void visitSummaryMapped(
			@IdParam IdType theVisitId,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom VisitResourceProvider.visitSummaryMapped() called for ID: {}", theVisitId);

		try {
			// Get the visit using service
			Encounter visit = visitService.getVisitById(theVisitId.getIdPart());

			if (visit == null) {
				sendErrorResponse(theResponse, HttpServletResponse.SC_NOT_FOUND, "Visit not found");
				return;
			}

			// Create single visit list for bundle response
			List<Encounter> visits = List.of(visit);
			
			// Build collection bundle response
			String jsonResponse = buildVisitBundleResponse(visits, "collection", null, 
					"visitId", theVisitId.getIdPart());
			
			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Visit summary response sent for ID: {}", theVisitId);

		} catch (Exception e) {
			logger.error("Error in visit summary: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	/**
	 * Custom operation to get active visits in mapped format
	 * Usage: GET /Encounter/$active-visits-mapped?location=Location/123&class=inpatient
	 */
	@Operation(name = "$active-visits-mapped", idempotent = true, type = Encounter.class)
	public void activeVisitsMapped(
			@OperationParam(name = "location") ReferenceParam theLocation,
			@OperationParam(name = "class") TokenParam theClass,
			@OperationParam(name = "_count") NumberParam theCount,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom VisitResourceProvider.activeVisitsMapped() called");

		try {
			// Create status parameter for active visits
			TokenParam activeStatus = new TokenParam();
			activeStatus.setSystem("http://hl7.org/fhir/encounter-status");
			activeStatus.setValue("in-progress");

			// Perform search using service
			List<Encounter> visits = visitService.searchVisits(
					null, null, theClass, null, activeStatus, null, theLocation, theCount);

			// Build bundle response with active visits metadata
			String jsonResponse = buildActiveVisitsResponse(visits, theLocation, theClass);
			
			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Active visits response sent, {} visits found", visits.size());

		} catch (Exception e) {
			logger.error("Error in active visits: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	/**
	 * Custom operation to get visit timeline in mapped format
	 * Usage: GET /Encounter/{id}/$visit-timeline?include-observations=true&include-procedures=true
	 */
	@Operation(name = "$visit-timeline", idempotent = true, type = Encounter.class)
	public void visitTimeline(
			@IdParam IdType theVisitId,
			@OperationParam(name = "include-observations") StringParam theIncludeObservations,
			@OperationParam(name = "include-procedures") StringParam theIncludeProcedures,
			@OperationParam(name = "period") NumberParam thePeriodDays,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom VisitResourceProvider.visitTimeline() called for ID: {}", theVisitId);

		try {
			if (visitService != null) {
				boolean includeObs = theIncludeObservations != null ? 
					"true".equalsIgnoreCase(theIncludeObservations.getValue()) : true;
				boolean includeProc = theIncludeProcedures != null ? 
					"true".equalsIgnoreCase(theIncludeProcedures.getValue()) : true;
				
				Bundle timelineBundle = visitService.getVisitTimeline(
						theVisitId.getIdPart(), includeObs, includeProc);

				// Convert bundle to JSON
				String jsonResponse = buildFhirBundleResponse(timelineBundle);
				
				sendFhirResponse(theResponse, jsonResponse);
				logger.info("Visit timeline response sent for ID: {}", theVisitId);
			} else {
				sendErrorResponse(theResponse, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Visit service not available");
			}

		} catch (Exception e) {
			logger.error("Error in visit timeline: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	// ============================================================================
	// EXISTING OPERATIONS (refactored to use common methods where applicable)
	// ============================================================================

	@Operation(name = "$visit-summary", idempotent = true)
	public Bundle visitSummary(@IdParam IdType theVisitId) {
		try {
			if (visitService != null) {
				return visitService.generateVisitSummary(theVisitId.getIdPart());
			}
			throw new UnsupportedOperationException("Visit service not available");
		} catch (Exception e) {
			logger.error("Error in visit summary for ID {}: ", theVisitId, e);
			// Return empty bundle instead of throwing exception
			Bundle errorBundle = new Bundle();
			errorBundle.setType(Bundle.BundleType.COLLECTION);
			errorBundle.setTotal(0);
			return errorBundle;
		}
	}

	@Operation(name = "$patient-visits", idempotent = true)
	public Bundle patientVisits(
			@OperationParam(name = "patient") ReferenceParam thePatient,
			@OperationParam(name = "class") TokenParam visitClass,
			@OperationParam(name = "start-date") DateParam startDate,
			@OperationParam(name = "end-date") DateParam endDate) {

		try {
			if (thePatient == null) {
				throw new IllegalArgumentException("Patient parameter is required");
			}
			
			if (visitService != null) {
				// Extract patient ID properly
				String patientId = extractPatientId(thePatient.getValue());
				return visitService.getPatientVisits(
						patientId,
						visitClass,
						startDate != null ? startDate.getValue() : null,
						endDate != null ? endDate.getValue() : null);
			}
			throw new UnsupportedOperationException("Visit service not available");
		} catch (Exception e) {
			logger.error("Error in patient visits for patient {}: ", thePatient != null ? thePatient.getValue() : "null", e);
			// Return empty bundle instead of throwing exception
			Bundle errorBundle = new Bundle();
			errorBundle.setType(Bundle.BundleType.COLLECTION);
			errorBundle.setTotal(0);
			return errorBundle;
		}
	}

	@Operation(name = "$active-visits", idempotent = true)
	public Bundle activeVisits(
			@OperationParam(name = "location") ReferenceParam location,
			@OperationParam(name = "class") TokenParam visitClass) {

		try {
			if (visitService != null) {
				return visitService.getActiveVisits(location, visitClass);
			}
			throw new UnsupportedOperationException("Visit service not available");
		} catch (Exception e) {
			logger.error("Error in active visits: ", e);
			// Return empty bundle instead of throwing exception
			Bundle errorBundle = new Bundle();
			errorBundle.setType(Bundle.BundleType.COLLECTION);
			errorBundle.setTotal(0);
			return errorBundle;
		}
	}

	@Search
	public List<Encounter> search(
			@OptionalParam(name = Encounter.SP_SUBJECT) ReferenceParam theSubject,
			@OptionalParam(name = Encounter.SP_PATIENT) ReferenceParam thePatient,
			@OptionalParam(name = Encounter.SP_CLASS) TokenParam theClass,
			@OptionalParam(name = Encounter.SP_DATE) DateRangeParam theDate,
			@OptionalParam(name = Encounter.SP_STATUS) TokenParam theStatus,
			@OptionalParam(name = Encounter.SP_TYPE) TokenParam theType,
			@OptionalParam(name = Encounter.SP_LOCATION) ReferenceParam theLocation,
			@OptionalParam(name = "_count") NumberParam theCount) {

		// If you have custom search logic, use your service
		if (visitService != null) {
			return visitService.searchVisits(theSubject, thePatient, theClass,
					theDate, theStatus, theType, theLocation, theCount);
		}

		// Otherwise, fall back to default JPA search
		return searchByParameters(
				buildSearchParams(theSubject, thePatient, theClass, theDate, theStatus, theType, theLocation, theCount));
	}

	// ============================================================================
	// COMMON RESPONSE METHODS
	// ============================================================================

	/**
	 * Common method to set response headers and send successful JSON response
	 */
	private void sendSuccessResponse(HttpServletResponse response, String jsonContent) {
		try {
			setCommonResponseHeaders(response);
			response.setStatus(HttpServletResponse.SC_OK);
			
			response.getWriter().write(jsonContent);
			response.getWriter().flush();
			response.getWriter().close();
		} catch (IOException e) {
			logger.error("Error writing success response", e);
		}
	}

	/**
	 * Common method to set response headers and send FHIR JSON response
	 */
	private void sendFhirResponse(HttpServletResponse response, String fhirContent) {
		try {
			response.setContentType("application/fhir+json");
			response.setCharacterEncoding("UTF-8");
			response.setHeader("Cache-Control", "no-cache");
			response.setStatus(HttpServletResponse.SC_OK);
			
			response.getWriter().write(fhirContent);
			response.getWriter().flush();
			response.getWriter().close();
		} catch (IOException e) {
			logger.error("Error writing FHIR response", e);
		}
	}

	/**
	 * Common method to set response headers and send error response
	 */
	private void sendErrorResponse(HttpServletResponse response, int statusCode, String errorMessage) {
		try {
			if (!response.isCommitted()) {
				setCommonResponseHeaders(response);
				response.setStatus(statusCode);
				
				String errorJson = String.format("{\"error\":\"%s\"}", errorMessage);
				response.getWriter().write(errorJson);
				response.getWriter().flush();
				response.getWriter().close();
			}
		} catch (IOException e) {
			logger.error("Error writing error response", e);
		}
	}

	/**
	 * Common method to set standard response headers
	 */
	private void setCommonResponseHeaders(HttpServletResponse response) {
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-cache");
	}

	/**
	 * Common method to build bundle response from visits list (mapped format)
	 */
	private String buildVisitBundleResponse(List<Encounter> visits, String bundleType, String patientValue) {
		return buildVisitBundleResponse(visits, bundleType, patientValue, null, null);
	}

	/**
	 * Common method to build bundle response with additional metadata (mapped format)
	 */
	private String buildVisitBundleResponse(List<Encounter> visits, String bundleType, String patientValue, 
			String additionalKey, String additionalValue) {
		try {
			StringBuilder jsonResponse = new StringBuilder();
			jsonResponse.append("{\"resourceType\":\"Bundle\",\"type\":\"").append(bundleType).append("\",\"total\":")
					.append(visits.size());

			// Add patient info if provided
			if (patientValue != null && !patientValue.isEmpty()) {
				jsonResponse.append(",\"patient\":\"").append(patientValue).append("\"");
			}

			// Add additional metadata if provided
			if (additionalKey != null && additionalValue != null) {
				jsonResponse.append(",\"").append(additionalKey).append("\":\"").append(additionalValue).append("\"");
			}

			jsonResponse.append(",\"entry\":[");

			// Add all visits (mapped format)
			for (int i = 0; i < visits.size(); i++) {
				if (i > 0) {
					jsonResponse.append(",");
				}
				VisitDto mappedVisit = visitMapper.mapToDTO(visits.get(i));
				String visitJson = objectMapper.writeValueAsString(mappedVisit);
				jsonResponse.append("{\"resource\":")
						.append(visitJson)
						.append("}");
			}
			jsonResponse.append("]}");

			return jsonResponse.toString();
		} catch (Exception e) {
			logger.error("Error building visit bundle response", e);
			return "{\"error\":\"Error building response\"}";
		}
	}

	/**
	 * Specialized method to build active visits response
	 */
	private String buildActiveVisitsResponse(List<Encounter> visits, ReferenceParam location, TokenParam visitClass) {
		try {
			StringBuilder jsonResponse = new StringBuilder();
			jsonResponse.append("{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":")
					.append(visits.size())
					.append(",\"status\":\"active\"");

			if (location != null) {
				jsonResponse.append(",\"location\":\"").append(location.getValue()).append("\"");
			}
			if (visitClass != null) {
				jsonResponse.append(",\"class\":\"").append(visitClass.getValue()).append("\"");
			}

			jsonResponse.append(",\"entry\":[");

			for (int i = 0; i < visits.size(); i++) {
				if (i > 0) {
					jsonResponse.append(",");
				}
				VisitDto mappedVisit = visitMapper.mapToDTO(visits.get(i));
				String visitJson = objectMapper.writeValueAsString(mappedVisit);
				jsonResponse.append("{\"resource\":")
						.append(visitJson)
						.append("}");
			}
			jsonResponse.append("]}");

			return jsonResponse.toString();
		} catch (Exception e) {
			logger.error("Error building active visits response", e);
			return "{\"error\":\"Error building response\"}";
		}
	}

	/**
	 * Common method to build FHIR Bundle response (standard FHIR format)
	 */
	private String buildFhirBundleResponse(Bundle bundle) {
		try {
			// Convert bundle to JSON
			ca.uhn.fhir.context.FhirContext ctx = ca.uhn.fhir.context.FhirContext.forR4();
			return ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
		} catch (Exception e) {
			logger.error("Error building FHIR bundle response", e);
			return "{\"error\":\"Error building FHIR response\"}";
		}
	}

	// ============================================================================
	// HELPER METHODS
	// ============================================================================

	/**
	 * Helper method to extract patient ID from reference
	 */
	private String extractPatientId(String patientReference) {
		if (patientReference == null) {
			throw new IllegalArgumentException("Patient reference cannot be null");
		}
		
		// Handle "Patient/123" format
		if (patientReference.startsWith("Patient/")) {
			return patientReference.substring(8); // Remove "Patient/" prefix
		}
		
		// Handle direct ID format
		return patientReference;
	}

	/**
	 * Helper method to build date range from start and end dates
	 */
	private DateRangeParam buildDateRange(DateParam startDate, DateParam endDate) {
		if (startDate == null && endDate == null) {
			return null;
		}

		DateRangeParam dateRange = new DateRangeParam();
		if (startDate != null) {
			DateParam lowerBound = new DateParam();
			lowerBound.setPrefix(ca.uhn.fhir.rest.param.ParamPrefixEnum.GREATERTHAN_OR_EQUALS);
			lowerBound.setValue(startDate.getValue());
			dateRange.setLowerBound(lowerBound);
		}
		if (endDate != null) {
			DateParam upperBound = new DateParam();
			upperBound.setPrefix(ca.uhn.fhir.rest.param.ParamPrefixEnum.LESSTHAN_OR_EQUALS);
			upperBound.setValue(endDate.getValue());
			dateRange.setUpperBound(upperBound);
		}
		return dateRange;
	}

	private void enrichVisitForEHR(Encounter visit) {
		// Set default status if not present
		if (visit.getStatus() == null) {
			visit.setStatus(Encounter.EncounterStatus.INPROGRESS);
		}

		// Add EHR metadata
		if (visit.getMeta() == null) {
			visit.setMeta(new Meta());
		}
		visit.getMeta()
				.addProfile("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/ehr-encounter")
				.addTag()
				.setSystem("http://" + hospitalConfig.getIdentifier() + "/fhir/CodeSystem/data-source")
				.setCode("ehr")
				.setDisplay("Electronic Health Record");

		// Add audit extension
		Extension auditExtension = new Extension();
		auditExtension.setUrl("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/audit-trail");
		auditExtension.addExtension("created-date", new DateTimeType(new Date()));
		auditExtension.addExtension("created-by", new StringType("EHR System"));
		visit.addExtension(auditExtension);

		// Set period start if not present
		if (visit.getPeriod() == null) {
			Period period = new Period();
			period.setStart(new Date());
			visit.setPeriod(period);
		} else if (visit.getPeriod().getStart() == null) {
			visit.getPeriod().setStart(new Date());
		}

		// Add default service provider if not present
		if (visit.getServiceProvider() == null) {
			visit.setServiceProvider(
					new Reference("Organization/" + hospitalConfig.getIdentifier())
							.setDisplay(hospitalConfig.getName()));
		}
	}

	// Helper method to build search parameters
	private List<Encounter> searchByParameters(ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams) {
		try {
			return getDao().search(searchParams).getResources(0, 100)
					.stream()
					.map(resource -> (Encounter) resource)
					.collect(java.util.stream.Collectors.toList());
		} catch (Exception e) {
			logger.error("Error in search: ", e);
			return java.util.Collections.emptyList();
		}
	}

	private ca.uhn.fhir.jpa.searchparam.SearchParameterMap buildSearchParams(
			ReferenceParam theSubject, ReferenceParam thePatient, TokenParam theClass,
			DateRangeParam theDate, TokenParam theStatus, TokenParam theType,
			ReferenceParam theLocation, NumberParam theCount) {

		ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = new ca.uhn.fhir.jpa.searchparam.SearchParameterMap();

		if (theSubject != null) {
			searchParams.add(Encounter.SP_SUBJECT, theSubject);
		}
		if (thePatient != null) {
			searchParams.add(Encounter.SP_PATIENT, thePatient);
		}
		if (theClass != null) {
			searchParams.add(Encounter.SP_CLASS, theClass);
		}
		if (theDate != null) {
			searchParams.add(Encounter.SP_DATE, theDate);
		}
		if (theStatus != null) {
			searchParams.add(Encounter.SP_STATUS, theStatus);
		}
		if (theType != null) {
			searchParams.add(Encounter.SP_TYPE, theType);
		}
		if (theLocation != null) {
			searchParams.add(Encounter.SP_LOCATION, theLocation);
		}

		return searchParams;
	}
}