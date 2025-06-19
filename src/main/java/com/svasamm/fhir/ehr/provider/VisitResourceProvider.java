// package com.svasamm.fhir.ehr.provider;

// import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
// import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
// import ca.uhn.fhir.rest.annotation.*;
// import ca.uhn.fhir.rest.api.MethodOutcome;
// import ca.uhn.fhir.rest.api.server.RequestDetails;
// import ca.uhn.fhir.rest.param.*;
// import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
// import jakarta.servlet.http.HttpServletRequest;

// import com.svasamm.fhir.ehr.service.VisitService;
// import com.svasamm.fhir.config.HospitalConfig;
// import org.hl7.fhir.r4.model.*;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;

// import java.util.Date;
// import java.util.List;

// /**
//  * Custom Visit Resource Provider that extends HAPI's JPA provider
//  * This preserves all JPA functionality (versioning, locking, etc.) while adding
//  * custom logic
//  * Visits are implemented using the Encounter resource
//  */
// public class VisitResourceProvider extends BaseJpaResourceProvider<Encounter> {

// 	private static final Logger logger = LoggerFactory.getLogger(VisitResourceProvider.class);

// 	@Autowired
// 	private VisitService visitService;

// 	@Autowired
// 	private HospitalConfig hospitalConfig;

// 	public VisitResourceProvider(IFhirResourceDao<Encounter> theDao) {
// 		super(theDao);
// 		logger.info("🚀 CUSTOM VisitResourceProvider CONSTRUCTOR CALLED with JPA DAO 🚀");
// 	}

// 	@Override
// 	public Class<Encounter> getResourceType() {
// 		logger.info("CUSTOM VisitResourceProvider.getResourceType() called");
// 		return Encounter.class;
// 	}

// 	@Create
// 	public MethodOutcome create(@ResourceParam Encounter theVisit, RequestDetails theRequestDetails) {
// 		logger.error("Custom VisitResourceProvider.create() called");
// 		logger.error("Visit class: {}", theVisit.getClass_() != null ? theVisit.getClass_().getCode() : "No class");

// 		try {
// 			// Apply custom EHR enrichment
// 			enrichVisitForEHR(theVisit);

// 			// Use DAO directly instead of super.create()
// 			MethodOutcome result = getDao().create(theVisit, theRequestDetails);

// 			logger.error("Visit created with ID: {} ", result.getId());
// 			return result;

// 		} catch (Exception e) {
// 			logger.error("Error in custom create visit provider: ", e);
// 			throw e;
// 		}
// 	}

// 	@Update
// 	public MethodOutcome update(
// 			HttpServletRequest theRequest,
// 			@IdParam IdType theId,
// 			@ResourceParam Encounter theVisit,
// 			@ConditionalUrlParam String theConditional,
// 			RequestDetails theRequestDetails) {

// 		logger.info("Custom VisitResourceProvider.update() called for ID: {}", theId);

// 		try {
// 			// Apply custom EHR enrichment
// 			enrichVisitForEHR(theVisit);

// 			// Call parent JPA implementation - this handles optimistic locking
// 			// automatically
// 			MethodOutcome result = super.update(theRequest, theVisit, theId, theConditional, theRequestDetails);

// 			// Custom post-processing
// 			if (visitService != null) {
// 				// Add any additional custom logic here
// 				logger.info("Visit updated successfully with ID: {}", result.getId());
// 			}

// 			return result;

// 		} catch (Exception e) {
// 			logger.error("Error in CUSTOM update: ", e);
// 			throw e;
// 		}
// 	}

// 	@Read
// 	public Encounter read(HttpServletRequest theRequest, @IdParam IdType theId, RequestDetails theRequestDetails) {
// 		logger.info("CUSTOM VisitResourceProvider.read() called for ID: {}", theId);

// 		try {
// 			// Use the parent JPA implementation for reading
// 			Encounter visit = super.read(theRequest, theId, theRequestDetails);

// 			// Add any custom post-read processing if needed
// 			if (visitService != null) {
// 				// Custom logic after reading
// 				logger.debug("Visit read successfully: {}", theId);
// 			}

// 			return visit;

// 		} catch (Exception e) {
// 			logger.error("Error reading visit {}: ", theId, e);
// 			throw new ResourceNotFoundException(theId);
// 		}
// 	}

// 	@Search
// 	public List<Encounter> search(
// 			@OptionalParam(name = Encounter.SP_SUBJECT) ReferenceParam theSubject,
// 			@OptionalParam(name = Encounter.SP_PATIENT) ReferenceParam thePatient,
// 			@OptionalParam(name = Encounter.SP_CLASS) TokenParam theClass,
// 			@OptionalParam(name = Encounter.SP_DATE) DateRangeParam theDate,
// 			@OptionalParam(name = Encounter.SP_STATUS) TokenParam theStatus,
// 			@OptionalParam(name = Encounter.SP_TYPE) TokenParam theType,
// 			@OptionalParam(name = Encounter.SP_LOCATION) ReferenceParam theLocation,
// 			@OptionalParam(name = "_count") NumberParam theCount) {

// 		// If you have custom search logic, use your service
// 		if (visitService != null) {
// 			return visitService.searchVisits(theSubject, thePatient, theClass,
// 					theDate, theStatus, theType, theLocation, theCount);
// 		}

// 		// Otherwise, fall back to default JPA search
// 		return searchByParameters(
// 				buildSearchParams(theSubject, thePatient, theClass, theDate, theStatus, theType, theLocation, theCount));
// 	}

// 	@Operation(name = "$visit-summary", idempotent = true)
// 	public Bundle visitSummary(@IdParam IdType theVisitId) {
// 		if (visitService != null) {
// 			return visitService.generateVisitSummary(theVisitId.getIdPart());
// 		}
// 		throw new UnsupportedOperationException("Visit service not available");
// 	}

// 	@Operation(name = "$patient-visits", idempotent = true)
// 	public Bundle patientVisits(
// 			@IdParam IdType thePatientId,
// 			@OperationParam(name = "class") TokenParam visitClass,
// 			@OperationParam(name = "start-date") DateParam startDate,
// 			@OperationParam(name = "end-date") DateParam endDate) {

// 		if (visitService != null) {
// 			return visitService.getPatientVisits(
// 					thePatientId.getIdPart(),
// 					visitClass,
// 					startDate != null ? startDate.getValue() : null,
// 					endDate != null ? endDate.getValue() : null);
// 		}
// 		throw new UnsupportedOperationException("Visit service not available");
// 	}

// 	@Operation(name = "$active-visits", idempotent = true)
// 	public Bundle activeVisits(
// 			@OperationParam(name = "location") ReferenceParam location,
// 			@OperationParam(name = "class") TokenParam visitClass) {

// 		if (visitService != null) {
// 			return visitService.getActiveVisits(location, visitClass);
// 		}
// 		throw new UnsupportedOperationException("Visit service not available");
// 	}

// 	// @Operation(name = "$visit-timeline", idempotent = true)
// 	// public Bundle visitTimeline(
// 	// @IdParam IdType theVisitId,
// 	// @OperationParam(name = "include-observations") BooleanParam
// 	// includeObservations,
// 	// @OperationParam(name = "include-procedures") BooleanParam includeProcedures)
// 	// {

// 	// if (visitService != null) {
// 	// return visitService.getVisitTimeline(
// 	// theVisitId.getIdPart(),
// 	// includeObservations != null ? includeObservations.getValue() : true,
// 	// includeProcedures != null ? includeProcedures.getValue() : true
// 	// );
// 	// }
// 	// throw new UnsupportedOperationException("Visit service not available");
// 	// }

// 	private void enrichVisitForEHR(Encounter visit) {
// 		// Set default status if not present
// 		if (visit.getStatus() == null) {
// 			visit.setStatus(Encounter.EncounterStatus.INPROGRESS);
// 		}

// 		// Add EHR metadata
// 		if (visit.getMeta() == null) {
// 			visit.setMeta(new Meta());
// 		}
// 		visit.getMeta()
// 				.addProfile("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/ehr-encounter")
// 				.addTag()
// 				.setSystem("http://" + hospitalConfig.getIdentifier() + "/fhir/CodeSystem/data-source")
// 				.setCode("ehr")
// 				.setDisplay("Electronic Health Record");

// 		// Add audit extension
// 		Extension auditExtension = new Extension();
// 		auditExtension.setUrl("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/audit-trail");
// 		auditExtension.addExtension("created-date", new DateTimeType(new Date()));
// 		auditExtension.addExtension("created-by", new StringType("EHR System"));
// 		visit.addExtension(auditExtension);

// 		// Set period start if not present
// 		if (visit.getPeriod() == null) {
// 			Period period = new Period();
// 			period.setStart(new Date());
// 			visit.setPeriod(period);
// 		} else if (visit.getPeriod().getStart() == null) {
// 			visit.getPeriod().setStart(new Date());
// 		}

// 		// Add default service provider if not present
// 		if (visit.getServiceProvider() == null) {
// 			visit.setServiceProvider(
// 					new Reference("Organization/" + hospitalConfig.getIdentifier())
// 							.setDisplay(hospitalConfig.getName()));
// 		}
// 	}

// 	// Helper method to build search parameters
// 	private List<Encounter> searchByParameters(ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams) {
// 		try {
// 			return getDao().search(searchParams).getResources(0, 100)
// 					.stream()
// 					.map(resource -> (Encounter) resource)
// 					.collect(java.util.stream.Collectors.toList());
// 		} catch (Exception e) {
// 			logger.error("Error in search: ", e);
// 			return java.util.Collections.emptyList();
// 		}
// 	}

// 	private ca.uhn.fhir.jpa.searchparam.SearchParameterMap buildSearchParams(
// 			ReferenceParam theSubject, ReferenceParam thePatient, TokenParam theClass,
// 			DateRangeParam theDate, TokenParam theStatus, TokenParam theType,
// 			ReferenceParam theLocation, NumberParam theCount) {

// 		ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = new ca.uhn.fhir.jpa.searchparam.SearchParameterMap();

// 		if (theSubject != null) {
// 			searchParams.add(Encounter.SP_SUBJECT, theSubject);
// 		}
// 		if (thePatient != null) {
// 			searchParams.add(Encounter.SP_PATIENT, thePatient);
// 		}
// 		if (theClass != null) {
// 			searchParams.add(Encounter.SP_CLASS, theClass);
// 		}
// 		if (theDate != null) {
// 			searchParams.add(Encounter.SP_DATE, theDate);
// 		}
// 		if (theStatus != null) {
// 			searchParams.add(Encounter.SP_STATUS, theStatus);
// 		}
// 		if (theType != null) {
// 			searchParams.add(Encounter.SP_TYPE, theType);
// 		}
// 		if (theLocation != null) {
// 			searchParams.add(Encounter.SP_LOCATION, theLocation);
// 		}

// 		return searchParams;
// 	}
// }

package com.svasamm.fhir.ehr.provider;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

import com.svasamm.fhir.ehr.service.VisitService;
import com.svasamm.fhir.ehr.mapper.VisitMapper;
import com.svasamm.fhir.config.HospitalConfig;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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

	@Operation(name = "$visit-summary", idempotent = true)
	public Bundle visitSummary(@IdParam IdType theVisitId) {
		if (visitService != null) {
			return visitService.generateVisitSummary(theVisitId.getIdPart());
		}
		throw new UnsupportedOperationException("Visit service not available");
	}

	@Operation(name = "$patient-visits", idempotent = true)
	public Bundle patientVisits(
			@IdParam IdType thePatientId,
			@OperationParam(name = "class") TokenParam visitClass,
			@OperationParam(name = "start-date") DateParam startDate,
			@OperationParam(name = "end-date") DateParam endDate) {

		if (visitService != null) {
			return visitService.getPatientVisits(
					thePatientId.getIdPart(),
					visitClass,
					startDate != null ? startDate.getValue() : null,
					endDate != null ? endDate.getValue() : null);
		}
		throw new UnsupportedOperationException("Visit service not available");
	}

	@Operation(name = "$active-visits", idempotent = true)
	public Bundle activeVisits(
			@OperationParam(name = "location") ReferenceParam location,
			@OperationParam(name = "class") TokenParam visitClass) {

		if (visitService != null) {
			return visitService.getActiveVisits(location, visitClass);
		}
		throw new UnsupportedOperationException("Visit service not available");
	}

	@Operation(name = "$visit-timeline", idempotent = true)
	public Bundle visitTimeline(
			@IdParam IdType theVisitId,
			@OperationParam(name = "include-observations") BooleanParam includeObservations,
			@OperationParam(name = "include-procedures") BooleanParam includeProcedures) {

		if (visitService != null) {
			return visitService.getVisitTimeline(
					theVisitId.getIdPart(),
					includeObservations != null ? includeObservations.getValue() : true,
					includeProcedures != null ? includeProcedures.getValue() : true);
		}
		throw new UnsupportedOperationException("Visit service not available");
	}

	@Operation(name = "$visit-statistics", idempotent = true)
	public Bundle visitStatistics(
			@OperationParam(name = "location") ReferenceParam location,
			@OperationParam(name = "period") NumberParam periodDays,
			@OperationParam(name = "class") TokenParam visitClass) {

		if (visitService != null) {
			return visitService.getVisitStatistics(
					location,
					periodDays != null ? periodDays.getValue().intValue() : 30,
					visitClass);
		}
		throw new UnsupportedOperationException("Visit service not available");
	}

	// @Operation(name = "$get-dto", idempotent = true)
	// public VisitDto getVisitDTO(@IdParam IdType theVisitId) {
	// 	if (visitMapper != null) {
	// 		try {
	// 			Encounter visit = getDao().read(theVisitId);
	// 			return visitMapper.mapToDTO(visit);
	// 		} catch (Exception e) {
	// 			logger.error("Error mapping visit to DTO: ", e);
	// 			throw new RuntimeException("Failed to map visit: " + e.getMessage());
	// 		}
	// 	}
	// 	throw new UnsupportedOperationException("Visit mapper not available");
	// }

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