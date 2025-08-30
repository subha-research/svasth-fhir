package com.svasamm.fhir.ehr.service;
// import ca.uhn.fhir.jpa.dao.IFhirResourceDao;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;

@Service
@Transactional
@ConditionalOnClass(Patient.class)
@Profile("!test")
public class PatientService {

    @Autowired
    private IFhirResourceDao<Patient> patientDao;
    @Autowired
    private IFhirResourceDao<Encounter> encounterDao;
    @Autowired
    private IFhirResourceDao<Observation> observationDao;
    @Autowired
    private IFhirResourceDao<DiagnosticReport> diagnosticReportDao;
    @Autowired
    private IFhirResourceDao<MedicationRequest> medicationRequestDao;

    public Patient getPatientById(String patientId) {
        try {
            return patientDao.read(new IdType(patientId));
        } catch (Exception e) {
            return null;
        }
    }

    public MethodOutcome createPatient(Patient patient) {
        addAuditExtension(patient, "created");
        return patientDao.create(patient);
    }

    public MethodOutcome updatePatient(String patientId, Patient patient) {
        Patient existingPatient = getPatientById(patientId);
        if (existingPatient == null) {
            throw new RuntimeException("Patient not found: " + patientId);
        }
        preserveSystemGeneratedData(existingPatient, patient);
        addAuditExtension(patient, "updated");
        patient.setId(patientId);
        return patientDao.update(patient);
    }

    public List<Patient> searchPatients(StringParam family, StringParam given,
            TokenParam identifier, DateParam birthDate,
            TokenParam active, NumberParam count) {
        SearchParameterMap searchMap = new SearchParameterMap();
        if (family != null) {
            searchMap.add(Patient.SP_FAMILY, family);
        }
        if (given != null) {
            searchMap.add(Patient.SP_GIVEN, given);
        }
        if (identifier != null) {
            searchMap.add(Patient.SP_IDENTIFIER, identifier);
        }
        if (birthDate != null) {
            searchMap.add(Patient.SP_BIRTHDATE, birthDate);
        }
        if (active != null) {
            searchMap.add(Patient.SP_ACTIVE, active);
        }
        int searchCount = count != null ? count.getValue().intValue() : 50;
        searchMap.setCount(searchCount);
        return patientDao.search(searchMap)
                .getResources(0, searchCount)
                .stream()
                .map(resource -> (Patient) resource)
                .collect(Collectors.toList());
    }

    public Bundle generatePatientSummary(String patientId) {
        Patient patient = getPatientById(patientId);
        if (patient == null) {
            throw new RuntimeException("Patient not found: " + patientId);
        }
        Bundle summary = new Bundle();
        summary.setType(Bundle.BundleType.COLLECTION);
        summary.setId(UUID.randomUUID().toString());
        summary.setTimestamp(new Date());
        // Add patient
        summary.addEntry()
                .setResource(patient)
                .setFullUrl("Patient/" + patientId);
        // Add recent encounters
        addRecentEncounters(summary, patientId, 5);
        // Add recent vital signs
        addRecentVitalSigns(summary, patientId, 10);
        // Add active medications
        addActiveMedications(summary, patientId);
        // Add recent lab results
        addRecentLabResults(summary, patientId, 5);
        return summary;
    }

    public Bundle generatePatientChart(String patientId, Date startDate, Date endDate) {
        Patient patient = getPatientById(patientId);
        if (patient == null) {
            throw new RuntimeException("Patient not found: " + patientId);
        }
        Bundle chart = new Bundle();
        chart.setType(Bundle.BundleType.COLLECTION);
        chart.setId(UUID.randomUUID().toString());
        chart.setTimestamp(new Date());
        // Add patient
        chart.addEntry()
                .setResource(patient)
                .setFullUrl("Patient/" + patientId);
        if (startDate != null && endDate != null) {
            addDataInDateRange(chart, patientId, startDate, endDate);
        } else {
            // Default to last 30 days
            Calendar cal = Calendar.getInstance();
            Date end = cal.getTime();
            cal.add(Calendar.DAY_OF_MONTH, -30);
            Date start = cal.getTime();
            addDataInDateRange(chart, patientId, start, end);
        }
        return chart;
    }

    public MethodOutcome mergePatients(String sourcePatientId, String targetPatientId) {
        Patient sourcePatient = getPatientById(sourcePatientId);
        Patient targetPatient = getPatientById(targetPatientId);
        if (sourcePatient == null || targetPatient == null) {
            throw new RuntimeException("One or both patients not found");
        }
        // Merge identifiers
        sourcePatient.getIdentifier().forEach(identifier -> {
            if (!identifierExists(targetPatient, identifier)) {
                targetPatient.addIdentifier(identifier);
            }
        });
        // Merge contact info
        sourcePatient.getTelecom().forEach(telecom -> {
            if (!telecomExists(targetPatient, telecom)) {
                targetPatient.addTelecom(telecom);
            }
        });
        // Add merge history extension
        Extension mergeExtension = new Extension();
        mergeExtension.setUrl("http://hospital.local/fhir/StructureDefinition/patient-merge");
        mergeExtension.addExtension("merged-from", new StringType(sourcePatientId));
        mergeExtension.addExtension("merge-date", new DateTimeType(new Date()));
        targetPatient.addExtension(mergeExtension);
        // Deactivate source patient
        sourcePatient.setActive(false);
        patientDao.update(sourcePatient);
        return patientDao.update(targetPatient);
    }

    private void addAuditExtension(Patient patient, String action) {
        Extension auditExtension = new Extension();
        auditExtension.setUrl("http://hospital.local/fhir/StructureDefinition/audit-trail");
        auditExtension.addExtension("action", new StringType(action));
        auditExtension.addExtension("timestamp", new DateTimeType(new Date()));
        auditExtension.addExtension("user", new StringType("system"));
        patient.addExtension(auditExtension);
    }

    private void preserveSystemGeneratedData(Patient existing, Patient updated) {
        // Preserve MRN and other system-generated identifiers
        existing.getIdentifier().stream()
                .filter(id -> "MR".equals(id.getType().getCodingFirstRep().getCode()))
                .forEach(mrn -> {
                    if (!identifierExists(updated, mrn)) {
                        updated.addIdentifier(mrn);
                    }
                });
    }

    private boolean identifierExists(Patient patient, Identifier identifier) {
        return patient.getIdentifier().stream()
                .anyMatch(existing -> existing.getSystem().equals(identifier.getSystem())
                && existing.getValue().equals(identifier.getValue()));
    }

    private boolean telecomExists(Patient patient, ContactPoint telecom) {
        return patient.getTelecom().stream()
                .anyMatch(existing -> existing.getSystem().equals(telecom.getSystem())
                && existing.getValue().equals(telecom.getValue()));
    }

    private void addRecentEncounters(Bundle bundle, String patientId, int count) {
        try {
            SearchParameterMap searchMap = new SearchParameterMap();
            searchMap.add("patient", new ReferenceParam(patientId));
            searchMap.add("_sort", new StringParam("-date"));
            searchMap.setCount(count);
            encounterDao.search(searchMap)
                    .getResources(0, count)
                    .forEach(resource -> {
                        Encounter encounter = (Encounter) resource;
                        bundle.addEntry()
                                .setResource(encounter)
                                .setFullUrl("Encounter/" + encounter.getIdElement().getIdPart());
                    });
        } catch (Exception e) {
            // Log error but don't fail the operation
        }
    }

    private void addRecentVitalSigns(Bundle bundle, String patientId, int count) {
        try {
            SearchParameterMap searchMap = new SearchParameterMap();
            searchMap.add("patient", new ReferenceParam(patientId));
            searchMap.add("category", new TokenParam("vital-signs"));
            searchMap.add("_sort", new StringParam("-date"));
            searchMap.setCount(count);
            observationDao.search(searchMap)
                    .getResources(0, count)
                    .forEach(resource -> {
                        Observation observation = (Observation) resource;
                        bundle.addEntry()
                                .setResource(observation)
                                .setFullUrl("Observation/" + observation.getIdElement().getIdPart());
                    });
        } catch (Exception e) {
            // Log error but don't fail the operation
        }
    }

    private void addActiveMedications(Bundle bundle, String patientId) {
        try {
            SearchParameterMap searchMap = new SearchParameterMap();
            searchMap.add("patient", new ReferenceParam(patientId));
            searchMap.add("status", new TokenParam("active"));
            medicationRequestDao.search(searchMap)
                    .getAllResources()
                    .forEach(resource -> {
                        MedicationRequest medication = (MedicationRequest) resource;
                        bundle.addEntry()
                                .setResource(medication)
                                .setFullUrl("MedicationRequest/" + medication.getIdElement().getIdPart());
                    });
        } catch (Exception e) {
            // Log error but don't fail the operation
        }
    }

    private void addRecentLabResults(Bundle bundle, String patientId, int count) {
        try {
            SearchParameterMap searchMap = new SearchParameterMap();
            searchMap.add("patient", new ReferenceParam(patientId));
            searchMap.add("category", new TokenParam("LAB"));
            searchMap.add("_sort", new StringParam("-date"));
            searchMap.setCount(count);
            diagnosticReportDao.search(searchMap)
                    .getResources(0, count)
                    .forEach(resource -> {
                        DiagnosticReport report = (DiagnosticReport) resource;
                        bundle.addEntry()
                                .setResource(report)
                                .setFullUrl("DiagnosticReport/" + report.getIdElement().getIdPart());
                    });
        } catch (Exception e) {
            // Log error but don't fail the operation
        }
    }

    private void addDataInDateRange(Bundle bundle, String patientId, Date startDate, Date endDate) {
        DateRangeParam dateRange = new DateRangeParam(startDate, endDate);
        // Add encounters in range
        try {
            SearchParameterMap encounterSearch = new SearchParameterMap();
            encounterSearch.add("patient", new ReferenceParam(patientId));
            encounterSearch.add("date", dateRange);
            encounterDao.search(encounterSearch)
                    .getAllResources()
                    .forEach(resource -> {
                        Encounter encounter = (Encounter) resource;
                        bundle.addEntry()
                                .setResource(encounter)
                                .setFullUrl("Encounter/" + encounter.getIdElement().getIdPart());
                    });
        } catch (Exception e) {
            // Log error but continue
        }
        // Add observations in range
        try {
            SearchParameterMap obsSearch = new SearchParameterMap();
            obsSearch.add("patient", new ReferenceParam(patientId));
            obsSearch.add("date", dateRange);
            observationDao.search(obsSearch)
                    .getAllResources()
                    .forEach(resource -> {
                        Observation observation = (Observation) resource;
                        bundle.addEntry()
                                .setResource(observation)
                                .setFullUrl("Observation/" + observation.getIdElement().getIdPart());
                    });
        } catch (Exception e) {
            // Log error but continue
        }
    }
}
