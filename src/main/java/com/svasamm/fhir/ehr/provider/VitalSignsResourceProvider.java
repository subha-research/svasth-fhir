package com.svasamm.fhir.ehr.provider;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

import com.svasamm.fhir.ehr.service.VitalService;
import com.svasamm.fhir.config.HospitalConfig;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;

/**
 * Custom Vital Signs Resource Provider that extends HAPI's JPA provider
 * Vital Signs are implemented as Observations with vital-signs category
 */
public class VitalSignsResourceProvider extends BaseJpaResourceProvider<Observation> {

	private static final Logger logger = LoggerFactory.getLogger(VitalSignsResourceProvider.class);

	@Autowired
	private VitalService vitalSignsService;

	@Autowired
	private HospitalConfig hospitalConfig;

	public VitalSignsResourceProvider(IFhirResourceDao<Observation> theDao) {
		super(theDao);
		logger.info("🚀 CUSTOM VitalSignsResourceProvider CONSTRUCTOR CALLED with JPA DAO 🚀");
	}

	@Override
	public Class<Observation> getResourceType() {
		logger.info("CUSTOM VitalSignsResourceProvider.getResourceType() called");
		return Observation.class;
	}

	@Create
	public MethodOutcome create(@ResourceParam Observation theVitalSign, RequestDetails theRequestDetails) {
		logger.error("Custom VitalSignsResourceProvider.create() called");
		logger.error("Vital Sign code: {}", theVitalSign.getCode().getCodingFirstRep().getDisplay());

		try {
			// Apply vital signs specific enrichment
			enrichVitalSignForEHR(theVitalSign);

			// Use DAO directly instead of super.create()
			MethodOutcome result = getDao().create(theVitalSign, theRequestDetails);

			logger.error("Vital Sign created with ID: {} ", result.getId());
			return result;

		} catch (Exception e) {
			logger.error("Error in custom create vital sign provider: ", e);
			throw e;
		}
	}

	@Update
	public MethodOutcome update(
			HttpServletRequest theRequest,
			@IdParam IdType theId,
			@ResourceParam Observation theVitalSign,
			@ConditionalUrlParam String theConditional,
			RequestDetails theRequestDetails) {

		logger.info("Custom VitalSignsResourceProvider.update() called for ID: {}", theId);

		try {
			// Apply vital signs specific enrichment
			enrichVitalSignForEHR(theVitalSign);

			// Call parent JPA implementation - this handles optimistic locking
			// automatically
			MethodOutcome result = super.update(theRequest, theVitalSign, theId, theConditional, theRequestDetails);

			// Custom post-processing
			if (vitalSignsService != null) {
				// Add any additional custom logic here
				logger.info("Vital Sign updated successfully with ID: {}", result.getId());
			}

			return result;

		} catch (Exception e) {
			logger.error("Error in CUSTOM update: ", e);
			throw e;
		}
	}

	@Read
	public Observation read(HttpServletRequest theRequest, @IdParam IdType theId, RequestDetails theRequestDetails) {
		logger.info("CUSTOM VitalSignsResourceProvider.read() called for ID: {}", theId);

		try {
			// Use the parent JPA implementation for reading
			Observation vitalSign = super.read(theRequest, theId, theRequestDetails);

			// Verify it's actually a vital sign
			if (vitalSignsService != null && !vitalSignsService.isVitalSign(vitalSign)) {
				throw new ResourceNotFoundException("Resource is not a vital sign: " + theId);
			}

			return vitalSign;

		} catch (Exception e) {
			logger.error("Error reading vital sign {}: ", theId, e);
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
			@OptionalParam(name = "_count") NumberParam theCount) {

		// Always filter for vital signs category
		if (vitalSignsService != null) {
			return vitalSignsService.searchVitalSigns(theSubject, thePatient, theCode,
					theDate, theStatus, theEncounter, theCount);
		}

		// Otherwise, fall back to default JPA search with vital signs filter
		return searchByParameters(
				buildSearchParams(theSubject, thePatient, theCode, theDate, theStatus, theEncounter, theCount));
	}

	@Operation(name = "$vital-signs-panel", idempotent = true)
	public Bundle vitalSignsPanel(@IdParam IdType thePatientId) {
		if (vitalSignsService != null) {
			return vitalSignsService.generateVitalSignsPanel(thePatientId.getIdPart());
		}
		throw new UnsupportedOperationException("Vital Signs service not available");
	}

	@Operation(name = "$vital-signs-trend", idempotent = true)
	public Bundle vitalSignsTrend(
			@IdParam IdType thePatientId,
			@OperationParam(name = "code") TokenParam code,
			@OperationParam(name = "start-date") DateParam startDate,
			@OperationParam(name = "end-date") DateParam endDate) {

		if (vitalSignsService != null) {
			return vitalSignsService.getVitalSignsTrend(
					thePatientId.getIdPart(),
					code,
					startDate != null ? startDate.getValue() : null,
					endDate != null ? endDate.getValue() : null);
		}
		throw new UnsupportedOperationException("Vital Signs service not available");
	}

	@Operation(name = "$latest-vital-signs", idempotent = true)
	public Bundle latestVitalSigns(
			@IdParam IdType thePatientId,
			@OperationParam(name = "period") NumberParam periodHours) {

		if (vitalSignsService != null) {
			return vitalSignsService.getLatestVitalSigns(
					thePatientId.getIdPart(),
					periodHours != null ? periodHours.getValue().intValue() : 24);
		}
		throw new UnsupportedOperationException("Vital Signs service not available");
	}

	@Operation(name = "$vital-signs-summary", idempotent = true)
	public Bundle vitalSignsSummary(
			@IdParam IdType thePatientId,
			@OperationParam(name = "encounter") ReferenceParam encounter) {

		if (vitalSignsService != null) {
			return vitalSignsService.getVitalSignsSummary(
					thePatientId.getIdPart(),
					encounter != null ? encounter.getValue() : null);
		}
		throw new UnsupportedOperationException("Vital Signs service not available");
	}

	private void enrichVitalSignForEHR(Observation vitalSign) {
		// Ensure vital signs category
		boolean hasVitalSignsCategory = vitalSign.getCategory().stream()
				.anyMatch(category -> category.getCoding().stream()
						.anyMatch(coding -> "vital-signs".equals(coding.getCode())));

		if (!hasVitalSignsCategory) {
			CodeableConcept vitalSignsCategory = new CodeableConcept();
			vitalSignsCategory.addCoding()
					.setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
					.setCode("vital-signs")
					.setDisplay("Vital Signs");
			vitalSign.addCategory(vitalSignsCategory);
		}

		// Set default status if not present
		if (vitalSign.getStatus() == null) {
			vitalSign.setStatus(Observation.ObservationStatus.FINAL);
		}

		// Add EHR metadata
		if (vitalSign.getMeta() == null) {
			vitalSign.setMeta(new Meta());
		}
		vitalSign.getMeta()
				.addProfile("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/ehr-vital-signs")
				.addTag()
				.setSystem("http://" + hospitalConfig.getIdentifier() + "/fhir/CodeSystem/data-source")
				.setCode("ehr")
				.setDisplay("Electronic Health Record");

		// Add audit extension
		Extension auditExtension = new Extension();
		auditExtension.setUrl("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/audit-trail");
		auditExtension.addExtension("created-date", new DateTimeType(new Date()));
		auditExtension.addExtension("created-by", new StringType("EHR System"));
		vitalSign.addExtension(auditExtension);

		// Set effective date if not present
		if (vitalSign.getEffective() == null) {
			vitalSign.setEffective(new DateTimeType(new Date()));
		}

		// Add issued timestamp
		if (vitalSign.getIssued() == null) {
			vitalSign.setIssued(new Date());
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
			DateRangeParam theDate, TokenParam theStatus, ReferenceParam theEncounter, NumberParam theCount) {

		ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = new ca.uhn.fhir.jpa.searchparam.SearchParameterMap();

		// Always filter for vital signs
		searchParams.add(Observation.SP_CATEGORY, new TokenParam("vital-signs"));

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

		return searchParams;
	}
}