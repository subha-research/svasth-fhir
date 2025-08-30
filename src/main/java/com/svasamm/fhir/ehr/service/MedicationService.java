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
@ConditionalOnClass(Medication.class)
@Profile("!test")
public class MedicationService {

	@Autowired
	private IFhirResourceDao<Medication> medicationDao;

	@Autowired
	private IFhirResourceDao<Patient> patientDao;

	@Autowired
	private IFhirResourceDao<Encounter> encounterDao;

	@Autowired
	private IFhirResourceDao<MedicationRequest> medicationRequestDao;

	public Medication getMedicationById(String medicationId) {
		try {
			return medicationDao.read(new IdType(medicationId));
		} catch (Exception e) {
			return null;
		}
	}

	public MethodOutcome createMedication(Medication medication) {
		addAuditExtension(medication, "created");
		return medicationDao.create(medication);
	}

	public MethodOutcome updateMedication(String medicationId, Medication medication) {
		Medication existingMedication = getMedicationById(medicationId);
		if (existingMedication == null) {
			throw new RuntimeException("Medication not found: " + medicationId);
		}

		preserveSystemGeneratedData(existingMedication, medication);
		addAuditExtension(medication, "updated");
		medication.setId(medicationId);
		return medicationDao.update(medication);
	}

	public List<Medication> searchMedications(ReferenceParam manufacturer, ReferenceParam ingredient,
			TokenParam code, TokenParam identifier, TokenParam form,
			TokenParam status, NumberParam count) {
		SearchParameterMap searchMap = new SearchParameterMap();

		if (manufacturer != null) {
			searchMap.add(Medication.SP_MANUFACTURER, manufacturer);
		}
		if (ingredient != null) {
			searchMap.add(Medication.SP_INGREDIENT, ingredient);
		}
		if (code != null) {
			searchMap.add(Medication.SP_CODE, code);
		}
		if (identifier != null) {
			searchMap.add(Medication.SP_IDENTIFIER, identifier);
		}
		if (form != null) {
			searchMap.add(Medication.SP_FORM, form);
		}
		if (status != null) {
			searchMap.add(Medication.SP_STATUS, status);
		}

		int searchCount = count != null ? count.getValue().intValue() : 50;
		searchMap.setCount(searchCount);

		return medicationDao.search(searchMap)
				.getResources(0, searchCount)
				.stream()
				.map(resource -> (Medication) resource)
				.collect(Collectors.toList());
	}

	public Bundle generateMedicationSummary(String medicationId) {
		Medication medication = getMedicationById(medicationId);
		if (medication == null) {
			throw new RuntimeException("Medication not found: " + medicationId);
		}

		Bundle summary = new Bundle();
		summary.setType(Bundle.BundleType.COLLECTION);
		summary.setId(UUID.randomUUID().toString());
		summary.setTimestamp(new Date());

		// Add main medication
		summary.addEntry()
				.setResource(medication)
				.setFullUrl("Medication/" + medicationId);

		// Add related medication requests
		addRelatedMedicationRequests(summary, medicationId);

		// Add manufacturer if available
		if (medication.hasManufacturer()) {
			String manufacturerId = medication.getManufacturer().getReferenceElement().getIdPart();
			Organization manufacturer = getOrganizationById(manufacturerId);
			if (manufacturer != null) {
				summary.addEntry()
						.setResource(manufacturer)
						.setFullUrl("Organization/" + manufacturerId);
			}
		}

		return summary;
	}

	public Bundle getPatientMedications(String patientId, TokenParam category, Date startDate, Date endDate) {
		Patient patient = getPatientById(patientId);
		if (patient == null) {
			throw new RuntimeException("Patient not found: " + patientId);
		}

		Bundle medications = new Bundle();
		medications.setType(Bundle.BundleType.COLLECTION);
		medications.setId(UUID.randomUUID().toString());
		medications.setTimestamp(new Date());

		// Search for MedicationRequest resources instead of Medication with patient
		// reference
		SearchParameterMap searchMap = new SearchParameterMap();
		searchMap.add(MedicationRequest.SP_PATIENT, new ReferenceParam(patientId));

		if (category != null) {
			searchMap.add(MedicationRequest.SP_CATEGORY, category);
		}

		if (startDate != null && endDate != null) {
			DateRangeParam dateRange = new DateRangeParam(startDate, endDate);
			searchMap.add(MedicationRequest.SP_AUTHOREDON, dateRange);
		}

		searchMap.add("_sort", new StringParam("-authored-on"));

		medicationRequestDao.search(searchMap)
				.getAllResources()
				.forEach(resource -> {
					MedicationRequest medReq = (MedicationRequest) resource;
					medications.addEntry()
							.setResource(medReq)
							.setFullUrl("MedicationRequest/" + medReq.getIdElement().getIdPart());
				});

		return medications;
	}

	public Bundle getMedicationInteractions(String patientReference, TokenParam medication, int periodDays) {
		String patientId = extractPatientIdFromReference(patientReference);
		Patient patient = getPatientById(patientId);
		if (patient == null) {
			throw new RuntimeException("Patient not found: " + patientId);
		}

		Bundle interactions = new Bundle();
		interactions.setType(Bundle.BundleType.COLLECTION);
		interactions.setId(UUID.randomUUID().toString());
		interactions.setTimestamp(new Date());

		// Calculate date range
		Calendar cal = Calendar.getInstance();
		Date endDate = cal.getTime();
		cal.add(Calendar.DAY_OF_MONTH, -periodDays);
		Date startDate = cal.getTime();

		SearchParameterMap searchMap = new SearchParameterMap();
		searchMap.add(MedicationRequest.SP_PATIENT, new ReferenceParam(patientId));
		searchMap.add(MedicationRequest.SP_STATUS, new TokenParam("active"));
		searchMap.add(MedicationRequest.SP_AUTHOREDON, new DateRangeParam(startDate, endDate));

		if (medication != null) {
			searchMap.add(MedicationRequest.SP_MEDICATION, medication);
		}

		searchMap.add("_sort", new StringParam("authored-on"));

		medicationRequestDao.search(searchMap)
				.getAllResources()
				.forEach(resource -> {
					MedicationRequest medReq = (MedicationRequest) resource;
					interactions.addEntry()
							.setResource(medReq)
							.setFullUrl("MedicationRequest/" + medReq.getIdElement().getIdPart());
				});

		return interactions;
	}

	public List<Medication> getMedicationsByStatus(String status, int count) {
		SearchParameterMap searchMap = new SearchParameterMap();
		searchMap.add(Medication.SP_STATUS, new TokenParam(status));
		searchMap.add("_sort", new StringParam("-_lastUpdated"));
		searchMap.setCount(count);

		return medicationDao.search(searchMap)
				.getResources(0, count)
				.stream()
				.map(resource -> (Medication) resource)
				.collect(Collectors.toList());
	}

	public List<Medication> getActiveMedications(int count) {
		return getMedicationsByStatus("active", count);
	}

	public List<Medication> getInactiveMedications(int count) {
		return getMedicationsByStatus("inactive", count);
	}

	private void addAuditExtension(Medication medication, String action) {
		Extension auditExtension = new Extension();
		auditExtension.setUrl("http://hospital.local/fhir/StructureDefinition/audit-trail");
		auditExtension.addExtension("action", new StringType(action));
		auditExtension.addExtension("timestamp", new DateTimeType(new Date()));
		auditExtension.addExtension("user", new StringType("system"));

		medication.addExtension(auditExtension);
	}

	private void preserveSystemGeneratedData(Medication existing, Medication updated) {
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

	private boolean identifierExists(Medication medication, Identifier identifier) {
		return medication.getIdentifier().stream()
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

	private Organization getOrganizationById(String organizationId) {
		try {
			// You'll need to add Organization DAO if not already present
			// return organizationDao.read(new IdType(organizationId));
			return null; // Placeholder
		} catch (Exception e) {
			return null;
		}
	}

	private void addRelatedMedicationRequests(Bundle bundle, String medicationId) {
		try {
			SearchParameterMap searchMap = new SearchParameterMap();
			searchMap.add(MedicationRequest.SP_MEDICATION, new ReferenceParam("Medication/" + medicationId));
			searchMap.add("_sort", new StringParam("-authored-on"));
			searchMap.setCount(5);

			medicationRequestDao.search(searchMap)
					.getResources(0, 5)
					.forEach(resource -> {
						MedicationRequest medReq = (MedicationRequest) resource;
						bundle.addEntry()
								.setResource(medReq)
								.setFullUrl("MedicationRequest/" + medReq.getIdElement().getIdPart());
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