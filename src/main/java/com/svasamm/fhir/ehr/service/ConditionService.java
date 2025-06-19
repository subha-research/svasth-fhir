package com.svasamm.fhir.ehr.service;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;

@Service
@Transactional
@ConditionalOnClass(Condition.class)
@Profile("!test")

public class ConditionService {

    @Autowired
    private IFhirResourceDao<Condition> conditionDao;

    @Autowired
    private IFhirResourceDao<Patient> patientDao;

    @Autowired
    private IFhirResourceDao<Observation> observationDao;

    @Autowired
    private IFhirResourceDao<DiagnosticReport> diagnosticReportDao;

    @Autowired
    private IFhirResourceDao<Procedure> procedureDao;

    @Autowired
    private IFhirResourceDao<MedicationRequest> medicationRequestDao;

    public Condition getConditionById(String conditionId) {
        try {
            return conditionDao.read(new IdType(conditionId));
        } catch (Exception e) {
            return null;
        }
    }

    public MethodOutcome createCondition(Condition condition) {
        try {
            addAuditExtension(condition, "Created");
            return conditionDao.create(condition);
        } catch (Exception e) {
            throw new RuntimeException("Error creating condition: " + e.getMessage(), e);
        }
    }

    public List<Condition> searchConditions(
            ReferenceParam patient,
            ReferenceParam subject,
            TokenParam category,
            TokenParam code,
            TokenParam clinicalStatus,
            TokenParam verificationStatus,
            DateRangeParam onsetDate,
            TokenParam bodySite,
            NumberParam count) {
        SearchParameterMap searchMap = new SearchParameterMap();

        if (patient != null) {
            searchMap.add(Condition.SP_PATIENT, patient);
        }
        if (subject != null) {
            searchMap.add(Condition.SP_SUBJECT, subject);
        }
        if (category != null) {
            searchMap.add(Condition.SP_CATEGORY, category);
        }
        if (code != null) {
            searchMap.add(Condition.SP_CODE, code);
        }
        if (clinicalStatus != null) {
            searchMap.add(Condition.SP_CLINICAL_STATUS, clinicalStatus);
        }
        if (verificationStatus != null) {
            searchMap.add(Condition.SP_VERIFICATION_STATUS, verificationStatus);
        }
        if (onsetDate != null) {
            searchMap.add(Condition.SP_ONSET_DATE, onsetDate);
        }
        if (bodySite != null) {
            searchMap.add(Condition.SP_BODY_SITE, bodySite);
        }

        int searchCount = count != null ? count.getValue().intValue() : 50;
        searchMap.setCount(searchCount);

        return conditionDao.search(searchMap)
                .getResources(0, searchCount)
                .stream()
                .map(resource -> (Condition) resource)
                .collect(Collectors.toList());
    }

    public List<Condition> getConditionsByPatient(String patientReference) {
        SearchParameterMap searchMap = new SearchParameterMap();
        searchMap.add(Condition.SP_PATIENT, new ReferenceParam(patientReference));

        return conditionDao.search(searchMap)
                .getAllResources()
                .stream()
                .map(resource -> (Condition) resource)
                .collect(Collectors.toList());
    }

    public List<Condition> getConditionByCode(String system, String code) {
        SearchParameterMap searchMap = new SearchParameterMap();

        if (system != null && code != null) {
            searchMap.add(Condition.SP_CODE, new TokenParam(system, code));
        } else if (code != null) {
            searchMap.add(Condition.SP_CODE, new TokenParam(code));
        }

        return conditionDao.search(searchMap)
                .getAllResources()
                .stream()
                .map(resource -> (Condition) resource)
                .collect(Collectors.toList());
    }

    public Bundle generateConditionSummary(String conditionId) {
        Condition condition = getConditionById(conditionId);
        if (condition == null) {
            throw new RuntimeException("Condition not found with ID: " + conditionId);
        }

        Bundle summary = new Bundle();
        summary.setType(Bundle.BundleType.COLLECTION);
        summary.setId(UUID.randomUUID().toString());
        summary.setTimestamp(new Date());

        summary.addEntry()
                .setResource(condition)
                .setFullUrl("Condition/" + conditionId);

        if (condition.getSubject() != null) {
            String patientId = extractIdFromReference(condition.getSubject().getReference());
            addPatientToBundle(summary, patientId);
        }

        addRelatedObservations(summary, conditionId, 10);
        addRelatedDiagnosticReports(summary, conditionId, 5);
        addRelatedProcedures(summary, conditionId, 5);
        addRelatedMedications(summary, conditionId);

        return summary;
    }

    public Bundle generateConditionTimeline(String conditionId, Date startDate, Date endDate) {
        Condition condition = getConditionById(conditionId);
        if (condition == null) {
            throw new RuntimeException("Condition not found: " + conditionId);
        }

        Bundle timeline = new Bundle();
        timeline.setType(Bundle.BundleType.COLLECTION);
        timeline.setId(UUID.randomUUID().toString());
        timeline.setTimestamp(new Date());
        // Add the condition
        timeline.addEntry()
                .setResource(condition)
                .setFullUrl("Condition/" + conditionId);
        if (startDate != null && endDate != null) {
            addTimelineDataInDateRange(timeline, conditionId, startDate, endDate);
        } else {
            // Default to last 90 days
            Calendar cal = Calendar.getInstance();
            Date end = cal.getTime();
            cal.add(Calendar.DAY_OF_MONTH, -90);
            Date start = cal.getTime();
            addTimelineDataInDateRange(timeline, conditionId, start, end);
        }
        return timeline;
    }

    public Bundle getPatientConditionsBundle(String patientId, boolean activeOnly, TokenParam category) {
        String patientReference = "Patient/" + patientId;

        SearchParameterMap searchMap = new SearchParameterMap();
        searchMap.add(Condition.SP_PATIENT, new ReferenceParam(patientReference));

        if (activeOnly) {
            searchMap.add(Condition.SP_CLINICAL_STATUS,
                    new TokenParam("http://terminology.hl7.org/CodeSystem/condition-clinical", "active"));
        }

        if (category != null) {
            searchMap.add(Condition.SP_CATEGORY, category);
        }
        List<Condition> conditions = conditionDao.search(searchMap)
                .getAllResources()
                .stream()
                .map(resource -> (Condition) resource)
                .collect(Collectors.toList());
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setId(UUID.randomUUID().toString());
        bundle.setTimestamp(new Date());
        bundle.setTotal(conditions.size());
        // Add conditions to bundle
        for (Condition condition : conditions) {
            bundle.addEntry()
                    .setResource(condition)
                    .setFullUrl("Condition/" + condition.getIdElement().getIdPart())
                    .getSearch()
                    .setMode(Bundle.SearchEntryMode.MATCH);
        }
        return bundle;
    }

    /**
     * Create a sample colorectal carcinoma condition based on FHIR JSON
     */
    public Condition createSampleColorectalCondition(String patientId) {
        Condition condition = new Condition();

        // Subject reference
        condition.setSubject(new Reference("Patient/" + patientId));

        // Category
        condition.addCategory()
                .addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/condition-category")
                .setCode("encounter-diagnosis")
                .setDisplay("Encounter Diagnosis");

        // Code (Colorectal carcinoma)
        condition.getCode()
                .addCoding()
                .setSystem("http://snomed.info/sct")
                .setCode("363406005")
                .setDisplay("Malignant tumor of colon");
        condition.getCode().setText("Colorectal carcinoma");

        // Body site
        condition.addBodySite()
                .addCoding()
                .setSystem("http://snomed.info/sct")
                .setCode("71854001")
                .setDisplay("Colon structure");
        condition.getBodySite().get(0).setText("Colon");

        // Onset date
        condition.setOnset(new DateTimeType("2023-06-26"));

        // Stage
        Condition.ConditionStageComponent stage = condition.addStage();
        stage.getSummary()
                .addCoding()
                .setSystem("http://snomed.info/sct")
                .setCode("261663004")
                .setDisplay("Stage IIIB");
        stage.getSummary().setText("IIIB");

        // Clinical status
        condition.getClinicalStatus()
                .addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/condition-clinical")
                .setCode("active")
                .setDisplay("Active");

        // Verification status
        condition.getVerificationStatus()
                .addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/condition-ver-status")
                .setCode("confirmed")
                .setDisplay("Confirmed");

        return condition;
    }

    private void addAuditExtension(Condition condition, String action) {
        Extension auditExtension = new Extension();
        auditExtension.setUrl("http://hospital.local/fhir/StructureDefinition/audit-trail");
        auditExtension.addExtension("action", new StringType(action));
        auditExtension.addExtension("timestamp", new DateTimeType(new Date()));
        auditExtension.addExtension("user", new StringType("system"));
        condition.addExtension(auditExtension);
    }

    private void preserveSystemGeneratedData(Condition existing, Condition updated) {
        // Preserve system-generated extensions and metadata
        existing.getExtension().stream()
                .filter(ext -> ext.getUrl().contains("system-generated") || ext.getUrl().contains("audit"))
                .forEach(ext -> {
                    if (!extensionExists(updated, ext)) {
                        updated.addExtension(ext);
                    }
                });
        // Preserve meta information
        if (existing.getMeta() != null) {
            if (updated.getMeta() == null) {
                updated.setMeta(existing.getMeta());
            } else {
                // Preserve system tags
                existing.getMeta().getTag().stream()
                        .filter(tag -> tag.getSystem() != null && tag.getSystem().contains("system"))
                        .forEach(tag -> updated.getMeta().addTag(tag));
            }
        }
    }

    private boolean extensionExists(Condition condition, Extension extension) {
        return condition.getExtension().stream()
                .anyMatch(existing -> existing.getUrl().equals(extension.getUrl()));
    }

    private String extractIdFromReference(String reference) {
        if (reference == null) {
            return null;
        }
        if (reference.contains("/")) {
            return reference.substring(reference.lastIndexOf("/") + 1);
        }
        return reference;
    }

    private void addPatientToBundle(Bundle bundle, String patientId) {
        try {
            Patient patient = patientDao.read(new IdType(patientId));
            if (patient != null) {
                bundle.addEntry()
                        .setResource(patient)
                        .setFullUrl("Patient/" + patientId);
            }
        } catch (Exception e) {
            // Log error but don't fail the operation
        }
    }

    private void addRelatedObservations(Bundle bundle, String conditionId, int count) {
        try {
            SearchParameterMap searchMap = new SearchParameterMap();
            searchMap.add("focus", new ReferenceParam("Condition/" + conditionId));
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

    private void addRelatedDiagnosticReports(Bundle bundle, String conditionId, int count) {
        try {
            SearchParameterMap searchMap = new SearchParameterMap();
            searchMap.add("subject", new ReferenceParam("Condition/" + conditionId));
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

    private void addRelatedProcedures(Bundle bundle, String conditionId, int count) {
        try {
            SearchParameterMap searchMap = new SearchParameterMap();
            searchMap.add("reason-reference", new ReferenceParam("Condition/" + conditionId));
            searchMap.add("_sort", new StringParam("-date"));
            searchMap.setCount(count);
            procedureDao.search(searchMap)
                    .getResources(0, count)
                    .forEach(resource -> {
                        Procedure procedure = (Procedure) resource;
                        bundle.addEntry()
                                .setResource(procedure)
                                .setFullUrl("Procedure/" + procedure.getIdElement().getIdPart());
                    });
        } catch (Exception e) {
            // Log error but don't fail the operation
        }
    }

    private void addRelatedMedications(Bundle bundle, String conditionId) {
        try {
            SearchParameterMap searchMap = new SearchParameterMap();
            searchMap.add("reason-reference", new ReferenceParam("Condition/" + conditionId));
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

    private void addTimelineDataInDateRange(Bundle bundle, String conditionId, Date startDate, Date endDate) {
        DateRangeParam dateRange = new DateRangeParam(startDate, endDate);
        // Add related observations in range
        try {
            SearchParameterMap obsSearch = new SearchParameterMap();
            obsSearch.add("focus", new ReferenceParam("Condition/" + conditionId));
            obsSearch.add("date", dateRange);
            obsSearch.add("_sort", new StringParam("date"));
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
        // Add related procedures in range
        try {
            SearchParameterMap procSearch = new SearchParameterMap();
            procSearch.add("reason-reference", new ReferenceParam("Condition/" + conditionId));
            procSearch.add("date", dateRange);
            procSearch.add("_sort", new StringParam("date"));
            procedureDao.search(procSearch)
                    .getAllResources()
                    .forEach(resource -> {
                        Procedure procedure = (Procedure) resource;
                        bundle.addEntry()
                                .setResource(procedure)
                                .setFullUrl("Procedure/" + procedure.getIdElement().getIdPart());
                    });
        } catch (Exception e) {
            // Log error but continue
        }
        // Add related diagnostic reports in range
        try {
            SearchParameterMap reportSearch = new SearchParameterMap();
            reportSearch.add("reason-reference", new ReferenceParam("Condition/" + conditionId));
            reportSearch.add("date", dateRange);
            reportSearch.add("_sort", new StringParam("date"));
            diagnosticReportDao.search(reportSearch)
                    .getAllResources()
                    .forEach(resource -> {
                        DiagnosticReport report = (DiagnosticReport) resource;
                        bundle.addEntry()
                                .setResource(report)
                                .setFullUrl("DiagnosticReport/" + report.getIdElement().getIdPart());
                    });
        } catch (Exception e) {
            // Log error but continue
        }
    }
}
