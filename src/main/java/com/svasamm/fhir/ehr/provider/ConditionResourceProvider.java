package com.svasamm.fhir.ehr.provider;

import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.svasamm.fhir.config.HospitalConfig;
import com.svasamm.fhir.ehr.service.ConditionService;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

public class ConditionResourceProvider extends BaseJpaResourceProvider<Condition> {

    private static final Logger logger = LoggerFactory.getLogger(ConditionResourceProvider.class);
    @Autowired
    private ConditionService conditionService;
    @Autowired
    private HospitalConfig hospitalConfig;

    public ConditionResourceProvider(IFhirResourceDao<Condition> resourceDao) {
        super(resourceDao);
        logger.info("CUSTOM ConditionResourceProvider CONSTRUCTOR CALLED with JPA DAO");
    }

    @Override
    public Class<Condition> getResourceType() {
        logger.info("CUSTOM ConditionResourceProvider.getResourceType() called");
        return Condition.class;
    }

    @Create
    public MethodOutcome createCondition(@ResourceParam Condition theCondition, RequestDetails theRequestDetails) {
        logger.error("CUSTOM ConditionResourceProvider.createCondition() called");
        logger.error("Condition code: {}", theCondition.getCode().getCodingFirstRep().getDisplay());

        try {
            enrichConditionForEHR(theCondition);
            MethodOutcome result = getDao().create(theCondition, theRequestDetails);

            logger.error("Condition created with ID: {}", result.getId());
            return result;
        } catch (Exception e) {
            logger.error("Error in custom create condition provider: ", e);
            throw e;
        }
    }

    @Read
    public Condition read(HttpServletRequest theRequest, @IdParam IdType theId, RequestDetails theRequestDetails) {
        logger.info("CUSTOM ConditionResourceProvider.read() called for ID: {}", theId);

        try {
            // Use the parent JPA implementation for reading
            Condition condition = super.read(theRequest, theId, theRequestDetails);

            // Add any custom post-read processing if needed
            if (conditionService != null) {
                // Custom logic after reading
                logger.debug("Condition read successfully: {}", theId);
            }

            return condition;

        } catch (Exception e) {
            logger.error("Error reading condition {}: ", theId, e);
            throw new ResourceNotFoundException(theId);
        }
    }

    @Search
    public List<Condition> search(
            @OptionalParam(name = Condition.SP_PATIENT) ReferenceParam thePatient,
            @OptionalParam(name = Condition.SP_SUBJECT) ReferenceParam theSubject,
            @OptionalParam(name = Condition.SP_CATEGORY) TokenParam theCategory,
            @OptionalParam(name = Condition.SP_CODE) TokenParam theCode,
            @OptionalParam(name = Condition.SP_CLINICAL_STATUS) TokenParam theClinicalStatus,
            @OptionalParam(name = Condition.SP_VERIFICATION_STATUS) TokenParam theVerificationStatus,
            @OptionalParam(name = Condition.SP_ONSET_DATE) DateRangeParam theOnsetDate,
            @OptionalParam(name = Condition.SP_BODY_SITE) TokenParam theBodySite,
            @OptionalParam(name = "_count") NumberParam theCount) {

        logger.info("CUSTOM ConditionResourceProvider.search() called");

        // If you have custom search logic, use your service
        if (conditionService != null) {
            return conditionService.searchConditions(thePatient, theSubject, theCategory,
                    theCode, theClinicalStatus, theVerificationStatus, theOnsetDate, theBodySite, theCount);
        }

        // Otherwise, fall back to default JPA search
        return searchByParameters(buildSearchParams(thePatient, theSubject, theCategory,
                theCode, theClinicalStatus, theVerificationStatus, theOnsetDate, theBodySite, theCount));
    }

    /**
     * Search for Conditions by patient reference GET
     * /fhir/R4/Condition?patient=Patient/{patientId}
     */
    @Search
    public List<Condition> searchConditionsByPatient(
            @RequiredParam(name = Condition.SP_PATIENT) ReferenceParam thePatient) {

        logger.info("CUSTOM search conditions by patient: {}", thePatient.getValue());

        if (conditionService != null) {
            return conditionService.getConditionsByPatient(thePatient.getValue());
        }

        // Fall back to JPA search
        ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = new ca.uhn.fhir.jpa.searchparam.SearchParameterMap();
        searchParams.add(Condition.SP_PATIENT, thePatient);
        return searchByParameters(searchParams);
    }

    /**
     * Search for Conditions by diagnosis code GET
     * /fhir/R4/Condition?code={system}|{code}
     */
    @Search
    public List<Condition> searchConditionsByCode(
            @RequiredParam(name = Condition.SP_CODE) TokenParam theCode) {

        logger.info("CUSTOM search conditions by code: {}|{}", theCode.getSystem(), theCode.getValue());

        if (conditionService != null) {
            return conditionService.getConditionByCode(theCode.getSystem(), theCode.getValue());
        }

        // Fall back to JPA search
        ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = new ca.uhn.fhir.jpa.searchparam.SearchParameterMap();
        searchParams.add(Condition.SP_CODE, theCode);
        return searchByParameters(searchParams);
    }

    // Custom operations
    @Operation(name = "$condition-summary", idempotent = true)
    public Bundle conditionSummary(@IdParam IdType theConditionId) {
        logger.info("CUSTOM condition summary operation for ID: {}", theConditionId);

        if (conditionService != null) {
            return conditionService.generateConditionSummary(theConditionId.getIdPart());
        }
        throw new UnsupportedOperationException("Condition service not available");
    }

    @Operation(name = "$condition-timeline", idempotent = true)
    public Bundle conditionTimeline(
            @IdParam IdType theConditionId,
            @OperationParam(name = "start-date") DateParam startDate,
            @OperationParam(name = "end-date") DateParam endDate) {

        logger.info("CUSTOM condition timeline operation for ID: {}", theConditionId);

        if (conditionService != null) {
            return conditionService.generateConditionTimeline(
                    theConditionId.getIdPart(),
                    startDate != null ? startDate.getValue() : null,
                    endDate != null ? endDate.getValue() : null
            );
        }
        throw new UnsupportedOperationException("Condition service not available");
    }

    @Operation(name = "$patient-conditions", idempotent = true)
    public Bundle getPatientConditions(
            @OperationParam(name = "patient-id", min = 1) IdType thePatientId,
            @OperationParam(name = "active-only") BooleanType activeOnly,
            @OperationParam(name = "category") TokenParam category) {

        logger.info("CUSTOM patient conditions operation for patient: {}", thePatientId);

        if (conditionService != null) {
            return conditionService.getPatientConditionsBundle(
                    thePatientId.getIdPart(),
                    activeOnly != null ? activeOnly.getValue() : false,
                    category
            );
        }
        throw new UnsupportedOperationException("Condition service not available");
    }

    private void enrichConditionForEHR(Condition condition) {
        logger.debug("Enriching condition for EHR");

        // Set clinical status if not present
        if (condition.getClinicalStatus() == null) {
            CodeableConcept clinicalStatus = new CodeableConcept();
            clinicalStatus.addCoding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/condition-clinical")
                    .setCode("active")
                    .setDisplay("Active");
            condition.setClinicalStatus(clinicalStatus);
            logger.debug("Added default clinical status: active");
        }

        // Set verification status if not present
        if (condition.getVerificationStatus() == null) {
            CodeableConcept verificationStatus = new CodeableConcept();
            verificationStatus.addCoding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/condition-ver-status")
                    .setCode("confirmed")
                    .setDisplay("Confirmed");
            condition.setVerificationStatus(verificationStatus);
            logger.debug("Added default verification status: confirmed");
        }

        // Add category if not present
        if (condition.getCategory().isEmpty()) {
            CodeableConcept category = new CodeableConcept();
            category.addCoding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/condition-category")
                    .setCode("encounter-diagnosis")
                    .setDisplay("Encounter Diagnosis");
            condition.addCategory(category);
            logger.debug("Added default category: encounter-diagnosis");
        }

        // Add EHR metadata
        if (condition.getMeta() == null) {
            condition.setMeta(new Meta());
        }
        condition.getMeta()
                .addProfile("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/ehr-condition")
                .addTag()
                .setSystem("http://" + hospitalConfig.getIdentifier() + "/fhir/CodeSystem/data-source")
                .setCode("ehr")
                .setDisplay("Electronic Health Record");

        // Add audit extension
        Extension auditExtension = new Extension();
        auditExtension.setUrl("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/audit-trail");
        auditExtension.addExtension("created-date", new DateTimeType(new Date()));
        auditExtension.addExtension("created-by", new StringType("EHR System"));
        auditExtension.addExtension("source", new StringType("Clinical Documentation"));
        condition.addExtension(auditExtension);

        // Add recorder reference if available
        if (condition.getRecorder() == null && hospitalConfig != null) {
            condition.setRecorder(
                    new Reference("Organization/" + hospitalConfig.getIdentifier())
                            .setDisplay(hospitalConfig.getName())
            );
        }

        logger.debug("Condition enrichment completed");
    }

    // Helper method to build search parameters
    private List<Condition> searchByParameters(ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams) {
        try {
            return getDao().search(searchParams).getResources(0, 100)
                    .stream()
                    .map(resource -> (Condition) resource)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            logger.error("Error in condition search: ", e);
            return java.util.Collections.emptyList();
        }
    }

    private ca.uhn.fhir.jpa.searchparam.SearchParameterMap buildSearchParams(
            ReferenceParam thePatient, ReferenceParam theSubject, TokenParam theCategory,
            TokenParam theCode, TokenParam theClinicalStatus, TokenParam theVerificationStatus,
            DateRangeParam theOnsetDate, TokenParam theBodySite, NumberParam theCount) {

        ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = new ca.uhn.fhir.jpa.searchparam.SearchParameterMap();

        if (thePatient != null) {
            searchParams.add(Condition.SP_PATIENT, thePatient);
        }
        if (theSubject != null) {
            searchParams.add(Condition.SP_SUBJECT, theSubject);
        }
        if (theCategory != null) {
            searchParams.add(Condition.SP_CATEGORY, theCategory);
        }
        if (theCode != null) {
            searchParams.add(Condition.SP_CODE, theCode);
        }
        if (theClinicalStatus != null) {
            searchParams.add(Condition.SP_CLINICAL_STATUS, theClinicalStatus);
        }
        if (theVerificationStatus != null) {
            searchParams.add(Condition.SP_VERIFICATION_STATUS, theVerificationStatus);
        }
        if (theOnsetDate != null) {
            searchParams.add(Condition.SP_ONSET_DATE, theOnsetDate);
        }
        if (theBodySite != null) {
            searchParams.add(Condition.SP_BODY_SITE, theBodySite);
        }

        return searchParams;
    }
}
