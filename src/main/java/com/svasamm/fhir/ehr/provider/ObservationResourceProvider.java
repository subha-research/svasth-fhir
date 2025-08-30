package com.svasamm.fhir.ehr.provider;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.svasamm.fhir.config.HospitalConfig;
import com.svasamm.fhir.ehr.dto.observation.ObservationDto;
import com.svasamm.fhir.ehr.mapper.ObservationMapper;
import com.svasamm.fhir.ehr.service.ObservationService;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.annotation.ConditionalUrlParam;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Custom Observation Resource Provider that extends HAPI's JPA provider
 * This preserves all JPA functionality (versioning, locking, etc.) while adding
 * custom logic and mapper operations
 */
public class ObservationResourceProvider extends BaseJpaResourceProvider<Observation> {

	private static final Logger logger = LoggerFactory.getLogger(ObservationResourceProvider.class);

	@Autowired
	private ObservationService observationService;

	@Autowired
	private HospitalConfig hospitalConfig;

	@Autowired
	private ObservationMapper observationMapper;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public ObservationResourceProvider(IFhirResourceDao<Observation> theDao) {
		super(theDao);
		logger.info("🚀 CUSTOM ObservationResourceProvider CONSTRUCTOR CALLED with JPA DAO 🚀");
	}

	@Override
	public Class<Observation> getResourceType() {
		logger.info("CUSTOM ObservationResourceProvider.getResourceType() called");
		return Observation.class;
	}

	@Create
	public MethodOutcome create(@ResourceParam Observation theObservation, RequestDetails theRequestDetails) {
		logger.error("Custom ObservationResourceProvider.create() called");
		logger.error("Observation code: {}", theObservation.getCode().getCodingFirstRep().getDisplay());

		try {
			// Apply custom EHR enrichment
			enrichObservationForEHR(theObservation);

			// Use DAO directly instead of super.create()
			MethodOutcome result = getDao().create(theObservation, theRequestDetails);

			logger.error("Observation created with ID: {} ", result.getId());
			return result;

		} catch (Exception e) {
			logger.error("Error in custom create observation provider: ", e);
			throw e;
		}
	}

	@Update
	public MethodOutcome update(
			HttpServletRequest theRequest,
			@IdParam IdType theId,
			@ResourceParam Observation theObservation,
			@ConditionalUrlParam String theConditional,
			RequestDetails theRequestDetails) {

		logger.info("Custom ObservationResourceProvider.update() called for ID: {}", theId);

		try {
			// Apply custom EHR enrichment
			enrichObservationForEHR(theObservation);

			// Call parent JPA implementation - this handles optimistic locking
			// automatically
			MethodOutcome result = super.update(theRequest, theObservation, theId, theConditional, theRequestDetails);

			// Custom post-processing
			if (observationService != null) {
				// Add any additional custom logic here
				logger.info("Observation updated successfully with ID: {}", result.getId());
			}

			return result;

		} catch (Exception e) {
			logger.error("Error in CUSTOM update: ", e);
			throw e;
		}
	}

	@Read
	public Observation read(HttpServletRequest theRequest, @IdParam IdType theId, RequestDetails theRequestDetails) {
		logger.info("CUSTOM ObservationResourceProvider.read() called for ID: {}", theId);

		try {
			// Use the parent JPA implementation for reading
			Observation observation = super.read(theRequest, theId, theRequestDetails);

			// Add any custom post-read processing if needed
			if (observationService != null) {
				// Custom logic after reading
				logger.debug("Observation read successfully: {}", theId);
			}

			return observation;

		} catch (Exception e) {
			logger.error("Error reading observation {}: ", theId, e);
			throw new ResourceNotFoundException(theId);
		}
	}

	/**
	 * Custom operation to get observation in mapped format
	 * Usage: GET /Observation/{id}/$mapped
	 */
	@Operation(name = "$mapped", idempotent = true, type = Observation.class)
	public void getMappedObservation(
			@IdParam IdType theId,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom ObservationResourceProvider.getMappedObservation() called for ID: {}", theId);

		try {
			// Get the observation using service
			Observation observation = observationService.getObservationById(theId.getIdPart());

			if (observation == null) {
				sendErrorResponse(theResponse, HttpServletResponse.SC_NOT_FOUND, "Observation not found");
				return;
			}

			// Map to custom format
			ObservationDto mappedObservation = observationMapper.mapToDTO(observation);
			String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mappedObservation);

			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Mapped observation response sent for ID: {}", theId);

		} catch (Exception e) {
			logger.error("Error getting mapped observation {}: ", theId, e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	/**
	 * Custom operation to search observations and return them in mapped format
	 * Usage: GET /Observation/$search-mapped?subject=Patient/123&code=8867-4&category=vital-signs
	 */
	@Operation(name = "$search-mapped", idempotent = true, type = Observation.class)
	public void searchMappedObservations(
			@OperationParam(name = "subject") ReferenceParam theSubject,
			@OperationParam(name = "patient") ReferenceParam thePatient,
			@OperationParam(name = "code") TokenParam theCode,
			@OperationParam(name = "category") TokenParam theCategory,
			@OperationParam(name = "date") DateRangeParam theDate,
			@OperationParam(name = "status") TokenParam theStatus,
			@OperationParam(name = "_count") NumberParam theCount,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom ObservationResourceProvider.searchMappedObservations() called");

		try {
			// Perform search using service
			List<Observation> observations = observationService.searchObservations(
					theSubject, thePatient, theCode, theCategory, theDate, theStatus, theCount);

			// Build bundle response
			String jsonResponse = buildBundleResponse(observations, "searchset", null);
			
			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Mapped observations search response sent, {} observations found", observations.size());

		} catch (Exception e) {
			logger.error("Error in search mapped observations: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	/**
	 * Custom operation to get patient's observations in mapped format
	 * Usage: GET /Observation/$patient-mapped?patient=Patient/123&category=vital-signs&start-date=2023-01-01
	 */
	@Operation(name = "$patient-mapped", idempotent = true, type = Observation.class)
	public void getPatientMappedObservations(
			@OperationParam(name = "patient") ReferenceParam thePatient,
			@OperationParam(name = "category") TokenParam theCategory,
			@OperationParam(name = "code") TokenParam theCode,
			@OperationParam(name = "start-date") DateParam theStartDate,
			@OperationParam(name = "end-date") DateParam theEndDate,
			@OperationParam(name = "_count") NumberParam theCount,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom ObservationResourceProvider.getPatientMappedObservations() called for patient: {}",
				thePatient.getValue());

		try {
			// Create search parameters
			DateRangeParam dateRange = buildDateRange(theStartDate, theEndDate);
			ReferenceParam subjectParam = new ReferenceParam(thePatient.getValue());

			// Perform search using service
			List<Observation> observations = observationService.searchObservations(
					subjectParam, thePatient, theCode, theCategory, dateRange, null, theCount);

			// Build bundle response with patient info
			String jsonResponse = buildBundleResponse(observations, "searchset", thePatient.getValue());
			
			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Mapped patient observations response sent, {} observations found for patient {}",
					observations.size(), thePatient.getValue());

		} catch (Exception e) {
			logger.error("Error in get patient mapped observations: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	/**
	 * Custom operation to get observation summary in mapped format
	 * Usage: GET /Observation/{id}/$observation-summary
	 */
	@Operation(name = "$observation-summary", idempotent = true, type = Observation.class)
	public void observationSummary(
			@IdParam IdType theObservationId,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom ObservationResourceProvider.observationSummary() called for ID: {}", theObservationId);

		try {
			// Get the observation using service
			Observation observation = observationService.getObservationById(theObservationId.getIdPart());

			if (observation == null) {
				sendErrorResponse(theResponse, HttpServletResponse.SC_NOT_FOUND, "Observation not found");
				return;
			}

			// Create single observation list for bundle response
			List<Observation> observations = List.of(observation);
			
			// Build collection bundle response
			String jsonResponse = buildBundleResponse(observations, "collection", null, 
					"observationId", theObservationId.getIdPart());
			
			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Observation summary response sent for ID: {}", theObservationId);

		} catch (Exception e) {
			logger.error("Error in observation summary: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	/**
	 * Custom operation to get patient observations and return them in mapped format
	 * Usage: GET /Observation/$patient-observations?patient=Patient/123&category=vital-signs&start-date=2023-01-01
	 */
	@Operation(name = "$patient-observations", idempotent = true, type = Observation.class)
	public void patientObservations(
			@OperationParam(name = "patient") ReferenceParam thePatient,
			@OperationParam(name = "category") TokenParam theCategory,
			@OperationParam(name = "code") TokenParam theCode,
			@OperationParam(name = "start-date") DateParam theStartDate,
			@OperationParam(name = "end-date") DateParam theEndDate,
			@OperationParam(name = "status") TokenParam theStatus,
			@OperationParam(name = "_count") NumberParam theCount,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom ObservationResourceProvider.patientObservations() called");

		try {
			// Create search parameters
			DateRangeParam dateRange = buildDateRange(theStartDate, theEndDate);
			ReferenceParam subjectParam = thePatient != null ? new ReferenceParam(thePatient.getValue()) : null;

			// Perform search using service
			List<Observation> observations = observationService.searchObservations(
					subjectParam, thePatient, theCode, theCategory, dateRange, theStatus, theCount);

			// Build bundle response with patient info
			String patientValue = thePatient != null ? thePatient.getValue() : "";
			String jsonResponse = buildBundleResponse(observations, "searchset", patientValue);
			
			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Patient observations response sent, {} observations found for patient {}", 
					observations.size(), patientValue);

		} catch (Exception e) {
			logger.error("Error in patient observations: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	/**
	 * Custom operation to get vital signs trend in mapped format
	 * Usage: GET /Observation/$vital-signs-trend?patient=Patient/123&code=8867-4&period=30
	 */
	@Operation(name = "$vital-signs-trend", idempotent = true, type = Observation.class)
	public void vitalSignsTrend(
			@OperationParam(name = "patient", min = 1) ReferenceParam thePatient,
			@OperationParam(name = "code") TokenParam theCode,
			@OperationParam(name = "period") NumberParam thePeriodDays,
			@OperationParam(name = "category") TokenParam theCategory,
			@OperationParam(name = "_count") NumberParam theCount,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom ObservationResourceProvider.vitalSignsTrend() called");

		try {
			if (thePatient == null) {
				sendErrorResponse(theResponse, HttpServletResponse.SC_BAD_REQUEST, "Patient parameter is required");
				return;
			}

			// Set default period to 30 days if not provided
			int periodDays = thePeriodDays != null ? thePeriodDays.getValue().intValue() : 30;

			// Create date range for the period (last X days)
			DateRangeParam dateRange = buildPeriodDateRange(periodDays);

			// Set category to vital-signs if not provided
			TokenParam category = theCategory;
			if (category == null) {
				category = new TokenParam();
				category.setSystem("http://terminology.hl7.org/CodeSystem/observation-category");
				category.setValue("vital-signs");
			}

			// Create subject parameter
			ReferenceParam subjectParam = new ReferenceParam(thePatient.getValue());

			// Perform search using service
			List<Observation> observations = observationService.searchObservations(
					subjectParam, thePatient, theCode, category, dateRange, null, theCount);

			// Build bundle response with additional metadata
			String codeValue = theCode != null ? theCode.getValue() : "all-vital-signs";
			String jsonResponse = buildVitalSignsTrendResponse(observations, thePatient.getValue(), periodDays, codeValue);
			
			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Vital signs trend response sent, {} observations found for patient {} over {} days", 
					observations.size(), thePatient.getValue(), periodDays);

		} catch (Exception e) {
			logger.error("Error in vital signs trend: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	@Search
	public List<Observation> search(
			@OptionalParam(name = Observation.SP_SUBJECT) ReferenceParam theSubject,
			@OptionalParam(name = Observation.SP_PATIENT) ReferenceParam thePatient,
			@OptionalParam(name = Observation.SP_CODE) TokenParam theCode,
			@OptionalParam(name = Observation.SP_CATEGORY) TokenParam theCategory,
			@OptionalParam(name = Observation.SP_DATE) DateRangeParam theDate,
			@OptionalParam(name = Observation.SP_STATUS) TokenParam theStatus,
			@OptionalParam(name = "_count") NumberParam theCount) {

		// If you have custom search logic, use your service
		if (observationService != null) {
			return observationService.searchObservations(theSubject, thePatient, theCode,
					theCategory, theDate, theStatus, theCount);
		}

		// Otherwise, fall back to default JPA search
		return searchByParameters(
				buildSearchParams(theSubject, thePatient, theCode, theCategory, theDate, theStatus, theCount));
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
	 * Common method to build bundle response from observations list
	 */
	private String buildBundleResponse(List<Observation> observations, String bundleType, String patientValue) {
		return buildBundleResponse(observations, bundleType, patientValue, null, null);
	}

	/**
	 * Common method to build bundle response with additional metadata
	 */
	private String buildBundleResponse(List<Observation> observations, String bundleType, String patientValue, 
			String additionalKey, String additionalValue) {
		try {
			StringBuilder jsonResponse = new StringBuilder();
			jsonResponse.append("{\"resourceType\":\"Bundle\",\"type\":\"").append(bundleType).append("\",\"total\":")
					.append(observations.size());

			// Add patient info if provided
			if (patientValue != null && !patientValue.isEmpty()) {
				jsonResponse.append(",\"patient\":\"").append(patientValue).append("\"");
			}

			// Add additional metadata if provided
			if (additionalKey != null && additionalValue != null) {
				jsonResponse.append(",\"").append(additionalKey).append("\":\"").append(additionalValue).append("\"");
			}

			jsonResponse.append(",\"entry\":[");

			// Add all observations
			for (int i = 0; i < observations.size(); i++) {
				if (i > 0) {
					jsonResponse.append(",");
				}
				ObservationDto mappedObservation = observationMapper.mapToDTO(observations.get(i));
				String observationJson = objectMapper.writeValueAsString(mappedObservation);
				jsonResponse.append("{\"resource\":")
						.append(observationJson)
						.append("}");
			}
			jsonResponse.append("]}");

			return jsonResponse.toString();
		} catch (Exception e) {
			logger.error("Error building bundle response", e);
			return "{\"error\":\"Error building response\"}";
		}
	}

	/**
	 * Specialized method to build vital signs trend response
	 */
	private String buildVitalSignsTrendResponse(List<Observation> observations, String patientValue, 
			int periodDays, String codeValue) {
		try {
			StringBuilder jsonResponse = new StringBuilder();
			jsonResponse.append("{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":")
					.append(observations.size())
					.append(",\"patient\":\"").append(patientValue).append("\"")
					.append(",\"period\":").append(periodDays)
					.append(",\"code\":\"").append(codeValue).append("\"")
					.append(",\"entry\":[");

			for (int i = 0; i < observations.size(); i++) {
				if (i > 0) {
					jsonResponse.append(",");
				}
				ObservationDto mappedObservation = observationMapper.mapToDTO(observations.get(i));
				String observationJson = objectMapper.writeValueAsString(mappedObservation);
				jsonResponse.append("{\"resource\":")
						.append(observationJson)
						.append("}");
			}
			jsonResponse.append("]}");

			return jsonResponse.toString();
		} catch (Exception e) {
			logger.error("Error building vital signs trend response", e);
			return "{\"error\":\"Error building response\"}";
		}
	}

	// ============================================================================
	// HELPER METHODS
	// ============================================================================

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

	/**
	 * Helper method to build date range for a period (last X days)
	 */
	private DateRangeParam buildPeriodDateRange(int periodDays) {
		DateRangeParam dateRange = new DateRangeParam();
		
		// Calculate start date (period days ago)
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, -periodDays);
		DateParam startDate = new DateParam();
		startDate.setPrefix(ca.uhn.fhir.rest.param.ParamPrefixEnum.GREATERTHAN_OR_EQUALS);
		startDate.setValue(cal.getTime());
		dateRange.setLowerBound(startDate);

		// End date is today
		DateParam endDate = new DateParam();
		endDate.setPrefix(ca.uhn.fhir.rest.param.ParamPrefixEnum.LESSTHAN_OR_EQUALS);
		endDate.setValue(new Date());
		dateRange.setUpperBound(endDate);

		return dateRange;
	}

	private void enrichObservationForEHR(Observation observation) {
		// Set default status if not present
		if (observation.getStatus() == null) {
			observation.setStatus(Observation.ObservationStatus.FINAL);
		}

		// Add EHR metadata
		if (observation.getMeta() == null) {
			observation.setMeta(new Meta());
		}
		observation.getMeta()
				.addProfile("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/ehr-observation")
				.addTag()
				.setSystem("http://" + hospitalConfig.getIdentifier() + "/fhir/CodeSystem/data-source")
				.setCode("ehr")
				.setDisplay("Electronic Health Record");

		// Add audit extension
		Extension auditExtension = new Extension();
		auditExtension.setUrl("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/audit-trail");
		auditExtension.addExtension("created-date", new DateTimeType(new Date()));
		auditExtension.addExtension("created-by", new StringType("EHR System"));
		observation.addExtension(auditExtension);

		// Set effective date if not present
		if (observation.getEffective() == null) {
			observation.setEffective(new DateTimeType(new Date()));
		}

		// Add issued timestamp
		if (observation.getIssued() == null) {
			observation.setIssued(new Date());
		}
	}

	// Helper method to build search parameters
	private List<Observation> searchByParameters(ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams) {
		try {
			return getDao().search(searchParams).getResources(0, 100)
					.stream()
					.map(resource -> (Observation) resource)
					.collect(java.util.stream.Collectors.toList());
		} catch (Exception e) {
			logger.error("Error in search: ", e);
			return java.util.Collections.emptyList();
		}
	}

	private ca.uhn.fhir.jpa.searchparam.SearchParameterMap buildSearchParams(
			ReferenceParam theSubject, ReferenceParam thePatient, TokenParam theCode,
			TokenParam theCategory, DateRangeParam theDate, TokenParam theStatus, NumberParam theCount) {

		ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = new ca.uhn.fhir.jpa.searchparam.SearchParameterMap();

		if (theSubject != null) {
			searchParams.add(Observation.SP_SUBJECT, theSubject);
		}
		if (thePatient != null) {
			searchParams.add(Observation.SP_PATIENT, thePatient);
		}
		if (theCode != null) {
			searchParams.add(Observation.SP_CODE, theCode);
		}
		if (theCategory != null) {
			searchParams.add(Observation.SP_CATEGORY, theCategory);
		}
		if (theDate != null) {
			searchParams.add(Observation.SP_DATE, theDate);
		}
		if (theStatus != null) {
			searchParams.add(Observation.SP_STATUS, theStatus);
		}

		return searchParams;
	}
}