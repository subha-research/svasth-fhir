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
class SpecimenApiE2ETest extends BaseIntegrationTest {

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
    void testCreateAndRetrieveSpecimen_ViaRestApi() {
        // First create a patient
        Patient patient = new Patient();
        patient.addName().setFamily("SpecimenE2E").addGiven("TestPatient");
        patient.setBirthDate(java.sql.Date.valueOf("1990-01-01"));

        MethodOutcome patientOutcome = fhirClient.create().resource(patient).execute();
        String patientId = patientOutcome.getId().getIdPart();

        // Create specimen via REST API
        Specimen specimen = new Specimen();
        specimen.setSubject(new Reference("Patient/" + patientId));
        specimen.getType().addCoding()
                .setSystem("http://snomed.info/sct")
                .setCode("119297000")
                .setDisplay("Blood specimen");
        specimen.getCollection().setCollected(new DateTimeType(new Date()));
        specimen.getCollection().setQuantity(new Quantity().setValue(5).setUnit("mL"));
        specimen.addContainer()
                .getType().addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/v2-0487")
                .setCode("TUBE")
                .setDisplay("Tube");

        // POST /fhir/Specimen
        MethodOutcome outcome = fhirClient.create().resource(specimen).execute();
        assertNotNull(outcome.getId());
        assertTrue(outcome.getCreated());

        String specimenId = outcome.getId().getIdPart();

        // GET /fhir/Specimen/{id}
        Specimen retrievedSpecimen = fhirClient.read()
                .resource(Specimen.class)
                .withId(specimenId)
                .execute();

        assertNotNull(retrievedSpecimen);
        assertEquals("Patient/" + patientId, retrievedSpecimen.getSubject().getReference());
        assertEquals("119297000", retrievedSpecimen.getType().getCodingFirstRep().getCode());

        // Verify custom enrichment
        assertTrue(retrievedSpecimen.getIdentifier().stream()
                        .anyMatch(id -> id.getSystem().contains("biobank")),
                "Biobank ID should be auto-generated");

        assertNotNull(retrievedSpecimen.getCollection().getCollector());
        assertEquals("Test Hospital", retrievedSpecimen.getCollection().getCollector().getDisplay());
    }

    @Test
    void testSpecimenSearch_ViaRestApi() {
        // Create patient
        Patient patient = new Patient();
        patient.addName().setFamily("SpecimenSearchE2E").addGiven("TestPatient");
        patient.setBirthDate(java.sql.Date.valueOf("1990-01-01"));

        MethodOutcome patientOutcome = fhirClient.create().resource(patient).execute();
        String patientId = patientOutcome.getId().getIdPart();

        // Create specimen
        Specimen specimen = new Specimen();
        specimen.setSubject(new Reference("Patient/" + patientId));
        specimen.getType().addCoding()
                .setSystem("http://snomed.info/sct")
                .setCode("119297000")
                .setDisplay("Blood specimen");
        specimen.getCollection().setCollected(new DateTimeType(new Date()));

        MethodOutcome specimenOutcome = fhirClient.create().resource(specimen).execute();
        assertNotNull(specimenOutcome.getId());

        // Search by patient reference
        Bundle searchResults = fhirClient.search()
                .forResource(Specimen.class)
                .where(Specimen.SUBJECT.hasId(patientId))
                .returnBundle(Bundle.class)
                .execute();

        assertNotNull(searchResults);
        assertTrue(searchResults.getTotal() >= 1);

        Specimen foundSpecimen = (Specimen) searchResults.getEntryFirstRep().getResource();
        assertEquals("Patient/" + patientId, foundSpecimen.getSubject().getReference());
    }

    @Test
    void testSpecimenChainOfCustody_ViaRestApi() {
        // Create patient
        Patient patient = new Patient();
        patient.addName().setFamily("CustodyE2E").addGiven("TestPatient");
        patient.setBirthDate(java.sql.Date.valueOf("1990-01-01"));

        MethodOutcome patientOutcome = fhirClient.create().resource(patient).execute();
        String patientId = patientOutcome.getId().getIdPart();

        // Create specimen
        Specimen specimen = new Specimen();
        specimen.setSubject(new Reference("Patient/" + patientId));
        specimen.getType().addCoding()
                .setSystem("http://snomed.info/sct")
                .setCode("119297000")
                .setDisplay("Blood specimen");
        specimen.getCollection().setCollected(new DateTimeType(new Date()));

        MethodOutcome specimenOutcome = fhirClient.create().resource(specimen).execute();
        String specimenId = specimenOutcome.getId().getIdPart();

        // Call $chain-of-custody operation
        Bundle custody = fhirClient.operation()
                .onInstance(new IdType("Specimen", specimenId))
                .named("$chain-of-custody")
                .withNoParameters(Parameters.class)
                .returnResourceType(Bundle.class)
                .execute();

        assertNotNull(custody);
        assertEquals(Bundle.BundleType.COLLECTION, custody.getType());
        assertTrue(custody.getEntry().size() >= 1);

        // First entry should be the specimen
        Bundle.BundleEntryComponent specimenEntry = custody.getEntry().get(0);
        assertTrue(specimenEntry.getResource() instanceof Specimen);
    }

    @Test
    void testSpecimenValidation_ViaRestApi() {
        // Try to create invalid specimen (missing subject)
        Specimen invalidSpecimen = new Specimen();
        invalidSpecimen.getType().addCoding()
                .setSystem("http://snomed.info/sct")
                .setCode("119297000");

        // Should throw exception
        assertThrows(Exception.class, () -> {
            fhirClient.create().resource(invalidSpecimen).execute();
        });
    }
}