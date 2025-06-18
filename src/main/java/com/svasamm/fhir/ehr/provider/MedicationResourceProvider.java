package com.svasamm.fhir.ehr.provider;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

import com.svasamm.fhir.ehr.service.MedicationService;
import com.svasamm.fhir.config.HospitalConfig;
import org.hl7.fhir.r4.model.*;

import com.svasamm.fhir.ehr.dto.medication.MedicationDto;
import com.svasamm.fhir.ehr.mapper.MedicationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;

/**
 * Custom Medication Resource Provider that extends HAPI's JPA provider
 * This preserves all JPA functionality (versioning, locking, etc.) while adding
 * custom logic
 */
public class MedicationResourceProvider extends BaseJpaResourceProvider<Medication> {

	private static final Logger logger = LoggerFactory.getLogger(MedicationResourceProvider.class);

	@Autowired
	private MedicationService medicationService;

	@Autowired
	private MedicationMapper medicationMapper;

	@Autowired
	private HospitalConfig hospitalConfig;

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

	@Operation(name = "$medication-summary", idempotent = true)
	public Bundle medicationSummary(@IdParam IdType theMedicationId) {
		if (medicationService != null) {
			return medicationService.generateMedicationSummary(theMedicationId.getIdPart());
		}
		throw new UnsupportedOperationException("Medication service not available");
	}

	@Operation(name = "$patient-medications", idempotent = true)
	public Bundle patientMedications(
			@IdParam IdType thePatientId,
			@OperationParam(name = "category") TokenParam category,
			@OperationParam(name = "start-date") DateParam startDate,
			@OperationParam(name = "end-date") DateParam endDate) {

		if (medicationService != null) {
			return medicationService.getPatientMedications(
					thePatientId.getIdPart(),
					category,
					startDate != null ? startDate.getValue() : null,
					endDate != null ? endDate.getValue() : null);
		}
		throw new UnsupportedOperationException("Medication service not available");
	}

	@Operation(name = "$medication-interactions", idempotent = true)
	public Bundle medicationInteractions(
			@OperationParam(name = "patient", min = 1) ReferenceParam patient,
			@OperationParam(name = "medication") TokenParam medication,
			@OperationParam(name = "period") NumberParam periodDays) {

		if (medicationService != null) {
			return medicationService.getMedicationInteractions(
					patient.getValue(),
					medication,
					periodDays != null ? periodDays.getValue().intValue() : 30);
		}
		throw new UnsupportedOperationException("Medication service not available");
	}

	// @Operation(name = "$get-dto", idempotent = true)
	// public MedicationDto getMedicationDTO(@IdParam IdType theMedicationId) {
	// if (medicationMapper != null) {
	// try {
	// Medication medication = getDao().read(theMedicationId);
	// return medicationMapper.mapToDTO(medication);
	// } catch (Exception e) {
	// logger.error("Error mapping medication to DTO: ", e);
	// throw new RuntimeException("Failed to map medication: " + e.getMessage());
	// }
	// }
	// throw new UnsupportedOperationException("Medication mapper not available");
	// }

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