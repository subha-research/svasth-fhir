package com.svasamm.fhir.ehr.service;

import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.*;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@ConditionalOnClass(Observation.class)
@Profile("!test")
public class LabResultsService {

	private static final Logger logger = LoggerFactory.getLogger(LabResultsService.class);

	@Autowired
	private IFhirResourceDao<Observation> observationDao;

	@Autowired
	private IFhirResourceDao<Patient> patientDao;

	@Autowired
	private IFhirResourceDao<Encounter> encounterDao;

	@Autowired(required = false)
	private IFhirResourceDao<DiagnosticReport> diagnosticReportDao;

	// Common lab test LOINC codes
	private static final Map<String, String> LAB_TEST_CODES = Map.of(
			"33747-0", "Hemoglobin A1c",
			"2339-0", "Glucose",
			"2093-3", "Total Cholesterol",
			"33746-2", "LDL Cholesterol",
			"2085-9", "HDL Cholesterol",
			"3094-0", "BUN",
			"2160-0", "Creatinine",
			"6768-6", "Alkaline Phosphatase",
			"1742-6", "ALT",
			"1920-8", "AST");

	public Observation getLabResultById(String labResultId) {
		try {
			Observation obs = observationDao.read(new IdType(labResultId));
			return isLabResult(obs) ? obs : null;
		} catch (Exception e) {
			logger.error("Error reading lab result {}: ", labResultId, e);
			return null;
		}
	}

	public MethodOutcome createLabResult(Observation labResult) {
		try {
			addAuditExtension(labResult, "created");
			ensureLabCategory(labResult);
			return observationDao.create(labResult);
		} catch (Exception e) {
			logger.error("Error creating lab result: ", e);
			throw new RuntimeException("Failed to create lab result: " + e.getMessage());
		}
	}

	public MethodOutcome updateLabResult(String labResultId, Observation labResult) {
		try {
			Observation existingLabResult = getLabResultById(labResultId);
			if (existingLabResult == null) {
				throw new RuntimeException("Lab Result not found: " + labResultId);
			}

			preserveSystemGeneratedData(existingLabResult, labResult);
			addAuditExtension(labResult, "updated");
			ensureLabCategory(labResult);
			labResult.setId(labResultId);
			return observationDao.update(labResult);
		} catch (Exception e) {
			logger.error("Error updating lab result {}: ", labResultId, e);
			throw new RuntimeException("Failed to update lab result: " + e.getMessage());
		}
	}

	public List<Observation> searchLabResults(ReferenceParam subject, ReferenceParam patient,
			TokenParam code, DateRangeParam date, TokenParam status,
			ReferenceParam encounter, QuantityParam valueQuantity, NumberParam count) {
		try {
			SearchParameterMap searchMap = new SearchParameterMap();

			// Always filter for laboratory category
			searchMap.add(Observation.SP_CATEGORY, new TokenParam("laboratory"));

			if (subject != null) {
				searchMap.add(Observation.SP_SUBJECT, subject);
			}
			if (patient != null) {
				searchMap.add(Observation.SP_PATIENT, patient);
			}
			if (code != null) {
				searchMap.add(Observation.SP_CODE, code);
			}
			if (date != null) {
				searchMap.add(Observation.SP_DATE, date);
			}
			if (status != null) {
				searchMap.add(Observation.SP_STATUS, status);
			}
			if (encounter != null) {
				searchMap.add(Observation.SP_ENCOUNTER, encounter);
			}
			if (valueQuantity != null) {
				searchMap.add(Observation.SP_VALUE_QUANTITY, valueQuantity);
			}

			int searchCount = count != null ? count.getValue().intValue() : 50;
			searchMap.setCount(searchCount);

			return observationDao.search(searchMap)
					.getResources(0, searchCount)
					.stream()
					.map(resource -> (Observation) resource)
					.collect(Collectors.toList());
		} catch (Exception e) {
			logger.error("Error searching lab results: ", e);
			return Collections.emptyList();
		}
	}

	public Bundle generateLabPanel(String patientId) {
		try {
			logger.info("Generating lab panel for patient: {}", patientId);

			Patient patient = getPatientById(patientId);
			if (patient == null) {
				throw new RuntimeException("Patient not found: " + patientId);
			}

			Bundle panel = new Bundle();
			panel.setType(Bundle.BundleType.COLLECTION);
			panel.setId(UUID.randomUUID().toString());
			panel.setTimestamp(new Date());

			// Add patient
			panel.addEntry()
					.setResource(patient)
					.setFullUrl("Patient/" + patientId);

			// Get latest lab results for each common test type
			for (String loincCode : LAB_TEST_CODES.keySet()) {
				List<Observation> latestLabs = getLatestLabResultsByCode(patientId, loincCode, 1);
				latestLabs.forEach(lab -> panel.addEntry()
						.setResource(lab)
						.setFullUrl("Observation/" + lab.getIdElement().getIdPart()));
			}

			logger.info("Generated lab panel with {} entries", panel.getEntry().size());
			return panel;
		} catch (Exception e) {
			logger.error("Error generating lab panel for {}: ", patientId, e);
			throw new RuntimeException("Failed to generate lab panel: " + e.getMessage());
		}
	}

	public Bundle getLabTrends(String patientId, TokenParam code, Date startDate, Date endDate) {
		try {
			logger.info("Getting lab trends for patient: {}", patientId);

			Patient patient = getPatientById(patientId);
			if (patient == null) {
				throw new RuntimeException("Patient not found: " + patientId);
			}

			Bundle trend = new Bundle();
			trend.setType(Bundle.BundleType.COLLECTION);
			trend.setId(UUID.randomUUID().toString());
			trend.setTimestamp(new Date());

			SearchParameterMap searchMap = new SearchParameterMap();
			searchMap.add(Observation.SP_PATIENT, new ReferenceParam(patientId));
			searchMap.add(Observation.SP_CATEGORY, new TokenParam("laboratory"));

			if (code != null) {
				searchMap.add(Observation.SP_CODE, code);
			}

			if (startDate != null && endDate != null) {
				DateRangeParam dateRange = new DateRangeParam(startDate, endDate);
				searchMap.add(Observation.SP_DATE, dateRange);
			} else {
				// Default to last 6 months
				Calendar cal = Calendar.getInstance();
				Date end = cal.getTime();
				cal.add(Calendar.MONTH, -6);
				Date start = cal.getTime();
				searchMap.add(Observation.SP_DATE, new DateRangeParam(start, end));
			}

			searchMap.add("_sort", new StringParam("date"));

			observationDao.search(searchMap)
					.getAllResources()
					.forEach(resource -> {
						Observation lab = (Observation) resource;
						trend.addEntry()
								.setResource(lab)
								.setFullUrl("Observation/" + lab.getIdElement().getIdPart());
					});

			logger.info("Found {} lab trend results", trend.getEntry().size());
			return trend;
		} catch (Exception e) {
			logger.error("Error getting lab trends for {}: ", patientId, e);
			throw new RuntimeException("Failed to get lab trends: " + e.getMessage());
		}
	}

	public Bundle getAbnormalResults(String patientId, int periodDays) {
		try {
			logger.info("Getting abnormal results for patient: {}", patientId);

			Patient patient = getPatientById(patientId);
			if (patient == null) {
				throw new RuntimeException("Patient not found: " + patientId);
			}

			Bundle abnormal = new Bundle();
			abnormal.setType(Bundle.BundleType.COLLECTION);
			abnormal.setId(UUID.randomUUID().toString());
			abnormal.setTimestamp(new Date());

			// Calculate time range
			Calendar cal = Calendar.getInstance();
			Date endDate = cal.getTime();
			cal.add(Calendar.DAY_OF_MONTH, -periodDays);
			Date startDate = cal.getTime();

			SearchParameterMap searchMap = new SearchParameterMap();
			searchMap.add(Observation.SP_PATIENT, new ReferenceParam(patientId));
			searchMap.add(Observation.SP_CATEGORY, new TokenParam("laboratory"));
			searchMap.add(Observation.SP_DATE, new DateRangeParam(startDate, endDate));
			searchMap.add("_sort", new StringParam("-date"));

			// Filter for abnormal results (interpretation codes)
			List<Observation> allResults = observationDao.search(searchMap)
					.getAllResources()
					.stream()
					.map(resource -> (Observation) resource)
					.filter(this::isAbnormalResult)
					.collect(Collectors.toList());

			allResults.forEach(lab -> abnormal.addEntry()
					.setResource(lab)
					.setFullUrl("Observation/" + lab.getIdElement().getIdPart()));

			logger.info("Found {} abnormal results", abnormal.getEntry().size());
			return abnormal;
		} catch (Exception e) {
			logger.error("Error getting abnormal results for {}: ", patientId, e);
			throw new RuntimeException("Failed to get abnormal results: " + e.getMessage());
		}
	}

	public Bundle getCriticalValues(String patientId, String encounterId) {
		try {
			logger.info("Getting critical values for patient: {}", patientId);

			Patient patient = getPatientById(patientId);
			if (patient == null) {
				throw new RuntimeException("Patient not found: " + patientId);
			}

			Bundle critical = new Bundle();
			critical.setType(Bundle.BundleType.COLLECTION);
			critical.setId(UUID.randomUUID().toString());
			critical.setTimestamp(new Date());

			SearchParameterMap searchMap = new SearchParameterMap();
			searchMap.add(Observation.SP_PATIENT, new ReferenceParam(patientId));
			searchMap.add(Observation.SP_CATEGORY, new TokenParam("laboratory"));

			if (encounterId != null) {
				searchMap.add(Observation.SP_ENCOUNTER, new ReferenceParam(encounterId));
			}

			searchMap.add("_sort", new StringParam("-date"));

			// Filter for critical results
			List<Observation> allResults = observationDao.search(searchMap)
					.getAllResources()
					.stream()
					.map(resource -> (Observation) resource)
					.filter(this::isCriticalResult)
					.collect(Collectors.toList());

			allResults.forEach(lab -> critical.addEntry()
					.setResource(lab)
					.setFullUrl("Observation/" + lab.getIdElement().getIdPart()));

			logger.info("Found {} critical values", critical.getEntry().size());
			return critical;
		} catch (Exception e) {
			logger.error("Error getting critical values for {}: ", patientId, e);
			throw new RuntimeException("Failed to get critical values: " + e.getMessage());
		}
	}

	public Bundle getLabSummary(String patientId, TokenParam category) {
		try {
			logger.info("Getting lab summary for patient: {}", patientId);

			Patient patient = getPatientById(patientId);
			if (patient == null) {
				throw new RuntimeException("Patient not found: " + patientId);
			}

			Bundle summary = new Bundle();
			summary.setType(Bundle.BundleType.COLLECTION);
			summary.setId(UUID.randomUUID().toString());
			summary.setTimestamp(new Date());

			SearchParameterMap searchMap = new SearchParameterMap();
			searchMap.add(Observation.SP_PATIENT, new ReferenceParam(patientId));
			searchMap.add(Observation.SP_CATEGORY, new TokenParam("laboratory"));

			if (category != null) {
				searchMap.add(Observation.SP_CODE, category);
			}

			searchMap.add("_sort", new StringParam("-date"));

			observationDao.search(searchMap)
					.getAllResources()
					.forEach(resource -> {
						Observation lab = (Observation) resource;
						summary.addEntry()
								.setResource(lab)
								.setFullUrl("Observation/" + lab.getIdElement().getIdPart());
					});

			logger.info("Generated lab summary with {} entries", summary.getEntry().size());
			return summary;
		} catch (Exception e) {
			logger.error("Error getting lab summary for {}: ", patientId, e);
			throw new RuntimeException("Failed to get lab summary: " + e.getMessage());
		}
	}

	public List<Observation> getLatestLabResultsByCode(String patientId, String loincCode, int count) {
		try {
			SearchParameterMap searchMap = new SearchParameterMap();
			searchMap.add(Observation.SP_PATIENT, new ReferenceParam(patientId));
			searchMap.add(Observation.SP_CATEGORY, new TokenParam("laboratory"));
			searchMap.add(Observation.SP_CODE, new TokenParam("http://loinc.org", loincCode));
			searchMap.add("_sort", new StringParam("-date"));
			searchMap.setCount(count);

			return observationDao.search(searchMap)
					.getResources(0, count)
					.stream()
					.map(resource -> (Observation) resource)
					.collect(Collectors.toList());
		} catch (Exception e) {
			logger.error("Error getting latest lab results by code {}: ", loincCode, e);
			return Collections.emptyList();
		}
	}

	public boolean isLabResult(Observation observation) {
		if (observation == null)
			return false;

		return observation.getCategory().stream()
				.anyMatch(category -> category.getCoding().stream()
						.anyMatch(coding -> "laboratory".equals(coding.getCode()) &&
								"http://terminology.hl7.org/CodeSystem/observation-category"
										.equals(coding.getSystem())));
	}

	private boolean isAbnormalResult(Observation observation) {
		return observation.getInterpretation().stream()
				.anyMatch(interp -> interp.getCoding().stream()
						.anyMatch(coding -> "H".equals(coding.getCode()) ||
								"L".equals(coding.getCode()) ||
								"A".equals(coding.getCode())));
	}

	private boolean isCriticalResult(Observation observation) {
		return observation.getInterpretation().stream()
				.anyMatch(interp -> interp.getCoding().stream()
						.anyMatch(coding -> "HH".equals(coding.getCode()) ||
								"LL".equals(coding.getCode()) ||
								"AA".equals(coding.getCode())));
	}

	private void addAuditExtension(Observation labResult, String action) {
		try {
			Extension auditExtension = new Extension();
			auditExtension.setUrl("http://hospital.local/fhir/StructureDefinition/audit-trail");
			auditExtension.addExtension("action", new StringType(action));
			auditExtension.addExtension("timestamp", new DateTimeType(new Date()));
			auditExtension.addExtension("user", new StringType("system"));

			labResult.addExtension(auditExtension);
		} catch (Exception e) {
			logger.warn("Could not add audit extension: {}", e.getMessage());
		}
	}

	private void ensureLabCategory(Observation observation) {
		if (!isLabResult(observation)) {
			CodeableConcept labCategory = new CodeableConcept();
			labCategory.addCoding()
					.setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
					.setCode("laboratory")
					.setDisplay("Laboratory");
			observation.addCategory(labCategory);
		}
	}

	private void preserveSystemGeneratedData(Observation existing, Observation updated) {
		try {
			// Preserve system-generated identifiers
			existing.getIdentifier().forEach(identifier -> {
				if (!identifierExists(updated, identifier)) {
					updated.addIdentifier(identifier);
				}
			});

			// Preserve audit trail extensions
			existing.getExtension().stream()
					.filter(ext -> ext.getUrl().contains("audit-trail"))
					.forEach(updated::addExtension);
		} catch (Exception e) {
			logger.warn("Could not preserve system generated data: {}", e.getMessage());
		}
	}

	private boolean identifierExists(Observation observation, Identifier identifier) {
		try {
			return observation.getIdentifier().stream()
					.anyMatch(existing -> existing.getSystem().equals(identifier.getSystem()) &&
							existing.getValue().equals(identifier.getValue()));
		} catch (Exception e) {
			logger.warn("Error checking identifier existence: {}", e.getMessage());
			return false;
		}
	}

	private Patient getPatientById(String patientId) {
		try {
			return patientDao.read(new IdType(patientId));
		} catch (Exception e) {
			logger.warn("Could not read patient {}: {}", patientId, e.getMessage());
			return null;
		}
	}

	private Encounter getEncounterById(String encounterId) {
		try {
			return encounterDao.read(new IdType(encounterId));
		} catch (Exception e) {
			logger.warn("Could not read encounter {}: {}", encounterId, e.getMessage());
			return null;
		}
	}

	private String extractPatientIdFromReference(String reference) {
		if (reference == null) {
			throw new IllegalArgumentException("Patient reference cannot be null");
		}

		if (reference.contains("/")) {
			return reference.substring(reference.lastIndexOf("/") + 1);
		}
		return reference;
	}
}