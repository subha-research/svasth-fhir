package com.svasamm.fhir.ehr.provider;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.svasamm.fhir.config.HospitalConfig;
import com.svasamm.fhir.ehr.dto.medication.MedicationDto;
import com.svasamm.fhir.ehr.mapper.MedicationMapper;
import com.svasamm.fhir.ehr.service.MedicationService;

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
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Custom Medication Resource Provider that extends HAPI's JPA provider
 * This preserves all JPA functionality (versioning, locking, etc.) while adding
 * custom logic and mapper operations
 */
public class MedicationResourceProvider extends BaseJpaResourceProvider<Medication> {

	private static final Logger logger = LoggerFactory.getLogger(MedicationResourceProvider.class);

	@Autowired
	private MedicationService medicationService;

	@Autowired
	private MedicationMapper medicationMapper;

	@Autowired
	private HospitalConfig hospitalConfig;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public MedicationResourceProvider(IFhirResourceDao<Medication> theDao) {
		super(theDao);
		logger.info("🚀 CUSTOM MedicationResourceProvider CONSTRUCTOR CALLED with JPA DAO 🚀");
	}

	@Override
	public Class<Medication> getResourceType() {
		logger.info("CUSTOM MedicationResourceProvider.getResourceType() called");
		return Medication.class;
	}

	@Create
	public MethodOutcome create(@ResourceParam Medication theMedication, RequestDetails theRequestDetails) {
		logger.error("Custom MedicationResourceProvider.create() called");
		logger.error("Medication code: {}",
				theMedication.getCode() != null && !theMedication.getCode().getCoding().isEmpty()
						? theMedication.getCode().getCodingFirstRep().getDisplay()
						: "No code");

		try {
			// Apply custom EHR enrichment
			enrichMedicationForEHR(theMedication);

			// Use DAO directly instead of super.create()
			MethodOutcome result = getDao().create(theMedication, theRequestDetails);

			logger.error("Medication created with ID: {} ", result.getId());
			return result;

		} catch (Exception e) {
			logger.error("Error in custom create medication provider: ", e);
			throw e;
		}
	}

	@Update
	public MethodOutcome update(
			HttpServletRequest theRequest,
			@IdParam IdType theId,
			@ResourceParam Medication theMedication,
			@ConditionalUrlParam String theConditional,
			RequestDetails theRequestDetails) {

		logger.info("Custom MedicationResourceProvider.update() called for ID: {}", theId);

		try {
			// Apply custom EHR enrichment
			enrichMedicationForEHR(theMedication);

			// Call parent JPA implementation - this handles optimistic locking
			// automatically
			MethodOutcome result = super.update(theRequest, theMedication, theId, theConditional, theRequestDetails);

			// Custom post-processing
			if (medicationService != null) {
				// Add any additional custom logic here
				logger.info("Medication updated successfully with ID: {}", result.getId());
			}

			return result;

		} catch (Exception e) {
			logger.error("Error in CUSTOM update: ", e);
			throw e;
		}
	}

	@Read
	public Medication read(HttpServletRequest theRequest, @IdParam IdType theId, RequestDetails theRequestDetails) {
		logger.info("CUSTOM MedicationResourceProvider.read() called for ID: {}", theId);

		try {
			// Use the parent JPA implementation for reading
			Medication medication = super.read(theRequest, theId, theRequestDetails);

			// Add any custom post-read processing if needed
			if (medicationService != null) {
				// Custom logic after reading
				logger.debug("Medication read successfully: {}", theId);
			}

			return medication;

		} catch (Exception e) {
			logger.error("Error reading medication {}: ", theId, e);
			throw new ResourceNotFoundException(theId);
		}
	}

	/**
	 * Custom operation to get medication in mapped format
	 * Usage: GET /Medication/{id}/$mapped
	 */
	@Operation(name = "$mapped", idempotent = true, type = Medication.class)
	public void getMappedMedication(
			@IdParam IdType theId,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom MedicationResourceProvider.getMappedMedication() called for ID: {}", theId);

		try {
			// Get the medication using service
			Medication medication = medicationService.getMedicationById(theId.getIdPart());

			if (medication == null) {
				sendErrorResponse(theResponse, HttpServletResponse.SC_NOT_FOUND, "Medication not found");
				return;
			}

			// Map to custom format
			MedicationDto mappedMedication = medicationMapper.mapToDTO(medication);
			String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mappedMedication);

			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Mapped medication response sent for ID: {}", theId);

		} catch (Exception e) {
			logger.error("Error getting mapped medication {}: ", theId, e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	/**
	 * Custom operation to search medications and return them in mapped format
	 * Usage: GET /Medication/$search-mapped?code=123&form=tablet&status=active
	 */
	@Operation(name = "$search-mapped", idempotent = true, type = Medication.class)
	public void searchMappedMedications(
			@OperationParam(name = "manufacturer") ReferenceParam theManufacturer,
			@OperationParam(name = "ingredient") ReferenceParam theIngredient,
			@OperationParam(name = "code") TokenParam theCode,
			@OperationParam(name = "identifier") TokenParam theIdentifier,
			@OperationParam(name = "form") TokenParam theForm,
			@OperationParam(name = "status") TokenParam theStatus,
			@OperationParam(name = "_count") NumberParam theCount,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom MedicationResourceProvider.searchMappedMedications() called");

		try {
			// Perform search using service
			List<Medication> medications = medicationService.searchMedications(
					theManufacturer, theIngredient, theCode, theIdentifier, theForm, theStatus, theCount);

			// Build bundle response
			String jsonResponse = buildMedicationBundleResponse(medications, "searchset", null);
			
			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Mapped medications search response sent, {} medications found", medications.size());

		} catch (Exception e) {
			logger.error("Error in search mapped medications: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	/**
	 * Custom operation to get patient medications in mapped format
	 * Usage: GET /Medication/$patient-mapped?patient=Patient/123&category=prescription&start-date=2023-01-01
	 */
	@Operation(name = "$patient-mapped", idempotent = true, type = Medication.class)
	public void getPatientMappedMedications(
			@OperationParam(name = "patient") ReferenceParam thePatient,
			@OperationParam(name = "category") TokenParam theCategory,
			@OperationParam(name = "code") TokenParam theCode,
			@OperationParam(name = "start-date") DateParam theStartDate,
			@OperationParam(name = "end-date") DateParam theEndDate,
			@OperationParam(name = "_count") NumberParam theCount,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom MedicationResourceProvider.getPatientMappedMedications() called for patient: {}",
				thePatient != null ? thePatient.getValue() : "null");

		try {
			if (thePatient == null) {
				sendErrorResponse(theResponse, HttpServletResponse.SC_BAD_REQUEST, "Patient parameter is required");
				return;
			}

			// Use search for medications (simplified for demo - in real implementation you'd filter by patient)
			List<Medication> medications = searchByParameters(
					buildSearchParams(null, null, theCode, null, null, null, theCount));

			// Apply count limit if specified
			if (theCount != null && theCount.getValue() != null) {
				int limit = theCount.getValue().intValue();
				if (medications.size() > limit) {
					medications = medications.subList(0, limit);
				}
			}

			// Build bundle response with patient info
			String jsonResponse = buildMedicationBundleResponse(medications, "searchset", thePatient.getValue());
			
			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Mapped patient medications response sent, {} medications found for patient {}",
					medications.size(), thePatient.getValue());

		} catch (Exception e) {
			logger.error("Error in get patient mapped medications: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	/**
	 * Custom operation to get medication summary in mapped format
	 * Usage: GET /Medication/{id}/$medication-summary
	 */
	@Operation(name = "$medication-summary", idempotent = true, type = Medication.class)
	public void medicationSummary(
			@IdParam IdType theMedicationId,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom MedicationResourceProvider.medicationSummary() called for ID: {}", theMedicationId);

		try {
			// Get the medication using service
			Medication medication = medicationService.getMedicationById(theMedicationId.getIdPart());

			if (medication == null) {
				sendErrorResponse(theResponse, HttpServletResponse.SC_NOT_FOUND, "Medication not found");
				return;
			}

			// Create single medication list for bundle response
			List<Medication> medications = List.of(medication);
			
			// Build collection bundle response
			String jsonResponse = buildMedicationBundleResponse(medications, "collection", null, 
					"medicationId", theMedicationId.getIdPart());
			
			sendSuccessResponse(theResponse, jsonResponse);
			logger.info("Medication summary response sent for ID: {}", theMedicationId);

		} catch (Exception e) {
			logger.error("Error in medication summary: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	/**
	 * Custom operation to get patient medications (standard FHIR Bundle)
	 * Usage: GET /Medication/$patient-medications?patient=Patient/123&category=prescription
	 */
	@Operation(name = "$patient-medications", idempotent = true, type = Medication.class)
	public void patientMedications(
			@OperationParam(name = "patient") ReferenceParam thePatient,
			@OperationParam(name = "category") TokenParam theCategory,
			@OperationParam(name = "start-date") DateParam theStartDate,
			@OperationParam(name = "end-date") DateParam theEndDate,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Patient medications operation called for patient: {}", 
				thePatient != null ? thePatient.getValue() : "null");

		try {
			if (thePatient == null) {
				sendErrorResponse(theResponse, HttpServletResponse.SC_BAD_REQUEST, "Patient parameter is required");
				return;
			}

			// Search for medications using existing search functionality
			List<Medication> medications = searchByParameters(
					buildSearchParams(null, null, null, null, null, null, null));

			// Build FHIR Bundle response
			String jsonResponse = buildFhirBundleResponse(medications, thePatient.getValue());
			
			sendFhirResponse(theResponse, jsonResponse);
			logger.info("Patient medications response sent for patient: {}", thePatient.getValue());

		} catch (Exception e) {
			logger.error("Error in patient medications operation: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
					"Failed to get patient medications: " + e.getMessage());
		}
	}

	/**
	 * Custom operation to get medication interactions in mapped format
	 * Usage: GET /Medication/$medication-interactions?patient=Patient/123&medication=aspirin&period=30
	 */
	@Operation(name = "$medication-interactions", idempotent = true, type = Medication.class)
	public void medicationInteractions(
			@OperationParam(name = "patient", min = 1) ReferenceParam thePatient,
			@OperationParam(name = "medication") TokenParam theMedication,
			@OperationParam(name = "period") NumberParam thePeriodDays,
			@OperationParam(name = "_count") NumberParam theCount,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {

		logger.info("Custom MedicationResourceProvider.medicationInteractions() called");

		try {
			if (thePatient == null) {
				sendErrorResponse(theResponse, HttpServletResponse.SC_BAD_REQUEST, "Patient parameter is required");
				return;
			}

			int periodDays = thePeriodDays != null ? thePeriodDays.getValue().intValue() : 30;

			// Search for medications (simplified - get all medications for demo)
			List<Medication> medications = searchByParameters(
					buildSearchParams(null, null, theMedication, null, null, null, theCount));

			// Build FHIR Bundle response for interactions
			String jsonResponse = buildMedicationInteractionsResponse(medications, thePatient.getValue(), periodDays);
			
			sendFhirResponse(theResponse, jsonResponse);
			logger.info("Medication interactions response sent for patient: {}", thePatient.getValue());

		} catch (Exception e) {
			logger.error("Error in medication interactions: ", e);
			sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
		}
	}

	@Search
	public List<Medication> search(
			@OptionalParam(name = Medication.SP_MANUFACTURER) ReferenceParam theManufacturer,
			@OptionalParam(name = Medication.SP_INGREDIENT) ReferenceParam theIngredient,
			@OptionalParam(name = Medication.SP_CODE) TokenParam theCode,
			@OptionalParam(name = Medication.SP_IDENTIFIER) TokenParam theIdentifier,
			@OptionalParam(name = Medication.SP_FORM) TokenParam theForm,
			@OptionalParam(name = Medication.SP_STATUS) TokenParam theStatus,
			@OptionalParam(name = "_count") NumberParam theCount) {

		// If you have custom search logic, use your service
		if (medicationService != null) {
			return medicationService.searchMedications(theManufacturer, theIngredient, theCode,
					theIdentifier, theForm, theStatus, theCount);
		}

		// Otherwise, fall back to default JPA search
		return searchByParameters(
				buildSearchParams(theManufacturer, theIngredient, theCode, theIdentifier, theForm, theStatus,
						theCount));
	}

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
	 * Common method to build bundle response from medications list (mapped format)
	 */
	private String buildMedicationBundleResponse(List<Medication> medications, String bundleType, String patientValue) {
		return buildMedicationBundleResponse(medications, bundleType, patientValue, null, null);
	}

	/**
	 * Common method to build bundle response with additional metadata (mapped format)
	 */
	private String buildMedicationBundleResponse(List<Medication> medications, String bundleType, String patientValue, 
			String additionalKey, String additionalValue) {
		try {
			StringBuilder jsonResponse = new StringBuilder();
			jsonResponse.append("{\"resourceType\":\"Bundle\",\"type\":\"").append(bundleType).append("\",\"total\":")
					.append(medications.size());

			// Add patient info if provided
			if (patientValue != null && !patientValue.isEmpty()) {
				jsonResponse.append(",\"patient\":\"").append(patientValue).append("\"");
			}

			// Add additional metadata if provided
			if (additionalKey != null && additionalValue != null) {
				jsonResponse.append(",\"").append(additionalKey).append("\":\"").append(additionalValue).append("\"");
			}

			jsonResponse.append(",\"entry\":[");

			// Add all medications (mapped format)
			for (int i = 0; i < medications.size(); i++) {
				if (i > 0) {
					jsonResponse.append(",");
				}
				MedicationDto mappedMedication = medicationMapper.mapToDTO(medications.get(i));
				String medicationJson = objectMapper.writeValueAsString(mappedMedication);
				jsonResponse.append("{\"resource\":")
						.append(medicationJson)
						.append("}");
			}
			jsonResponse.append("]}");

			return jsonResponse.toString();
		} catch (Exception e) {
			logger.error("Error building medication bundle response", e);
			return "{\"error\":\"Error building response\"}";
		}
	}

	/**
	 * Common method to build FHIR Bundle response (standard FHIR format)
	 */
	private String buildFhirBundleResponse(List<Medication> medications, String patientValue) {
		try {
			// Create FHIR Bundle
			Bundle medicationsBundle = new Bundle();
			medicationsBundle.setType(Bundle.BundleType.SEARCHSET);
			medicationsBundle.setTotal(medications.size());

			// Add medications to bundle
			for (Medication medication : medications) {
				Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
				entry.setResource(medication);
				entry.setFullUrl("Medication/" + medication.getId());
				medicationsBundle.addEntry(entry);
			}

			// Convert bundle to JSON
			ca.uhn.fhir.context.FhirContext ctx = ca.uhn.fhir.context.FhirContext.forR4();
			return ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(medicationsBundle);
		} catch (Exception e) {
			logger.error("Error building FHIR bundle response", e);
			return "{\"error\":\"Error building FHIR response\"}";
		}
	}

	/**
	 * Specialized method to build medication interactions FHIR response
	 */
	private String buildMedicationInteractionsResponse(List<Medication> medications, String patientValue, int periodDays) {
		try {
			// Create interactions bundle (simplified - showing medications as potential interactions)
			Bundle interactionsBundle = new Bundle();
			interactionsBundle.setType(Bundle.BundleType.SEARCHSET);
			interactionsBundle.setTotal(medications.size());

			// Add medications to bundle as potential interactions
			for (Medication medication : medications) {
				Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
				entry.setResource(medication);
				entry.setFullUrl("Medication/" + medication.getId());
				interactionsBundle.addEntry(entry);
			}

			// Convert bundle to JSON
			ca.uhn.fhir.context.FhirContext ctx = ca.uhn.fhir.context.FhirContext.forR4();
			return ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(interactionsBundle);
		} catch (Exception e) {
			logger.error("Error building medication interactions response", e);
			return "{\"error\":\"Error building interactions response\"}";
		}
	}

	private void enrichMedicationForEHR(Medication medication) {
		// Set default status if not present
		if (medication.getStatus() == null) {
			medication.setStatus(Medication.MedicationStatus.ACTIVE);
		}

		// Add EHR metadata
		if (medication.getMeta() == null) {
			medication.setMeta(new Meta());
		}
		medication.getMeta()
				.addProfile("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/ehr-medication")
				.addTag()
				.setSystem("http://" + hospitalConfig.getIdentifier() + "/fhir/CodeSystem/data-source")
				.setCode("ehr")
				.setDisplay("Electronic Health Record");

		// Add audit extension
		Extension auditExtension = new Extension();
		auditExtension.setUrl("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/audit-trail");
		auditExtension.addExtension("created-date", new DateTimeType(new Date()));
		auditExtension.addExtension("created-by", new StringType("EHR System"));
		medication.addExtension(auditExtension);
	}

	// Helper method to build search parameters
	private List<Medication> searchByParameters(ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams) {
		try {
			return getDao().search(searchParams).getResources(0, 100)
					.stream()
					.map(resource -> (Medication) resource)
					.collect(java.util.stream.Collectors.toList());
		} catch (Exception e) {
			logger.error("Error in search: ", e);
			return java.util.Collections.emptyList();
		}
	}

	private ca.uhn.fhir.jpa.searchparam.SearchParameterMap buildSearchParams(
			ReferenceParam theManufacturer, ReferenceParam theIngredient, TokenParam theCode,
			TokenParam theIdentifier, TokenParam theForm, TokenParam theStatus, NumberParam theCount) {

		ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = new ca.uhn.fhir.jpa.searchparam.SearchParameterMap();

		if (theManufacturer != null) {
			searchParams.add(Medication.SP_MANUFACTURER, theManufacturer);
		}
		if (theIngredient != null) {
			searchParams.add(Medication.SP_INGREDIENT, theIngredient);
		}
		if (theCode != null) {
			searchParams.add(Medication.SP_CODE, theCode);
		}
		if (theIdentifier != null) {
			searchParams.add(Medication.SP_IDENTIFIER, theIdentifier);
		}
		if (theForm != null) {
			searchParams.add(Medication.SP_FORM, theForm);
		}
		if (theStatus != null) {
			searchParams.add(Medication.SP_STATUS, theStatus);
		}

		return searchParams;
	}
}