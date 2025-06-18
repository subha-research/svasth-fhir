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
				theResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
				theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
				theResponse.getWriter().write("{\"error\":\"Observation not found\"}");
				theResponse.getWriter().flush();
				return;
			}

			// Map to custom format
			ObservationDto mappedObservation = observationMapper.mapToDTO(observation);
			String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mappedObservation);

			// Set response headers and status first
			theResponse.setStatus(HttpServletResponse.SC_OK);
			theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
			theResponse.setCharacterEncoding("UTF-8");
			theResponse.setHeader("Cache-Control", "no-cache");

			// Write response and flush immediately
			theResponse.getWriter().write(jsonResponse);
			theResponse.getWriter().flush();
			theResponse.getWriter().close();

			logger.info("Mapped observation response sent for ID: {}", theId);

		} catch (Exception e) {
			logger.error("Error getting mapped observation {}: ", theId, e);
			try {
				if (!theResponse.isCommitted()) {
					theResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
					theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
					theResponse.getWriter().write("{\"error\":\"Internal server error\"}");
					theResponse.getWriter().flush();
				}
			} catch (IOException ioException) {
				logger.error("Error writing error response", ioException);
			}
		}
	}

	/**
	 * Custom operation to search observations and return them in mapped format
	 * Usage: GET
	 * /Observation/$search-mapped?subject=Patient/123&code=8867-4&category=vital-signs
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

			// Map all observations to custom format
			StringBuilder jsonResponse = new StringBuilder();
			jsonResponse.append("{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":")
					.append(observations.size())
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

			// Set response headers and status first
			theResponse.setStatus(HttpServletResponse.SC_OK);
			theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
			theResponse.setCharacterEncoding("UTF-8");
			theResponse.setHeader("Cache-Control", "no-cache");

			// Write response and flush immediately
			theResponse.getWriter().write(jsonResponse.toString());
			theResponse.getWriter().flush();
			theResponse.getWriter().close();

			logger.info("Mapped observations search response sent, {} observations found", observations.size());

		} catch (Exception e) {
			logger.error("Error in search mapped observations: ", e);
			try {
				if (!theResponse.isCommitted()) {
					theResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
					theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
					theResponse.getWriter().write("{\"error\":\"Internal server error\"}");
					theResponse.getWriter().flush();
				}
			} catch (IOException ioException) {
				logger.error("Error writing error response", ioException);
			}
		}
	}

	/**
	 * Custom operation to get patient's observations in mapped format
	 * Usage: GET
	 * /Observation/$patient-mapped?patient=Patient/123&category=vital-signs&start-date=2023-01-01
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
			// Create search parameters for patient observations
			ReferenceParam subjectParam = new ReferenceParam(thePatient.getValue());
			DateRangeParam dateRange = null;

			if (theStartDate != null || theEndDate != null) {
				dateRange = new DateRangeParam();
				if (theStartDate != null) {
					DateParam lowerBound = new DateParam();
					lowerBound.setPrefix(ca.uhn.fhir.rest.param.ParamPrefixEnum.GREATERTHAN_OR_EQUALS);
					lowerBound.setValue(theStartDate.getValue());
					dateRange.setLowerBound(lowerBound);
				}
				if (theEndDate != null) {
					DateParam upperBound = new DateParam();
					upperBound.setPrefix(ca.uhn.fhir.rest.param.ParamPrefixEnum.LESSTHAN_OR_EQUALS);
					upperBound.setValue(theEndDate.getValue());
					dateRange.setUpperBound(upperBound);
				}
			}

			// Perform search using service
			List<Observation> observations = observationService.searchObservations(
					subjectParam, thePatient, theCode, theCategory, dateRange, null, theCount);

			// Map all observations to custom format
			StringBuilder jsonResponse = new StringBuilder();
			jsonResponse.append("{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":")
					.append(observations.size())
					.append(",\"patient\":\"").append(thePatient.getValue()).append("\"")
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

			// Set response headers and status first
			theResponse.setStatus(HttpServletResponse.SC_OK);
			theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
			theResponse.setCharacterEncoding("UTF-8");
			theResponse.setHeader("Cache-Control", "no-cache");

			// Write response and flush immediately
			theResponse.getWriter().write(jsonResponse.toString());
			theResponse.getWriter().flush();
			theResponse.getWriter().close();

			logger.info("Mapped patient observations response sent, {} observations found for patient {}",
					observations.size(), thePatient.getValue());

		} catch (Exception e) {
			logger.error("Error in get patient mapped observations: ", e);
			try {
				if (!theResponse.isCommitted()) {
					theResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
					theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
					theResponse.getWriter().write("{\"error\":\"Internal server error\"}");
					theResponse.getWriter().flush();
				}
			} catch (IOException ioException) {
				logger.error("Error writing error response", ioException);
			}
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
		// Get the observation using service (similar to your other operations)
		Observation observation = observationService.getObservationById(theObservationId.getIdPart());

		if (observation == null) {
			// Handle not found case (same pattern as $search-mapped error handling)
			theResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
			theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
			theResponse.getWriter().write("{\"error\":\"Observation not found\"}");
			theResponse.getWriter().flush();
			theResponse.getWriter().close();
			return;
		}

		// Map observation to custom format (same as your other operations)
		ObservationDto mappedObservation = observationMapper.mapToDTO(observation);

		// Create summary response in same format as $search-mapped
		StringBuilder jsonResponse = new StringBuilder();
		jsonResponse.append("{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"total\":1")
				.append(",\"observationId\":\"").append(theObservationId.getIdPart()).append("\"")
				.append(",\"entry\":[");

		// Add the mapped observation
		String observationJson = objectMapper.writeValueAsString(mappedObservation);
		jsonResponse.append("{\"resource\":")
				.append(observationJson)
				.append("}");

		jsonResponse.append("]}");

		// Set response headers and status first (exactly same as $search-mapped)
		theResponse.setStatus(HttpServletResponse.SC_OK);
		theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
		theResponse.setCharacterEncoding("UTF-8");
		theResponse.setHeader("Cache-Control", "no-cache");

		// Write response and flush immediately (exactly same as $search-mapped)
		theResponse.getWriter().write(jsonResponse.toString());
		theResponse.getWriter().flush();
		theResponse.getWriter().close();

		logger.info("Observation summary response sent for ID: {}", theObservationId);

	} catch (Exception e) {
		logger.error("Error in observation summary: ", e);
		try {
			if (!theResponse.isCommitted()) {
				theResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
				theResponse.getWriter().write("{\"error\":\"Internal server error\"}");
				theResponse.getWriter().flush();
			}
		} catch (IOException ioException) {
			logger.error("Error writing error response", ioException);
		}
	}
}
/**
 * Custom operation to get patient observations and return them in mapped format
 * Usage: GET
 * /Observation/$patient-observations?patient=Patient/123&category=vital-signs&start-date=2023-01-01
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
		// Create search parameters for patient observations (same as $patient-mapped logic)
		ReferenceParam subjectParam = null;
		DateRangeParam dateRange = null;

		if (thePatient != null) {
			subjectParam = new ReferenceParam(thePatient.getValue());
		}

		if (theStartDate != null || theEndDate != null) {
			dateRange = new DateRangeParam();
			if (theStartDate != null) {
				DateParam lowerBound = new DateParam();
				lowerBound.setPrefix(ca.uhn.fhir.rest.param.ParamPrefixEnum.GREATERTHAN_OR_EQUALS);
				lowerBound.setValue(theStartDate.getValue());
				dateRange.setLowerBound(lowerBound);
			}
			if (theEndDate != null) {
				DateParam upperBound = new DateParam();
				upperBound.setPrefix(ca.uhn.fhir.rest.param.ParamPrefixEnum.LESSTHAN_OR_EQUALS);
				upperBound.setValue(theEndDate.getValue());
				dateRange.setUpperBound(upperBound);
			}
		}

		// Perform search using service (same as $search-mapped)
		List<Observation> observations = observationService.searchObservations(
				subjectParam, thePatient, theCode, theCategory, dateRange, theStatus, theCount);

		// Map all observations to custom format (exactly same as $search-mapped)
		StringBuilder jsonResponse = new StringBuilder();
		jsonResponse.append("{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":")
				.append(observations.size())
				.append(",\"patient\":\"").append(thePatient != null ? thePatient.getValue() : "").append("\"")
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

		// Set response headers and status first (exactly same as $search-mapped)
		theResponse.setStatus(HttpServletResponse.SC_OK);
		theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
		theResponse.setCharacterEncoding("UTF-8");
		theResponse.setHeader("Cache-Control", "no-cache");

		// Write response and flush immediately (exactly same as $search-mapped)
		theResponse.getWriter().write(jsonResponse.toString());
		theResponse.getWriter().flush();
		theResponse.getWriter().close();

		logger.info("Patient observations response sent, {} observations found for patient {}", 
				observations.size(), thePatient != null ? thePatient.getValue() : "null");

	} catch (Exception e) {
		logger.error("Error in patient observations: ", e);
		try {
			if (!theResponse.isCommitted()) {
				theResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
				theResponse.getWriter().write("{\"error\":\"Internal server error\"}");
				theResponse.getWriter().flush();
			}
		} catch (IOException ioException) {
			logger.error("Error writing error response", ioException);
		}
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
			theResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
			theResponse.getWriter().write("{\"error\":\"Patient parameter is required\"}");
			theResponse.getWriter().flush();
			theResponse.getWriter().close();
			return;
		}

		// Set default period to 30 days if not provided
		int periodDays = thePeriodDays != null ? thePeriodDays.getValue().intValue() : 30;

		// Create date range for the period (last X days)
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

		// Set category to vital-signs if not provided
		TokenParam category = theCategory;
		if (category == null) {
			category = new TokenParam();
			category.setSystem("http://terminology.hl7.org/CodeSystem/observation-category");
			category.setValue("vital-signs");
		}

		// Create subject parameter
		ReferenceParam subjectParam = new ReferenceParam(thePatient.getValue());

		// Perform search using service (same as $search-mapped)
		List<Observation> observations = observationService.searchObservations(
				subjectParam, thePatient, theCode, category, dateRange, null, theCount);

		// Map all observations to custom format (exactly same as $search-mapped)
		StringBuilder jsonResponse = new StringBuilder();
		jsonResponse.append("{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":")
				.append(observations.size())
				.append(",\"patient\":\"").append(thePatient.getValue()).append("\"")
				.append(",\"period\":").append(periodDays)
				.append(",\"code\":\"").append(theCode != null ? theCode.getValue() : "all-vital-signs").append("\"")
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

		// Set response headers and status first (exactly same as $search-mapped)
		theResponse.setStatus(HttpServletResponse.SC_OK);
		theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
		theResponse.setCharacterEncoding("UTF-8");
		theResponse.setHeader("Cache-Control", "no-cache");

		// Write response and flush immediately (exactly same as $search-mapped)
		theResponse.getWriter().write(jsonResponse.toString());
		theResponse.getWriter().flush();
		theResponse.getWriter().close();

		logger.info("Vital signs trend response sent, {} observations found for patient {} over {} days", 
				observations.size(), thePatient.getValue(), periodDays);

	} catch (Exception e) {
		logger.error("Error in vital signs trend: ", e);
		try {
			if (!theResponse.isCommitted()) {
				theResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
				theResponse.getWriter().write("{\"error\":\"Internal server error\"}");
				theResponse.getWriter().flush();
			}
		} catch (IOException ioException) {
			logger.error("Error writing error response", ioException);
		}
	}
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