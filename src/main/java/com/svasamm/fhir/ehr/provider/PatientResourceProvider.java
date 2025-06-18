package com.svasamm.fhir.ehr.provider;

import java.util.Date;
import java.util.List;
import java.util.Random;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.svasamm.fhir.config.HospitalConfig;
import com.svasamm.fhir.ehr.service.PatientService;
import com.svasamm.fhir.exception.Exception;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao; // Your custom exception
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.annotation.ConditionalUrlParam;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import jakarta.servlet.http.HttpServletRequest;

public class PatientResourceProvider extends BaseJpaResourceProvider<Patient> {

	private static final Logger logger = LoggerFactory.getLogger(PatientResourceProvider.class);

	@Autowired
	private PatientService patientService;

	@Autowired
	private HospitalConfig hospitalConfig;

	public PatientResourceProvider(IFhirResourceDao<Patient> theDao) {
		super(theDao);
		logger.info("🚀 CUSTOM PatientResourceProvider CONSTRUCTOR CALLED with JPA DAO 🚀");
	}

	@Override
	public Class<Patient> getResourceType() {
		logger.info("CUSTOM PatientResourceProvider.getResourceType() called");
		return Patient.class;
	}

	@Create
	public MethodOutcome create(@ResourceParam Patient thePatient, RequestDetails theRequestDetails) {
		logger.error("Custom PatientResourceProvider.create() called");

		// JWT is already validated by interceptor - just log success
		logJwtSuccess("CREATE");

		// Validate patient data before creation
		validatePatientData(thePatient);

		logger.error("Patient name: {}",
				thePatient.getName().isEmpty() ? "No name" : thePatient.getName().get(0).getFamily());

		try {
			enrichPatientForEHR(thePatient);
			MethodOutcome result = getDao().create(thePatient, theRequestDetails);
			logger.error("Patient created with ID: {} ", result.getId());
			return result;

		} catch (java.lang.Exception e) {
			logger.error("Error in custom create patient provider: ", e);
			// Convert generic exceptions to our custom exception
			throw Exception.internalError("Failed to create patient: " + e.getMessage(), e);
		}
	}

	@Update
	public MethodOutcome update(
			HttpServletRequest theRequest,
			@IdParam IdType theId,
			@ResourceParam Patient thePatient,
			@ConditionalUrlParam String theConditional,
			RequestDetails theRequestDetails) {

		logger.info("Custom PatientResourceProvider.update() called for ID: {}", theId);

		// JWT is already validated by interceptor - just log success
		logJwtSuccess("UPDATE");

		// Validate the ID
		if (theId == null || theId.getIdPart() == null || theId.getIdPart().trim().isEmpty()) {
			throw new Exception("Patient ID is required for update operation");
		}

		// Validate patient data
		validatePatientData(thePatient);

		try {
			// Check if patient exists first
			Patient existingPatient = super.read(theRequest, theId, theRequestDetails);
			if (existingPatient == null) {
				throw Exception.notFound("Patient", theId.getIdPart());
			}

			enrichPatientForEHR(thePatient);
			MethodOutcome result = super.update(theRequest, thePatient, theId, theConditional, theRequestDetails);

			if (patientService != null) {
				logger.info("Patient updated successfully with ID: {}", result.getId());
			}

			return result;

		} catch (Exception e) {
			// Re-throw our custom exceptions as-is
			throw e;
		} catch (java.lang.Exception e) {
			logger.error("Error in CUSTOM update: ", e);
			throw Exception.internalError("Failed to update patient with ID " + theId.getIdPart(), e);
		}
	}

	@Read
	public Patient read(HttpServletRequest theRequest, @IdParam IdType theId, RequestDetails theRequestDetails) {
		logger.info("CUSTOM PatientResourceProvider.read() called for ID: {}", theId);

		// JWT is already validated by interceptor - just log success
		logJwtSuccess("READ");

		// Validate ID parameter
		if (theId == null || theId.getIdPart() == null || theId.getIdPart().trim().isEmpty()) {
			throw Exception.badRequest("Patient ID cannot be empty");
		}

		try {
			Patient patient = super.read(theRequest, theId, theRequestDetails);

			if (patient == null) {
				throw Exception.notFound("Patient", theId.getIdPart());
			}

			if (patientService != null) {
				logger.debug("Patient read successfully: {}", theId);
			}

			return patient;

		} catch (Exception e) {
			// Re-throw our custom exceptions
			throw e;
		} catch (java.lang.Exception e) {
			logger.error("Error reading patient {}: ", theId, e);
			throw Exception.notFound("Patient", theId.getIdPart());
		}
	}

	@Search
	public List<Patient> search(
			@OptionalParam(name = Patient.SP_FAMILY) StringParam theFamily,
			@OptionalParam(name = Patient.SP_GIVEN) StringParam theGiven,
			@OptionalParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier,
			@OptionalParam(name = Patient.SP_BIRTHDATE) DateParam theBirthDate,
			@OptionalParam(name = Patient.SP_ACTIVE) TokenParam theActive,
			@OptionalParam(name = "_count") NumberParam theCount,
			RequestDetails theRequestDetails) {

		// JWT is already validated by interceptor - just log success
		logJwtSuccess("SEARCH");

		// Validate search parameters
		validateSearchParameters(theFamily, theGiven, theIdentifier, theBirthDate, theActive, theCount);

		try {
			if (patientService != null) {
				return patientService.searchPatients(theFamily, theGiven, theIdentifier,
						theBirthDate, theActive, theCount);
			}

			return searchByParameters(
					buildSearchParams(theFamily, theGiven, theIdentifier, theBirthDate, theActive, theCount));
		} catch (Exception e) {
			// Re-throw our custom exceptions
			throw e;
		} catch (java.lang.Exception e) {
			logger.error("Error in patient search: ", e);
			throw Exception.internalError("Failed to search patients", e);
		}
	}

	@Operation(name = "$patient-summary", idempotent = true)
	public Bundle patientSummary(@IdParam IdType thePatientId, RequestDetails theRequestDetails) {

		// JWT is already validated by interceptor - just log success
		logJwtSuccess("SUMMARY");

		// Validate patient ID
		if (thePatientId == null || thePatientId.getIdPart() == null || thePatientId.getIdPart().trim().isEmpty()) {
			throw Exception.badRequest("Patient ID is required for summary operation");
		}

		try {
			if (patientService != null) {
				Bundle summary = patientService.generatePatientSummary(thePatientId.getIdPart());
				if (summary == null) {
					throw Exception.notFound("Patient", thePatientId.getIdPart());
				}
				return summary;
			}
			throw new Exception("Patient service is not available at this time");
		} catch (Exception e) {
			// Re-throw our custom exceptions
			throw e;
		} catch (java.lang.Exception e) {
			logger.error("Error generating patient summary for ID {}: ", thePatientId, e);
			throw Exception.internalError("Failed to generate patient summary", e);
		}
	}

	// Validation methods
	private void validatePatientData(Patient patient) {
		if (patient == null) {
			throw Exception.badRequest("Patient data cannot be null");
		}

		List<String> validationErrors = new java.util.ArrayList<>();

		// Check if patient has at least one name
		if (patient.getName() == null || patient.getName().isEmpty()) {
			validationErrors.add("Patient must have at least one name");
		} else {
			// Check if name has family or given name
			boolean hasValidName = patient.getName().stream()
					.anyMatch(name -> (name.getFamily() != null && !name.getFamily().trim().isEmpty()) ||
							(name.getGiven() != null && !name.getGiven().isEmpty()));

			if (!hasValidName) {
				validationErrors.add("Patient name must include either family name or given name");
			}
		}

		// Check birth date format if provided
		if (patient.getBirthDate() != null) {
			Date currentDate = new Date();
			if (patient.getBirthDate().after(currentDate)) {
				validationErrors.add("Birth date cannot be in the future");
			}
		}

		// Check gender if provided
		if (patient.getGender() != null && patient.getGender().toCode() == null) {
			validationErrors.add("Invalid gender value provided");
		}

		// Check identifiers format
		if (patient.getIdentifier() != null && !patient.getIdentifier().isEmpty()) {
			for (int i = 0; i < patient.getIdentifier().size(); i++) {
				Identifier id = patient.getIdentifier().get(i);
				if (id.getValue() == null || id.getValue().trim().isEmpty()) {
					validationErrors.add("Identifier at position " + i + " must have a value");
				}
			}
		}

		// If we have validation errors, throw them
		if (!validationErrors.isEmpty()) {
			throw Exception.validationError("Patient validation failed", validationErrors);
		}
	}

	private void validateSearchParameters(StringParam theFamily, StringParam theGiven,
			TokenParam theIdentifier, DateParam theBirthDate, TokenParam theActive,
			NumberParam theCount) {

		// Validate count parameter
		if (theCount != null && theCount.getValue() != null) {
			if (theCount.getValue().intValue() < 0) {
				throw Exception.badRequest("Count parameter cannot be negative");
			}
			if (theCount.getValue().intValue() > 1000) {
				throw Exception.badRequest("Count parameter cannot exceed 1000");
			}
		}

		// Validate birth date parameter
		if (theBirthDate != null && theBirthDate.getValue() != null) {
			Date currentDate = new Date();
			if (theBirthDate.getValue().after(currentDate)) {
				throw Exception.badRequest("Birth date search parameter cannot be in the future");
			}
		}

		// Ensure at least one search parameter is provided
		if (theFamily == null && theGiven == null && theIdentifier == null &&
				theBirthDate == null && theActive == null) {
			throw Exception.badRequest("At least one search parameter must be provided");
		}
	}

	// Simple logging method to confirm JWT was validated
	private void logJwtSuccess(String operation) {
		logger.info("🔐 JWT validated successfully for {} operation", operation);
	}

	// Keep all your existing business logic methods
	private void enrichPatientForEHR(Patient patient) {
		try {
			boolean hasMRN = patient.getIdentifier().stream()
					.anyMatch(id -> "MR".equals(id.getType().getCodingFirstRep().getCode()));

			if (!hasMRN) {
				Identifier mrn = new Identifier();
				mrn.setSystem("urn:mrn:" + hospitalConfig.getIdentifier());
				mrn.setValue(generateMRN());
				mrn.getType().addCoding()
						.setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
						.setCode("MR")
						.setDisplay("Medical Record Number");
				mrn.setUse(Identifier.IdentifierUse.USUAL);

				patient.addIdentifier(mrn);
			}

			if (patient.getManagingOrganization() == null) {
				patient.setManagingOrganization(
						new Reference("Organization/" + hospitalConfig.getIdentifier())
								.setDisplay(hospitalConfig.getName()));
			}

			if (patient.getMeta() == null) {
				patient.setMeta(new Meta());
			}
			patient.getMeta()
					.addProfile("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/ehr-patient")
					.addTag()
					.setSystem("http://" + hospitalConfig.getIdentifier() + "/fhir/CodeSystem/data-source")
					.setCode("ehr")
					.setDisplay("Electronic Health Record");

			Extension auditExtension = new Extension();
			auditExtension.setUrl("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/audit-trail");
			auditExtension.addExtension("created-date", new DateTimeType(new Date()));
			auditExtension.addExtension("created-by", new StringType("EHR System"));
			patient.addExtension(auditExtension);
		} catch (java.lang.Exception e) {
			logger.error("Error enriching patient data: ", e);
			throw Exception.internalError("Failed to enrich patient data for EHR", e);
		}
	}

	private String generateMRN() {
		try {
			return hospitalConfig.getIdentifier() + "-MRN-" + System.currentTimeMillis()
					+ String.format("%03d", new Random().nextInt(1000));
		} catch (java.lang.Exception e) {
			logger.error("Error generating MRN: ", e);
			throw Exception.internalError("Failed to generate Medical Record Number", e);
		}
	}

	private List<Patient> searchByParameters(ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams) {
		try {
			return getDao().search(searchParams).getResources(0, 100)
					.stream()
					.map(resource -> (Patient) resource)
					.collect(java.util.stream.Collectors.toList());
		} catch (java.lang.Exception e) {
			logger.error("Error in search: ", e);
			throw Exception.internalError("Database search operation failed", e);
		}
	}

	private ca.uhn.fhir.jpa.searchparam.SearchParameterMap buildSearchParams(
			StringParam theFamily, StringParam theGiven, TokenParam theIdentifier,
			DateParam theBirthDate, TokenParam theActive, NumberParam theCount) {

		try {
			ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = new ca.uhn.fhir.jpa.searchparam.SearchParameterMap();

			if (theFamily != null) {
				searchParams.add(Patient.SP_FAMILY, theFamily);
			}
			if (theGiven != null) {
				searchParams.add(Patient.SP_GIVEN, theGiven);
			}
			if (theIdentifier != null) {
				searchParams.add(Patient.SP_IDENTIFIER, theIdentifier);
			}
			if (theBirthDate != null) {
				searchParams.add(Patient.SP_BIRTHDATE, theBirthDate);
			}
			if (theActive != null) {
				searchParams.add(Patient.SP_ACTIVE, theActive);
			}

			return searchParams;
		} catch (java.lang.Exception e) {
			logger.error("Error building search parameters: ", e);
			throw Exception.internalError("Failed to build search parameters", e);
		}
	}
}