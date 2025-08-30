package com.svasamm.fhir.ehr.provider;

import java.io.IOException;
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
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.svasamm.fhir.config.HospitalConfig;
import com.svasamm.fhir.ehr.dto.patient.PatientDto;
import com.svasamm.fhir.ehr.mapper.PatientMapper;
import com.svasamm.fhir.ehr.service.PatientService;

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
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Custom Patient Resource Provider that extends HAPI's JPA provider This
 * preserves all JPA functionality while adding custom logic and mapping
 */
public class PatientResourceProvider extends BaseJpaResourceProvider<Patient> {

    private static final Logger logger = LoggerFactory.getLogger(PatientResourceProvider.class);
    @Autowired
    private PatientService patientService;
    @Autowired
    private HospitalConfig hospitalConfig;
    @Autowired
    private PatientMapper patientMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        logger.error("Patient name: {}", thePatient.getName().isEmpty() ? "No name" : thePatient.getName().get(0).getFamily());
        try {
            // Apply custom EHR enrichment
            enrichPatientForEHR(thePatient);
            // Use DAO directly instead of super.create()
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
        try {
            // Apply custom EHR enrichment
            enrichPatientForEHR(thePatient);
            // Call parent JPA implementation - this handles optimistic locking automatically
            MethodOutcome result = super.update(theRequest, thePatient, theId, theConditional, theRequestDetails);
            // Custom post-processing
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
        try {
            // Use the parent JPA implementation for reading
            Patient patient = super.read(theRequest, theId, theRequestDetails);
            // Add any custom post-read processing if needed
            if (patientService != null) {
                logger.debug("Patient read successfully: {}", theId);
            }
            return patient;
        } catch (Exception e) {
            logger.error("Error reading patient {}: ", theId, e);
            throw new ResourceNotFoundException(theId);
        }
    }

    /**
     * Custom operation to get patient in mapped format Usage: GET
     * /Patient/{id}/$mapped
     */
    @Operation(name = "$mapped", idempotent = true)
    public void getMappedPatient(
            @IdParam IdType theId,
            HttpServletRequest theRequest,
            HttpServletResponse theResponse,
            RequestDetails theRequestDetails) {
        logger.info("Custom PatientResourceProvider.getMappedPatient() called for ID: {}", theId);
        try {
            // Get the patient using service
            Patient patient = patientService.getPatientById(theId.getIdPart());
            if (patient == null) {
                theResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
                theResponse.getWriter().write("{\"error\":\"Patient not found\"}");
                theResponse.getWriter().flush();
                return;
            }
            // Map to custom format
            PatientDto mappedPatient = patientMapper.mapToDTO(patient);
            String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mappedPatient);
            // Set response headers and status first
            theResponse.setStatus(HttpServletResponse.SC_OK);
            theResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            theResponse.setCharacterEncoding("UTF-8");
            theResponse.setHeader("Cache-Control", "no-cache");
            // Write response and flush immediately
            theResponse.getWriter().write(jsonResponse);
            theResponse.getWriter().flush();
            theResponse.getWriter().close();
            logger.info("Mapped patient response sent for ID: {}", theId);
        } catch (Exception e) {
            logger.error("Error getting mapped patient {}: ", theId, e);
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
     * Custom operation to search patients and return them in mapped format
     * Usage: GET /Patient/$search-mapped?family=Smith&given=Jane
     */
    @Operation(name = "$search-mapped", idempotent = true)
    public void searchMappedPatients(
            @OperationParam(name = "family") StringParam theFamily,
            @OperationParam(name = "given") StringParam theGiven,
            @OperationParam(name = "identifier") TokenParam theIdentifier,
            @OperationParam(name = "birthdate") DateParam theBirthDate,
            @OperationParam(name = "active") TokenParam theActive,
            @OperationParam(name = "_count") NumberParam theCount,
            HttpServletRequest theRequest,
            HttpServletResponse theResponse,
            RequestDetails theRequestDetails) {
        logger.info("Custom PatientResourceProvider.searchMappedPatients() called");
        try {
            // Perform search using service
            List<Patient> patients = patientService.searchPatients(theFamily, theGiven, theIdentifier, theBirthDate, theActive, theCount);
            // Map all patients to custom format
            StringBuilder jsonResponse = new StringBuilder();
            jsonResponse.append("{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":")
                    .append(patients.size())
                    .append(",\"entry\":[");
            for (int i = 0; i < patients.size(); i++) {
                if (i > 0) {
                    jsonResponse.append(",");
                }
                PatientDto mappedPatient = patientMapper.mapToDTO(patients.get(i));
                String patientJson = objectMapper.writeValueAsString(mappedPatient);
                jsonResponse.append("{\"resource\":")
                        .append(patientJson)
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
            logger.info("Mapped patients search response sent, {} patients found", patients.size());
        } catch (Exception e) {
            logger.error("Error in search mapped patients: ", e);
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
    public List<Patient> search(
            @OptionalParam(name = Patient.SP_FAMILY) StringParam theFamily,
            @OptionalParam(name = Patient.SP_GIVEN) StringParam theGiven,
            @OptionalParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier,
            @OptionalParam(name = Patient.SP_BIRTHDATE) DateParam theBirthDate,
            @OptionalParam(name = Patient.SP_ACTIVE) TokenParam theActive,
            @OptionalParam(name = "_count") NumberParam theCount) {
        // Use your existing service for search
        return patientService.searchPatients(theFamily, theGiven, theIdentifier, theBirthDate, theActive, theCount);
    }

    @Operation(name = "$patient-summary", idempotent = true)
    public Bundle patientSummary(@IdParam IdType thePatientId) {
        return patientService.generatePatientSummary(thePatientId.getIdPart());
    }

    @Operation(name = "$patient-chart", idempotent = true)
    public Bundle patientChart(
            @IdParam IdType thePatientId,
            @OperationParam(name = "start-date") DateParam startDate,
            @OperationParam(name = "end-date") DateParam endDate) {
        return patientService.generatePatientChart(
                thePatientId.getIdPart(),
                startDate != null ? startDate.getValue() : null,
                endDate != null ? endDate.getValue() : null
        );
    }

    @Operation(name = "$merge-patients")
    public MethodOutcome mergePatients(
            @OperationParam(name = "source-patient", min = 1) IdType sourcePatient,
            @OperationParam(name = "target-patient", min = 1) IdType targetPatient) {
        return patientService.mergePatients(sourcePatient.getIdPart(), targetPatient.getIdPart());
    }

    // Your existing private methods remain the same
    private void enrichPatientForEHR(Patient patient) {
        // Generate MRN if not present
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
        // Add managing organization
        if (patient.getManagingOrganization() == null) {
            patient.setManagingOrganization(
                    new Reference("Organization/" + hospitalConfig.getIdentifier())
                            .setDisplay(hospitalConfig.getName())
            );
        }
        // Add EHR metadata
        if (patient.getMeta() == null) {
            patient.setMeta(new Meta());
        }
        patient.getMeta()
                .addProfile("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/ehr-patient")
                .addTag()
                .setSystem("http://" + hospitalConfig.getIdentifier() + "/fhir/CodeSystem/data-source")
                .setCode("ehr")
                .setDisplay("Electronic Health Record");
        // Add audit extension
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
