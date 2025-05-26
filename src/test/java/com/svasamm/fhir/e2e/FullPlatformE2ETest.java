package com.svasamm.fhir.e2e;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.svasamm.fhir.BaseIntegrationTest;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@org.springframework.boot.test.context.SpringBootTest(webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
class FullPlatformE2ETest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private FhirContext fhirContext;

    private IGenericClient fhirClient;

    @BeforeEach
    void setUp() {
        String serverBase = "http://localhost:" + port + "/fhir";
        fhirClient = fhirContext.newRestfulGenericClient(serverBase);
    }

    @Test
    void testCompleteWorkflow_PatientToSpecimen() {
        // 1. Create Patient (EHR Module)
        Patient patient = new Patient();
        patient.addName().setFamily("WorkflowTest").addGiven("Complete");
        patient.setGender(Enumerations.AdministrativeGender.FEMALE);
        patient.setBirthDate(java.sql.Date.valueOf("1985-03-20"));
        patient.addTelecom()
                .setSystem(ContactPoint.ContactPointSystem.EMAIL)
                .setValue("workflow@test.com");
        patient.addAddress()
                .addLine("123 Workflow Street")
                .setCity("Test City")
                .setPostalCode("12345");

        MethodOutcome patientOutcome = fhirClient.create().resource(patient).execute();
        String patientId = patientOutcome.getId().getIdPart();
        
        // Verify patient creation
        assertNotNull(patientId);
        assertTrue(patientOutcome.getCreated());

        // 2. Read Patient and verify EHR enrichment
        Patient createdPatient = fhirClient.read()
                .resource(Patient.class)
                .withId(patientId)
                .execute();

        // Should have MRN
        assertTrue(createdPatient.getIdentifier().stream()
                .anyMatch(id -> "MR".equals(id.getType().getCodingFirstRep().getCode())));

        // Should have managing organization
        assertNotNull(createdPatient.getManagingOrganization());
        assertEquals("Test Hospital", createdPatient.getManagingOrganization().getDisplay());

        // 3. Create Specimen linked to Patient (Biobank Module)
        Specimen specimen = new Specimen();
        specimen.setSubject(new Reference("Patient/" + patientId));
        specimen.getType().addCoding()
                .setSystem("http://snomed.info/sct")
                .setCode("119361006")
                .setDisplay("Plasma specimen");
        specimen.getCollection()
            .setCollected(new DateTimeType(new Date()))
            .setQuantity(new Quantity().setValue(7.5).setUnit("mL"))
            .setCollector(new Reference().setDisplay("Lab Technician"));
        specimen.addContainer()
                .getType().addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/v2-0487")
                .setCode("TUBE")
                .setDisplay("Tube");

        MethodOutcome specimenOutcome = fhirClient.create().resource(specimen).execute();
        String specimenId = specimenOutcome.getId().getIdPart();

        // Verify specimen creation
        assertNotNull(specimenId);
        assertTrue(specimenOutcome.getCreated());

        // 4. Read Specimen and verify Biobank enrichment
        Specimen createdSpecimen = fhirClient.read()
                .resource(Specimen.class)
                .withId(specimenId)
                .execute();

        // Should have biobank ID
        assertTrue(createdSpecimen.getIdentifier().stream()
                .anyMatch(id -> id.getSystem().contains("biobank")));

        // Should have collection facility
        assertNotNull(createdSpecimen.getCollection().getCollector());

        // 5. Test Patient Summary (includes linked specimens)
        Bundle patientSummary = fhirClient.operation()
                .onInstance(new IdType("Patient", patientId))
                .named("$patient-summary")
                .withNoParameters(Parameters.class)
                .returnResourceType(Bundle.class)
                .execute();

        assertNotNull(patientSummary);
        assertTrue(patientSummary.getEntry().size() >= 1);

        // 6. Test Specimen Chain of Custody
        Bundle chainOfCustody = fhirClient.operation()
                .onInstance(new IdType("Specimen", specimenId))
                .named("$chain-of-custody")
                .withNoParameters(Parameters.class)
                .returnResourceType(Bundle.class)
                .execute();

        assertNotNull(chainOfCustody);
        assertTrue(chainOfCustody.getEntry().size() >= 1);

        // 7. Search specimens by patient
        Bundle specimenSearch = fhirClient.search()
                .forResource(Specimen.class)
                .where(Specimen.SUBJECT.hasId(patientId))
                .returnBundle(Bundle.class)
                .execute();

        assertNotNull(specimenSearch);
        assertTrue(specimenSearch.getTotal() >= 1);

        // Verify the specimen belongs to the patient
        Specimen foundSpecimen = (Specimen) specimenSearch.getEntryFirstRep().getResource();
        assertEquals("Patient/" + patientId, foundSpecimen.getSubject().getReference());

        // 8. Test Cross-Module Data Integrity
        // Update patient and ensure specimen reference remains valid
        createdPatient.getNameFirstRep().setFamily("UpdatedWorkflowTest");
        fhirClient.update().resource(createdPatient).execute();

        // Read specimen again - reference should still be valid
        Specimen rereadSpecimen = fhirClient.read()
                .resource(Specimen.class)
                .withId(specimenId)
                .execute();

        assertEquals("Patient/" + patientId, rereadSpecimen.getSubject().getReference());
    }

    @Test
    void testMetadataEndpoint_ShowsBothModules() {
        // Get capability statement
        CapabilityStatement capabilities = fhirClient.capabilities()
                .ofType(CapabilityStatement.class)
                .execute();

        assertNotNull(capabilities);
        assertEquals("Test Hospital FHIR Platform", capabilities.getName());
        assertEquals("Test Hospital", capabilities.getPublisher());

        // Should have REST component
        assertNotNull(capabilities.getRest());
        assertFalse(capabilities.getRest().isEmpty());

        CapabilityStatement.CapabilityStatementRestComponent rest = capabilities.getRestFirstRep();

        // Should have Patient resource (EHR module)
        assertTrue(rest.getResource().stream()
                .anyMatch(resource -> "Patient".equals(resource.getType())));

        // Should have Specimen resource (Biobank module)
        assertTrue(rest.getResource().stream()
                .anyMatch(resource -> "Specimen".equals(resource.getType())));

        // Verify Patient operations
        CapabilityStatement.CapabilityStatementRestResourceComponent patientResource = 
                rest.getResource().stream()
                        .filter(resource -> "Patient".equals(resource.getType()))
                        .findFirst()
                        .orElse(null);

        assertNotNull(patientResource);
        assertTrue(patientResource.getOperation().stream()
                .anyMatch(op -> "patient-summary".equals(op.getName())));

        // Verify Specimen operations
        CapabilityStatement.CapabilityStatementRestResourceComponent specimenResource = 
                rest.getResource().stream()
                        .filter(resource -> "Specimen".equals(resource.getType()))
                        .findFirst()
                        .orElse(null);

        assertNotNull(specimenResource);
        assertTrue(specimenResource.getOperation().stream()
                .anyMatch(op -> "chain-of-custody".equals(op.getName())));
    }

    @Test
    void testModuleConfiguration_BothEnabled() {
        // This test verifies that both EHR and Biobank modules are active
        // by testing that resources from both modules can be created

        // Test EHR module
        Patient patient = new Patient();
        patient.addName().setFamily("ConfigTest").addGiven("EHR");
        patient.setBirthDate(java.sql.Date.valueOf("1990-01-01"));

        MethodOutcome ehrOutcome = fhirClient.create().resource(patient).execute();
        assertNotNull(ehrOutcome.getId());
        assertTrue(ehrOutcome.getCreated());

        // Test Biobank module
        Specimen specimen = new Specimen();
        specimen.setSubject(new Reference("Patient/" + ehrOutcome.getId().getIdPart()));
        specimen.getType().addCoding()
                .setSystem("http://snomed.info/sct")
                .setCode("119297000")
                .setDisplay("Blood specimen");
        specimen.getCollection().setCollected(new DateTimeType(new Date()));

        MethodOutcome biobankOutcome = fhirClient.create().resource(specimen).execute();
        assertNotNull(biobankOutcome.getId());
        assertTrue(biobankOutcome.getCreated());

        // Both modules are working if we reach here without exceptions
        assertTrue(true, "Both EHR and Biobank modules are active and functional");
    }
}