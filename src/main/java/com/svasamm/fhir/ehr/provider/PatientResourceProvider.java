package com.svasamm.fhir.ehr.provider;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.svasamm.fhir.ehr.service.PatientService;
import com.svasamm.fhir.config.HospitalConfig;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Random;

@Component
public class PatientResourceProvider implements IResourceProvider {

    @Autowired
    private PatientService patientService;

    @Autowired
    private HospitalConfig hospitalConfig;

    @Override
    public Class<Patient> getResourceType() {
        return Patient.class;
    }

    @Read
    public Patient read(@IdParam IdType theId) {
        Patient patient = patientService.getPatientById(theId.getIdPart());
        if (patient == null) {
            throw new ResourceNotFoundException(theId);
        }
        return patient;
    }

    @Create
    public MethodOutcome create(@ResourceParam Patient thePatient) {
        enrichPatientForEHR(thePatient);
        return patientService.createPatient(thePatient);
    }

    @Update
    public MethodOutcome update(@IdParam IdType theId, @ResourceParam Patient thePatient) {
        return patientService.updatePatient(theId.getIdPart(), thePatient);
    }

    @Search
    public List<Patient> search(
            @OptionalParam(name = Patient.SP_FAMILY) StringParam theFamily,
            @OptionalParam(name = Patient.SP_GIVEN) StringParam theGiven,
            @OptionalParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier,
            @OptionalParam(name = Patient.SP_BIRTHDATE) DateParam theBirthDate,
            @OptionalParam(name = Patient.SP_ACTIVE) TokenParam theActive,
            @OptionalParam(name = "_count") NumberParam theCount) {
        
        return patientService.searchPatients(theFamily, theGiven, theIdentifier, 
                                           theBirthDate, theActive, theCount);
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
        return hospitalConfig.getIdentifier() + "-MRN-" + System.currentTimeMillis() + 
               String.format("%03d", new Random().nextInt(1000));
    }
}