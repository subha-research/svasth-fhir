package com.svasamm.fhir.ehr.provider;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

import com.svasamm.fhir.ehr.service.ObservationService;
import com.svasamm.fhir.config.HospitalConfig;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;

/**
 * Custom Observation Resource Provider that extends HAPI's JPA provider
 * This preserves all JPA functionality (versioning, locking, etc.) while adding
 * custom logic
 */
public class ObservationResourceProvider extends BaseJpaResourceProvider<Observation> {

    private static final Logger logger = LoggerFactory.getLogger(ObservationResourceProvider.class);

    @Autowired
    private ObservationService observationService;

    @Autowired
    private HospitalConfig hospitalConfig;

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

    @Operation(name = "$observation-summary", idempotent = true)
    public Bundle observationSummary(@IdParam IdType theObservationId) {
        if (observationService != null) {
            return observationService.generateObservationSummary(theObservationId.getIdPart());
        }
        throw new UnsupportedOperationException("Observation service not available");
    }

    @Operation(name = "$patient-observations", idempotent = true)
    public Bundle patientObservations(
            @IdParam IdType thePatientId,
            @OperationParam(name = "category") TokenParam category,
            @OperationParam(name = "start-date") DateParam startDate,
            @OperationParam(name = "end-date") DateParam endDate) {

        if (observationService != null) {
            return observationService.getPatientObservations(
                    thePatientId.getIdPart(),
                    category,
                    startDate != null ? startDate.getValue() : null,
                    endDate != null ? endDate.getValue() : null);
        }
        throw new UnsupportedOperationException("Observation service not available");
    }

    @Operation(name = "$vital-signs-trend", idempotent = true)
    public Bundle vitalSignsTrend(
            @OperationParam(name = "patient", min = 1) ReferenceParam patient,
            @OperationParam(name = "code") TokenParam code,
            @OperationParam(name = "period") NumberParam periodDays) {

        if (observationService != null) {
            return observationService.getVitalSignsTrend(
                    patient.getValue(),
                    code,
                    periodDays != null ? periodDays.getValue().intValue() : 30);
        }
        throw new UnsupportedOperationException("Observation service not available");
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