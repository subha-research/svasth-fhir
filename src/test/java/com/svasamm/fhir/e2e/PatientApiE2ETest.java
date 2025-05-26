package com.svasamm.fhir.e2e;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.svasamm.fhir.BaseIntegrationTest;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.*;

@org.springframework.boot.test.context.SpringBootTest(webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
class PatientApiE2ETest extends BaseIntegrationTest {

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
    void testCreateAndRetrievePatient_ViaRestApi() {
        // Create patient via REST API
        Patient patient = new Patient();
        patient.addName().setFamily("E2ETest").addGiven("Patient");
        patient.setGender(Enumerations.AdministrativeGender.MALE);
        patient.setBirthDate(java.sql.Date.valueOf("1990-01-01"));
        patient.addTelecom()
            .setSystem(ContactPoint.ContactPointSystem.EMAIL)
            .setValue("e2e@test.com");

        // POST /fhir/Patient
        MethodOutcome outcome = fhirClient.create().resource(patient).execute();
        assertNotNull(outcome.getId());
        assertTrue(outcome.getCreated());

        String patientId = outcome.getId().getIdPart();

        // GET /fhir/Patient/{id}
        Patient retrievedPatient = fhirClient.read()
                .resource(Patient.class)
                .withId(patientId)
                .execute();

        assertNotNull(retrievedPatient);
        assertEquals("E2ETest", retrievedPatient.getNameFirstRep().getFamily());
        assertEquals("Patient", retrievedPatient.getNameFirstRep().getGiven().get(0).getValue());

        // Verify custom enrichment
        assertTrue(retrievedPatient.getIdentifier().stream()
            .anyMatch(id -> "MR".equals(id.getType().getCodingFirstRep().getCode())),
            "MRN should be auto-generated");

        assertNotNull(retrievedPatient.getManagingOrganization());
        assertEquals("Test Hospital", retrievedPatient.getManagingOrganization().getDisplay());
    }

    @Test
    void testPatientSearch_ViaRestApi() {
        // Create test patient
        Patient patient = new Patient();
        patient.addName().setFamily("SearchE2E").addGiven("TestPatient");
        patient.setBirthDate(java.sql.Date.valueOf("1985-05-15"));
        
        MethodOutcome outcome = fhirClient.create().resource(patient).execute();
        assertNotNull(outcome.getId());

        // Search by family name
        Bundle searchResults = fhirClient.search()
                .forResource(Patient.class)
                .where(Patient.FAMILY.matches().value("SearchE2E"))
                .returnBundle(Bundle.class)
                .execute();

        assertNotNull(searchResults);
        assertTrue(searchResults.getTotal() >= 1);
        
        Patient foundPatient = (Patient) searchResults.getEntryFirstRep().getResource();
        assertEquals("SearchE2E", foundPatient.getNameFirstRep().getFamily());
        assertEquals("TestPatient", foundPatient.getNameFirstRep().getGiven().get(0).getValue());
    }

    @Test
    void testPatientSummaryOperation_ViaRestApi() {
        // Create patient
        Patient patient = new Patient();
        patient.addName().setFamily("SummaryE2E").addGiven("TestPatient");
        patient.setBirthDate(java.sql.Date.valueOf("1990-01-01"));
        
        MethodOutcome outcome = fhirClient.create().resource(patient).execute();
        String patientId = outcome.getId().getIdPart();

        // Call $patient-summary operation
        Bundle summary = fhirClient.operation()
                .onInstance(new IdType("Patient", patientId))
                .named("$patient-summary")
                .withNoParameters(Parameters.class)
                .returnResourceType(Bundle.class)
                .execute();

        assertNotNull(summary);
        assertEquals(Bundle.BundleType.COLLECTION, summary.getType());
        assertTrue(summary.getEntry().size() >= 1);
        
        // First entry should be the patient
        Bundle.BundleEntryComponent patientEntry = summary.getEntry().get(0);
        assertTrue(patientEntry.getResource() instanceof Patient);
    }

    @Test
    void testPatientUpdate_ViaRestApi() {
        // Create patient
        Patient patient = new Patient();
        patient.addName().setFamily("UpdateE2E").addGiven("TestPatient");
        patient.setBirthDate(java.sql.Date.valueOf("1990-01-01"));
        MethodOutcome createOutcome = fhirClient.create().resource(patient).execute();
       String patientId = createOutcome.getId().getIdPart();

       // Read patient
       Patient readPatient = fhirClient.read()
               .resource(Patient.class)
               .withId(patientId)
               .execute();

       // Update patient
       readPatient.getNameFirstRep().setFamily("UpdatedE2E");
       readPatient.addTelecom()
               .setSystem(ContactPoint.ContactPointSystem.PHONE)
               .setValue("555-1234");

       // PUT /fhir/Patient/{id}
       MethodOutcome updateOutcome = fhirClient.update()
               .resource(readPatient)
               .execute();

       assertNotNull(updateOutcome);
       assertFalse(updateOutcome.getCreated()); // Should be false for update

       // Read updated patient
       Patient updatedPatient = fhirClient.read()
               .resource(Patient.class)
               .withId(patientId)
               .execute();

       assertEquals("UpdatedE2E", updatedPatient.getNameFirstRep().getFamily());
       assertTrue(updatedPatient.getTelecom().stream()
               .anyMatch(telecom -> "555-1234".equals(telecom.getValue())));
   }

   @Test
   void testPatientValidation_ViaRestApi() {
       // Try to create invalid patient (missing name)
       Patient invalidPatient = new Patient();
       invalidPatient.setGender(Enumerations.AdministrativeGender.MALE);

       // Should throw exception
       assertThrows(Exception.class, () -> {
           fhirClient.create().resource(invalidPatient).execute();
       });
   }

   @Test
   void testPatientNotFound_ViaRestApi() {
       // Try to read non-existent patient
       assertThrows(ResourceNotFoundException.class, () -> {
           fhirClient.read()
                   .resource(Patient.class)
                   .withId("non-existent-id")
                   .execute();
       });
   }
}