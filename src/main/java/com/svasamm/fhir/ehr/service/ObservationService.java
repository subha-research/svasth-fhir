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
public class ObservationService {

	@Autowired
	private IFhirResourceDao<Observation> observationDao;

	@Autowired
	private IFhirResourceDao<Patient> patientDao;

	@Autowired
	private IFhirResourceDao<Encounter> encounterDao;

	@Autowired
	private IFhirResourceDao<DiagnosticReport> diagnosticReportDao;

	public Observation getObservationById(String observationId) {
		try {
			return observationDao.read(new IdType(observationId));
		} catch (Exception e) {
			return null;
		}
	}

	public MethodOutcome createObservation(Observation observation) {
		addAuditExtension(observation, "created");
		return observationDao.create(observation);
	}

	public MethodOutcome updateObservation(String observationId, Observation observation) {
		Observation existingObservation = getObservationById(observationId);
		if (existingObservation == null) {
			throw new RuntimeException("Observation not found: " + observationId);
		}

		preserveSystemGeneratedData(existingObservation, observation);
		addAuditExtension(observation, "updated");
		observation.setId(observationId);
		return observationDao.update(observation);
	}

	public List<Observation> searchObservations(ReferenceParam subject, ReferenceParam patient,
			TokenParam code, TokenParam category, DateRangeParam date,
			TokenParam status, NumberParam count) {
		SearchParameterMap searchMap = new SearchParameterMap();

		if (subject != null) {
			searchMap.add(Observation.SP_SUBJECT, subject);
		}
		if (patient != null) {
			searchMap.add(Observation.SP_PATIENT, patient);
		}
		if (code != null) {
			searchMap.add(Observation.SP_CODE, code);
		}
		if (category != null) {
			searchMap.add(Observation.SP_CATEGORY, category);
		}
		if (date != null) {
			searchMap.add(Observation.SP_DATE, date);
		}
		if (status != null) {
			searchMap.add(Observation.SP_STATUS, status);
		}

		int searchCount = count != null ? count.getValue().intValue() : 50;
		searchMap.setCount(searchCount);

		return observationDao.search(searchMap)
				.getResources(0, searchCount)
				.stream()
				.map(resource -> (Observation) resource)
				.collect(Collectors.toList());
	}

	public Bundle generateObservationSummary(String observationId) {
		Observation observation = getObservationById(observationId);
		if (observation == null) {
			throw new RuntimeException("Observation not found: " + observationId);
		}

		Bundle summary = new Bundle();
		summary.setType(Bundle.BundleType.COLLECTION);
		summary.setId(UUID.randomUUID().toString());
		summary.setTimestamp(new Date());

		// Add main observation
		summary.addEntry()
				.setResource(observation)
				.setFullUrl("Observation/" + observationId);

		// Add related patient
		if (observation.hasSubject()) {
			String patientId = observation.getSubject().getReferenceElement().getIdPart();
			Patient patient = getPatientById(patientId);
			if (patient != null) {
				summary.addEntry()
						.setResource(patient)
						.setFullUrl("Patient/" + patientId);
			}
		}

		// Add related encounter
		if (observation.hasEncounter()) {
			String encounterId = observation.getEncounter().getReferenceElement().getIdPart();
			Encounter encounter = getEncounterById(encounterId);
			if (encounter != null) {
				summary.addEntry()
						.setResource(encounter)
						.setFullUrl("Encounter/" + encounterId);
			}
		}

		// Add related observations (same category and patient)
		addRelatedObservations(summary, observation);

		return summary;
	}

	public Bundle getPatientObservations(String patientId, TokenParam category, Date startDate, Date endDate) {
		Patient patient = getPatientById(patientId);
		if (patient == null) {
			throw new RuntimeException("Patient not found: " + patientId);
		}

		Bundle observations = new Bundle();
		observations.setType(Bundle.BundleType.COLLECTION);
		observations.setId(UUID.randomUUID().toString());
		observations.setTimestamp(new Date());

		SearchParameterMap searchMap = new SearchParameterMap();
		searchMap.add(Observation.SP_PATIENT, new ReferenceParam(patientId));

		if (category != null) {
			searchMap.add(Observation.SP_CATEGORY, category);
		}

		if (startDate != null && endDate != null) {
			DateRangeParam dateRange = new DateRangeParam(startDate, endDate);
			searchMap.add(Observation.SP_DATE, dateRange);
		}

		searchMap.add("_sort", new StringParam("-date"));

		observationDao.search(searchMap)
				.getAllResources()
				.forEach(resource -> {
					Observation obs = (Observation) resource;
					observations.addEntry()
							.setResource(obs)
							.setFullUrl("Observation/" + obs.getIdElement().getIdPart());
				});

		return observations;
	}

	public Bundle getVitalSignsTrend(String patientReference, TokenParam code, int periodDays) {
		String patientId = extractPatientIdFromReference(patientReference);
		Patient patient = getPatientById(patientId);
		if (patient == null) {
			throw new RuntimeException("Patient not found: " + patientId);
		}

		Bundle trend = new Bundle();
		trend.setType(Bundle.BundleType.COLLECTION);
		trend.setId(UUID.randomUUID().toString());
		trend.setTimestamp(new Date());

		// Calculate date range
		Calendar cal = Calendar.getInstance();
		Date endDate = cal.getTime();
		cal.add(Calendar.DAY_OF_MONTH, -periodDays);
		Date startDate = cal.getTime();

		SearchParameterMap searchMap = new SearchParameterMap();
		searchMap.add(Observation.SP_PATIENT, new ReferenceParam(patientId));
		searchMap.add(Observation.SP_CATEGORY, new TokenParam("vital-signs"));
		searchMap.add(Observation.SP_DATE, new DateRangeParam(startDate, endDate));

		if (code != null) {
			searchMap.add(Observation.SP_CODE, code);
		}

		searchMap.add("_sort", new StringParam("date"));

		observationDao.search(searchMap)
				.getAllResources()
				.forEach(resource -> {
					Observation obs = (Observation) resource;
					trend.addEntry()
							.setResource(obs)
							.setFullUrl("Observation/" + obs.getIdElement().getIdPart());
				});

		return trend;
	}

	public List<Observation> getObservationsByCategory(String patientId, String category, int count) {
		SearchParameterMap searchMap = new SearchParameterMap();
		searchMap.add(Observation.SP_PATIENT, new ReferenceParam(patientId));
		searchMap.add(Observation.SP_CATEGORY, new TokenParam(category));
		searchMap.add("_sort", new StringParam("-date"));
		searchMap.setCount(count);

		return observationDao.search(searchMap)
				.getResources(0, count)
				.stream()
				.map(resource -> (Observation) resource)
				.collect(Collectors.toList());
	}

	public List<Observation> getRecentLabResults(String patientId, int count) {
		return getObservationsByCategory(patientId, "laboratory", count);
	}

	public List<Observation> getRecentVitalSigns(String patientId, int count) {
		return getObservationsByCategory(patientId, "vital-signs", count);
	}

	private void addAuditExtension(Observation observation, String action) {
		Extension auditExtension = new Extension();
		auditExtension.setUrl("http://hospital.local/fhir/StructureDefinition/audit-trail");
		auditExtension.addExtension("action", new StringType(action));
		auditExtension.addExtension("timestamp", new DateTimeType(new Date()));
		auditExtension.addExtension("user", new StringType("system"));

		observation.addExtension(auditExtension);
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

	private void addRelatedObservations(Bundle bundle, Observation mainObservation) {
		try {
			if (!mainObservation.hasSubject() || !mainObservation.hasCategory()) {
				return;
			}

			String patientId = mainObservation.getSubject().getReferenceElement().getIdPart();
			String categoryCode = mainObservation.getCategoryFirstRep().getCodingFirstRep().getCode();

			SearchParameterMap searchMap = new SearchParameterMap();
			searchMap.add(Observation.SP_PATIENT, new ReferenceParam(patientId));
			searchMap.add(Observation.SP_CATEGORY, new TokenParam(categoryCode));
			searchMap.add("_sort", new StringParam("-date"));
			searchMap.setCount(5);

			observationDao.search(searchMap)
					.getResources(0, 5)
					.stream()
					.filter(resource -> !resource.getIdElement().getIdPart()
							.equals(mainObservation.getIdElement().getIdPart()))
					.forEach(resource -> {
						Observation obs = (Observation) resource;
						bundle.addEntry()
								.setResource(obs)
								.setFullUrl("Observation/" + obs.getIdElement().getIdPart());
					});

		} catch (Exception e) {
			// Log error but don't fail the operation
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