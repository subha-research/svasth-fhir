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

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
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
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
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

        logger.error("Patient name: {}", thePatient.getName().isEmpty() ? "No name" : thePatient.getName().get(0).getFamily());

        try {
            enrichPatientForEHR(thePatient);
            MethodOutcome result = getDao().create(thePatient, theRequestDetails);
            logger.error("Patient created with ID: {} ", result.getId());
            return result;

        } catch (Exception e) {
            logger.error("Error in custom create patient provider: ", e);
            throw e;
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

        try {
            enrichPatientForEHR(thePatient);
            MethodOutcome result = super.update(theRequest, thePatient, theId, theConditional, theRequestDetails);

            if (patientService != null) {
                logger.info("Patient updated successfully with ID: {}", result.getId());
            }

            return result;

        } catch (Exception e) {
            logger.error("Error in CUSTOM update: ", e);
            throw e;
        }
    }

    @Read
    public Patient read(HttpServletRequest theRequest, @IdParam IdType theId, RequestDetails theRequestDetails) {
        logger.info("CUSTOM PatientResourceProvider.read() called for ID: {}", theId);

        // JWT is already validated by interceptor - just log success
        logJwtSuccess("READ");

        try {
            Patient patient = super.read(theRequest, theId, theRequestDetails);

            if (patientService != null) {
                logger.debug("Patient read successfully: {}", theId);
            }

            return patient;

        } catch (Exception e) {
            logger.error("Error reading patient {}: ", theId, e);
            throw new ResourceNotFoundException(theId);
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

        if (patientService != null) {
            return patientService.searchPatients(theFamily, theGiven, theIdentifier,
                    theBirthDate, theActive, theCount);
        }

        return searchByParameters(buildSearchParams(theFamily, theGiven, theIdentifier, theBirthDate, theActive, theCount));
    }

    @Operation(name = "$patient-summary", idempotent = true)
    public Bundle patientSummary(@IdParam IdType thePatientId, RequestDetails theRequestDetails) {

        // JWT is already validated by interceptor - just log success
        logJwtSuccess("SUMMARY");

        if (patientService != null) {
            return patientService.generatePatientSummary(thePatientId.getIdPart());
        }
        throw new UnsupportedOperationException("Patient service not available");
    }

    // Simple logging method to confirm JWT was validated
    private void logJwtSuccess(String operation) {
        logger.info("🔐 JWT validated successfully for {} operation", operation);
    }

    // Keep all your existing business logic methods
    private void enrichPatientForEHR(Patient patient) {
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
                            .setDisplay(hospitalConfig.getName())
            );
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
    }

    private String generateMRN() {
        return hospitalConfig.getIdentifier() + "-MRN-" + System.currentTimeMillis()
                + String.format("%03d", new Random().nextInt(1000));
    }

    private List<Patient> searchByParameters(ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams) {
        try {
            return getDao().search(searchParams).getResources(0, 100)
                    .stream()
                    .map(resource -> (Patient) resource)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            logger.error("Error in search: ", e);
            return java.util.Collections.emptyList();
        }
    }

    private ca.uhn.fhir.jpa.searchparam.SearchParameterMap buildSearchParams(
            StringParam theFamily, StringParam theGiven, TokenParam theIdentifier,
            DateParam theBirthDate, TokenParam theActive, NumberParam theCount) {

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
    }
}