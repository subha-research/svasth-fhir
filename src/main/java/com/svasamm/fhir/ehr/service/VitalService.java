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

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@ConditionalOnClass(Observation.class)
@Profile("!test")
public class VitalService {

	@Autowired
	private IFhirResourceDao<Observation> observationDao;

	@Autowired
	private IFhirResourceDao<Patient> patientDao;

	@Autowired
	private IFhirResourceDao<Encounter> encounterDao;

	// Common vital signs LOINC codes
	private static final Map<String, String> VITAL_SIGNS_CODES = Map.of(
			"8310-5", "Body temperature",
			"8867-4", "Heart rate",
			"8480-6", "Systolic blood pressure",
			"8462-4", "Diastolic blood pressure",
			"9279-1", "Respiratory rate",
			"2708-6", "Oxygen saturation",
			"29463-7", "Body weight",
			"8302-2", "Body height",
			"39156-5", "Body mass index");

	public Observation getVitalSignById(String vitalSignId) {
		try {
			Observation obs = observationDao.read(new IdType(vitalSignId));
			return isVitalSign(obs) ? obs : null;
		} catch (Exception e) {
			return null;
		}
	}

	public MethodOutcome createVitalSign(Observation vitalSign) {
		addAuditExtension(vitalSign, "created");
		ensureVitalSignsCategory(vitalSign);
		return observationDao.create(vitalSign);
	}

	public MethodOutcome updateVitalSign(String vitalSignId, Observation vitalSign) {
		Observation existingVitalSign = getVitalSignById(vitalSignId);
		if (existingVitalSign == null) {
			throw new RuntimeException("Vital Sign not found: " + vitalSignId);
		}

		preserveSystemGeneratedData(existingVitalSign, vitalSign);
		addAuditExtension(vitalSign, "updated");
		ensureVitalSignsCategory(vitalSign);
		vitalSign.setId(vitalSignId);
		return observationDao.update(vitalSign);
	}

	public List<Observation> searchVitalSigns(ReferenceParam subject, ReferenceParam patient,
			TokenParam code, DateRangeParam date, TokenParam status,
			ReferenceParam encounter, NumberParam count) {
		SearchParameterMap searchMap = new SearchParameterMap();

		// Always filter for vital signs category
		searchMap.add(Observation.SP_CATEGORY, new TokenParam("vital-signs"));

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

		int searchCount = count != null ? count.getValue().intValue() : 50;
		searchMap.setCount(searchCount);

		return observationDao.search(searchMap)
				.getResources(0, searchCount)
				.stream()
				.map(resource -> (Observation) resource)
				.collect(Collectors.toList());
	}

	public Bundle generateVitalSignsPanel(String patientId) {
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

		// Get latest vital signs for each type
		for (String loincCode : VITAL_SIGNS_CODES.keySet()) {
			List<Observation> latestVitals = getLatestVitalSignsByCode(patientId, loincCode, 1);
			latestVitals.forEach(vital -> panel.addEntry()
					.setResource(vital)
					.setFullUrl("Observation/" + vital.getIdElement().getIdPart()));
		}

		return panel;
	}

	public Bundle getVitalSignsTrend(String patientId, TokenParam code, Date startDate, Date endDate) {
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
		searchMap.add(Observation.SP_CATEGORY, new TokenParam("vital-signs"));

		if (code != null) {
			searchMap.add(Observation.SP_CODE, code);
		}

		if (startDate != null && endDate != null) {
			DateRangeParam dateRange = new DateRangeParam(startDate, endDate);
			searchMap.add(Observation.SP_DATE, dateRange);
		} else {
			// Default to last 30 days
			Calendar cal = Calendar.getInstance();
			Date end = cal.getTime();
			cal.add(Calendar.DAY_OF_MONTH, -30);
			Date start = cal.getTime();
			searchMap.add(Observation.SP_DATE, new DateRangeParam(start, end));
		}

		searchMap.add("_sort", new StringParam("date"));

		observationDao.search(searchMap)
				.getAllResources()
				.forEach(resource -> {
					Observation vital = (Observation) resource;
					trend.addEntry()
							.setResource(vital)
							.setFullUrl("Observation/" + vital.getIdElement().getIdPart());
				});

		return trend;
	}

	public Bundle getLatestVitalSigns(String patientId, int periodHours) {
		Patient patient = getPatientById(patientId);
		if (patient == null) {
			throw new RuntimeException("Patient not found: " + patientId);
		}

		Bundle latest = new Bundle();
		latest.setType(Bundle.BundleType.COLLECTION);
		latest.setId(UUID.randomUUID().toString());
		latest.setTimestamp(new Date());

		// Calculate time range
		Calendar cal = Calendar.getInstance();
		Date endDate = cal.getTime();
		cal.add(Calendar.HOUR_OF_DAY, -periodHours);
		Date startDate = cal.getTime();

		SearchParameterMap searchMap = new SearchParameterMap();
		searchMap.add(Observation.SP_PATIENT, new ReferenceParam(patientId));
		searchMap.add(Observation.SP_CATEGORY, new TokenParam("vital-signs"));
		searchMap.add(Observation.SP_DATE, new DateRangeParam(startDate, endDate));
		searchMap.add("_sort", new StringParam("-date"));

		observationDao.search(searchMap)
				.getAllResources()
				.forEach(resource -> {
					Observation vital = (Observation) resource;
					latest.addEntry()
							.setResource(vital)
							.setFullUrl("Observation/" + vital.getIdElement().getIdPart());
				});

		return latest;
	}

	public Bundle getVitalSignsSummary(String patientId, String encounterId) {
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
		searchMap.add(Observation.SP_CATEGORY, new TokenParam("vital-signs"));

		if (encounterId != null) {
			searchMap.add(Observation.SP_ENCOUNTER, new ReferenceParam(encounterId));
		}

		searchMap.add("_sort", new StringParam("-date"));

		observationDao.search(searchMap)
				.getAllResources()
				.forEach(resource -> {
					Observation vital = (Observation) resource;
					summary.addEntry()
							.setResource(vital)
							.setFullUrl("Observation/" + vital.getIdElement().getIdPart());
				});

		return summary;
	}

	public List<Observation> getLatestVitalSignsByCode(String patientId, String loincCode, int count) {
		SearchParameterMap searchMap = new SearchParameterMap();
		searchMap.add(Observation.SP_PATIENT, new ReferenceParam(patientId));
		searchMap.add(Observation.SP_CATEGORY, new TokenParam("vital-signs"));
		searchMap.add(Observation.SP_CODE, new TokenParam("http://loinc.org", loincCode));
		searchMap.add("_sort", new StringParam("-date"));
		searchMap.setCount(count);

		return observationDao.search(searchMap)
				.getResources(0, count)
				.stream()
				.map(resource -> (Observation) resource)
				.collect(Collectors.toList());
	}

	public boolean isVitalSign(Observation observation) {
		if (observation == null)
			return false;

		return observation.getCategory().stream()
				.anyMatch(category -> category.getCoding().stream()
						.anyMatch(coding -> "vital-signs".equals(coding.getCode()) &&
								"http://terminology.hl7.org/CodeSystem/observation-category".equals(coding.getSystem())));
	}

	public List<Observation> getBloodPressureReadings(String patientId, int count) {
		// Get both systolic and diastolic readings
		List<Observation> systolic = getLatestVitalSignsByCode(patientId, "8480-6", count);
		List<Observation> diastolic = getLatestVitalSignsByCode(patientId, "8462-4", count);

		List<Observation> combined = new ArrayList<>();
		combined.addAll(systolic);
		combined.addAll(diastolic);

		return combined.stream()
				.sorted(
						(a, b) -> b.getEffectiveDateTimeType().getValue().compareTo(a.getEffectiveDateTimeType().getValue()))
				.limit(count * 2)
				.collect(Collectors.toList());
	}

	private void addAuditExtension(Observation vitalSign, String action) {
		Extension auditExtension = new Extension();
		auditExtension.setUrl("http://hospital.local/fhir/StructureDefinition/audit-trail");
		auditExtension.addExtension("action", new StringType(action));
		auditExtension.addExtension("timestamp", new DateTimeType(new Date()));
		auditExtension.addExtension("user", new StringType("system"));

		vitalSign.addExtension(auditExtension);
	}

	private void ensureVitalSignsCategory(Observation observation) {
		if (!isVitalSign(observation)) {
			CodeableConcept vitalSignsCategory = new CodeableConcept();
			vitalSignsCategory.addCoding()
					.setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
					.setCode("vital-signs")
					.setDisplay("Vital Signs");
			observation.addCategory(vitalSignsCategory);
		}
	}

	private void preserveSystemGeneratedData(Observation existing, Observation updated) {
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
	}

	private boolean identifierExists(Observation observation, Identifier identifier) {
		return observation.getIdentifier().stream()
				.anyMatch(existing -> existing.getSystem().equals(identifier.getSystem()) &&
						existing.getValue().equals(identifier.getValue()));
	}

	private Patient getPatientById(String patientId) {
		try {
			return patientDao.read(new IdType(patientId));
		} catch (Exception e) {
			return null;
		}
	}

	private Encounter getEncounterById(String encounterId) {
		try {
			return encounterDao.read(new IdType(encounterId));
		} catch (Exception e) {
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