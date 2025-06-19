package com.svasamm.fhir.ehr.provider;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

import com.svasamm.fhir.ehr.service.LabResultsService;
import com.svasamm.fhir.config.HospitalConfig;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;

/**
 * Custom Lab Results Resource Provider that extends HAPI's JPA provider
 * Lab Results are implemented as Observations with laboratory category
 */
public class LabResultsResourceProvider extends BaseJpaResourceProvider<Observation> {

	private static final Logger logger = LoggerFactory.getLogger(LabResultsResourceProvider.class);

	@Autowired
	private LabResultsService labResultsService;

	@Autowired
	private HospitalConfig hospitalConfig;

	public LabResultsResourceProvider(IFhirResourceDao<Observation> theDao) {
		super(theDao);
		logger.info("🚀 CUSTOM LabResultsResourceProvider CONSTRUCTOR CALLED with JPA DAO 🚀");
	}

	@Override
	public Class<Observation> getResourceType() {
		logger.info("CUSTOM LabResultsResourceProvider.getResourceType() called");
		return Observation.class;
	}

	@Create
	public MethodOutcome create(@ResourceParam Observation theLabResult, RequestDetails theRequestDetails) {
		logger.error("Custom LabResultsResourceProvider.create() called");
		logger.error("Lab Result code: {}", theLabResult.getCode().getCodingFirstRep().getDisplay());

		try {
			// Apply lab results specific enrichment
			enrichLabResultForEHR(theLabResult);

			// Use DAO directly instead of super.create()
			MethodOutcome result = getDao().create(theLabResult, theRequestDetails);

			logger.error("Lab Result created with ID: {} ", result.getId());
			return result;

		} catch (Exception e) {
			logger.error("Error in custom create lab result provider: ", e);
			throw e;
		}
	}

	@Update
	public MethodOutcome update(
			HttpServletRequest theRequest,
			@IdParam IdType theId,
			@ResourceParam Observation theLabResult,
			@ConditionalUrlParam String theConditional,
			RequestDetails theRequestDetails) {

		logger.info("Custom LabResultsResourceProvider.update() called for ID: {}", theId);

		try {
			// Apply lab results specific enrichment
			enrichLabResultForEHR(theLabResult);

			// Call parent JPA implementation - this handles optimistic locking
			// automatically
			MethodOutcome result = super.update(theRequest, theLabResult, theId, theConditional, theRequestDetails);

			// Custom post-processing
			if (labResultsService != null) {
				// Add any additional custom logic here
				logger.info("Lab Result updated successfully with ID: {}", result.getId());
			}

			return result;

		} catch (Exception e) {
			logger.error("Error in CUSTOM update: ", e);
			throw e;
		}
	}

	@Read
	public Observation read(HttpServletRequest theRequest, @IdParam IdType theId, RequestDetails theRequestDetails) {
		logger.info("CUSTOM LabResultsResourceProvider.read() called for ID: {}", theId);

		try {
			// Use the parent JPA implementation for reading
			Observation labResult = super.read(theRequest, theId, theRequestDetails);

			// Verify it's actually a lab result
			if (labResultsService != null && !labResultsService.isLabResult(labResult)) {
				throw new ResourceNotFoundException("Resource is not a lab result: " + theId);
			}

			return labResult;

		} catch (Exception e) {
			logger.error("Error reading lab result {}: ", theId, e);
			throw new ResourceNotFoundException(theId);
		}
	}

	@Search
	public List<Observation> search(
			@OptionalParam(name = Observation.SP_SUBJECT) ReferenceParam theSubject,
			@OptionalParam(name = Observation.SP_PATIENT) ReferenceParam thePatient,
			@OptionalParam(name = Observation.SP_CODE) TokenParam theCode,
			@OptionalParam(name = Observation.SP_DATE) DateRangeParam theDate,
			@OptionalParam(name = Observation.SP_STATUS) TokenParam theStatus,
			@OptionalParam(name = Observation.SP_ENCOUNTER) ReferenceParam theEncounter,
			@OptionalParam(name = Observation.SP_VALUE_QUANTITY) QuantityParam theValueQuantity,
			@OptionalParam(name = "_count") NumberParam theCount) {

		// Always filter for laboratory category
		if (labResultsService != null) {
			return labResultsService.searchLabResults(theSubject, thePatient, theCode,
					theDate, theStatus, theEncounter, theValueQuantity, theCount);
		}

		// Otherwise, fall back to default JPA search with lab filter
		return searchByParameters(buildSearchParams(theSubject, thePatient, theCode, theDate, theStatus, theEncounter,
				theValueQuantity, theCount));
	}

	@Operation(name = "$lab-panel", idempotent = true)
	public Bundle labPanel(@IdParam IdType thePatientId) {
		if (labResultsService != null) {
			return labResultsService.generateLabPanel(thePatientId.getIdPart());
		}
		throw new UnsupportedOperationException("Lab Results service not available");
	}

	@Operation(name = "$lab-trends", idempotent = true)
	public Bundle labTrends(
			@IdParam IdType thePatientId,
			@OperationParam(name = "code") TokenParam code,
			@OperationParam(name = "start-date") DateParam startDate,
			@OperationParam(name = "end-date") DateParam endDate) {

		if (labResultsService != null) {
			return labResultsService.getLabTrends(
					thePatientId.getIdPart(),
					code,
					startDate != null ? startDate.getValue() : null,
					endDate != null ? endDate.getValue() : null);
		}
		throw new UnsupportedOperationException("Lab Results service not available");
	}

	@Operation(name = "$abnormal-results", idempotent = true)
	public Bundle abnormalResults(
			@IdParam IdType thePatientId,
			@OperationParam(name = "period") NumberParam periodDays) {

		if (labResultsService != null) {
			return labResultsService.getAbnormalResults(
					thePatientId.getIdPart(),
					periodDays != null ? periodDays.getValue().intValue() : 30);
		}
		throw new UnsupportedOperationException("Lab Results service not available");
	}

	@Operation(name = "$critical-values", idempotent = true)
	public Bundle criticalValues(
			@IdParam IdType thePatientId,
			@OperationParam(name = "encounter") ReferenceParam encounter) {

		if (labResultsService != null) {
			return labResultsService.getCriticalValues(
					thePatientId.getIdPart(),
					encounter != null ? encounter.getValue() : null);
		}
		throw new UnsupportedOperationException("Lab Results service not available");
	}

	@Operation(name = "$lab-summary", idempotent = true)
	public Bundle labSummary(
			@IdParam IdType thePatientId,
			@OperationParam(name = "category") TokenParam category) {

		if (labResultsService != null) {
			return labResultsService.getLabSummary(
					thePatientId.getIdPart(),
					category);
		}
		throw new UnsupportedOperationException("Lab Results service not available");
	}

	private void enrichLabResultForEHR(Observation labResult) {
		// Ensure laboratory category
		boolean hasLabCategory = labResult.getCategory().stream()
				.anyMatch(category -> category.getCoding().stream()
						.anyMatch(coding -> "laboratory".equals(coding.getCode())));

		if (!hasLabCategory) {
			CodeableConcept labCategory = new CodeableConcept();
			labCategory.addCoding()
					.setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
					.setCode("laboratory")
					.setDisplay("Laboratory");
			labResult.addCategory(labCategory);
		}

		// Set default status if not present
		if (labResult.getStatus() == null) {
			labResult.setStatus(Observation.ObservationStatus.FINAL);
		}

		// Add EHR metadata
		if (labResult.getMeta() == null) {
			labResult.setMeta(new Meta());
		}
		labResult.getMeta()
				.addProfile("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/ehr-lab-result")
				.addTag()
				.setSystem("http://" + hospitalConfig.getIdentifier() + "/fhir/CodeSystem/data-source")
				.setCode("ehr")
				.setDisplay("Electronic Health Record");

		// Add audit extension
		Extension auditExtension = new Extension();
		auditExtension.setUrl("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/audit-trail");
		auditExtension.addExtension("created-date", new DateTimeType(new Date()));
		auditExtension.addExtension("created-by", new StringType("EHR System"));
		labResult.addExtension(auditExtension);

		// Set effective date if not present
		if (labResult.getEffective() == null) {
			labResult.setEffective(new DateTimeType(new Date()));
		}

		// Add issued timestamp
		if (labResult.getIssued() == null) {
			labResult.setIssued(new Date());
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
			DateRangeParam theDate, TokenParam theStatus, ReferenceParam theEncounter,
			QuantityParam theValueQuantity, NumberParam theCount) {

		ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = new ca.uhn.fhir.jpa.searchparam.SearchParameterMap();

		// Always filter for laboratory
		searchParams.add(Observation.SP_CATEGORY, new TokenParam("laboratory"));

		if (theSubject != null) {
			searchParams.add(Observation.SP_SUBJECT, theSubject);
		}
		if (thePatient != null) {
			searchParams.add(Observation.SP_PATIENT, thePatient);
		}
		if (theCode != null) {
			searchParams.add(Observation.SP_CODE, theCode);
		}
		if (theDate != null) {
			searchParams.add(Observation.SP_DATE, theDate);
		}
		if (theStatus != null) {
			searchParams.add(Observation.SP_STATUS, theStatus);
		}
		if (theEncounter != null) {
			searchParams.add(Observation.SP_ENCOUNTER, theEncounter);
		}
		if (theValueQuantity != null) {
			searchParams.add(Observation.SP_VALUE_QUANTITY, theValueQuantity);
		}

		return searchParams;
	}
}