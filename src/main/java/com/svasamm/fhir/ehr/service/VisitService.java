package com.svasamm.fhir.ehr.service;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;
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
@ConditionalOnClass(Encounter.class)
@Profile("!test")
public class VisitService {

	@Autowired
	private IFhirResourceDao<Encounter> encounterDao;

	@Autowired
	private IFhirResourceDao<Patient> patientDao;

	@Autowired
	private IFhirResourceDao<Observation> observationDao;

	@Autowired
	private IFhirResourceDao<Procedure> procedureDao;

	@Autowired
	private IFhirResourceDao<Location> locationDao;

	public Encounter getVisitById(String visitId) {
		try {
			return encounterDao.read(new IdType(visitId));
		} catch (Exception e) {
			return null;
		}
	}

	public MethodOutcome createVisit(Encounter visit) {
		addAuditExtension(visit, "created");
		return encounterDao.create(visit);
	}

	public MethodOutcome updateVisit(String visitId, Encounter visit) {
		Encounter existingVisit = getVisitById(visitId);
		if (existingVisit == null) {
			throw new RuntimeException("Visit not found: " + visitId);
		}

		preserveSystemGeneratedData(existingVisit, visit);
		addAuditExtension(visit, "updated");
		visit.setId(visitId);
		return encounterDao.update(visit);
	}

	public List<Encounter> searchVisits(ReferenceParam subject, ReferenceParam patient,
			TokenParam visitClass, DateRangeParam date, TokenParam status,
			TokenParam type, ReferenceParam location, NumberParam count) {
		SearchParameterMap searchMap = new SearchParameterMap();

		if (subject != null) {
			searchMap.add(Encounter.SP_SUBJECT, subject);
		}
		if (patient != null) {
			searchMap.add(Encounter.SP_PATIENT, patient);
		}
		if (visitClass != null) {
			searchMap.add(Encounter.SP_CLASS, visitClass);
		}
		if (date != null) {
			searchMap.add(Encounter.SP_DATE, date);
		}
		if (status != null) {
			searchMap.add(Encounter.SP_STATUS, status);
		}
		if (type != null) {
			searchMap.add(Encounter.SP_TYPE, type);
		}
		if (location != null) {
			searchMap.add(Encounter.SP_LOCATION, location);
		}

		int searchCount = count != null ? count.getValue().intValue() : 50;
		searchMap.setCount(searchCount);

		return encounterDao.search(searchMap)
				.getResources(0, searchCount)
				.stream()
				.map(resource -> (Encounter) resource)
				.collect(Collectors.toList());
	}

	public Bundle generateVisitSummary(String visitId) {
		Encounter visit = getVisitById(visitId);
		if (visit == null) {
			throw new RuntimeException("Visit not found: " + visitId);
		}

		Bundle summary = new Bundle();
		summary.setType(Bundle.BundleType.COLLECTION);
		summary.setId(UUID.randomUUID().toString());
		summary.setTimestamp(new Date());

		// Add main visit
		summary.addEntry()
				.setResource(visit)
				.setFullUrl("Encounter/" + visitId);

		// Add related patient
		if (visit.hasSubject()) {
			String patientId = visit.getSubject().getReferenceElement().getIdPart();
			Patient patient = getPatientById(patientId);
			if (patient != null) {
				summary.addEntry()
						.setResource(patient)
						.setFullUrl("Patient/" + patientId);
			}
		}

		// Add visit observations
		addVisitObservations(summary, visitId);

		// Add visit procedures
		addVisitProcedures(summary, visitId);

		// Add location information
		if (visit.hasLocation()) {
			visit.getLocation().forEach(loc -> {
				if (loc.hasLocation()) {
					String locationId = loc.getLocation().getReferenceElement().getIdPart();
					Location location = getLocationById(locationId);
					if (location != null) {
						summary.addEntry()
								.setResource(location)
								.setFullUrl("Location/" + locationId);
					}
				}
			});
		}

		return summary;
	}

	public Bundle getPatientVisits(String patientId, TokenParam visitClass, Date startDate, Date endDate) {
    // Extract just the ID part if it's in "Patient/123" format
    String cleanPatientId = patientId;
    if (patientId.startsWith("Patient/")) {
        cleanPatientId = patientId.substring(8);
    }
    
    Patient patient = getPatientById(cleanPatientId);
    if (patient == null) {
        throw new RuntimeException("Patient not found: " + cleanPatientId);
    }

    Bundle visits = new Bundle();
    visits.setType(Bundle.BundleType.COLLECTION);
    visits.setId(UUID.randomUUID().toString());
    visits.setTimestamp(new Date());

    SearchParameterMap searchMap = new SearchParameterMap();
    searchMap.add(Encounter.SP_PATIENT, new ReferenceParam("Patient/" + cleanPatientId));

    if (visitClass != null) {
        searchMap.add(Encounter.SP_CLASS, visitClass);
    }

    if (startDate != null && endDate != null) {
        DateRangeParam dateRange = new DateRangeParam(startDate, endDate);
        searchMap.add(Encounter.SP_DATE, dateRange);
    }

    // REMOVE the _sort line - this is causing the error
    // searchMap.add("_sort", new StringParam("-date"));

    encounterDao.search(searchMap)
            .getAllResources()
            .forEach(resource -> {
                Encounter visit = (Encounter) resource;
                visits.addEntry()
                        .setResource(visit)
                        .setFullUrl("Encounter/" + visit.getIdElement().getIdPart());
            });

    return visits;
}

	public Bundle getActiveVisits(ReferenceParam location, TokenParam visitClass) {
    Bundle activeVisits = new Bundle();
    activeVisits.setType(Bundle.BundleType.COLLECTION);
    activeVisits.setId(UUID.randomUUID().toString());
    activeVisits.setTimestamp(new Date());

    SearchParameterMap searchMap = new SearchParameterMap();
    searchMap.add(Encounter.SP_STATUS, new TokenParam("in-progress"));

    if (location != null) {
        searchMap.add(Encounter.SP_LOCATION, location);
    }

    if (visitClass != null) {
        searchMap.add(Encounter.SP_CLASS, visitClass);
    }

    // REMOVE the _sort line - this is causing the error
    // searchMap.add("_sort", new StringParam("date"));

    encounterDao.search(searchMap)
            .getAllResources()
            .forEach(resource -> {
                Encounter visit = (Encounter) resource;
                activeVisits.addEntry()
                        .setResource(visit)
                        .setFullUrl("Encounter/" + visit.getIdElement().getIdPart());
            });

    return activeVisits;
}

	public Bundle getVisitTimeline(String visitId, boolean includeObservations, boolean includeProcedures) {
		Encounter visit = getVisitById(visitId);
		if (visit == null) {
			throw new RuntimeException("Visit not found: " + visitId);
		}

		Bundle timeline = new Bundle();
		timeline.setType(Bundle.BundleType.COLLECTION);
		timeline.setId(UUID.randomUUID().toString());
		timeline.setTimestamp(new Date());

		// Add main visit
		timeline.addEntry()
				.setResource(visit)
				.setFullUrl("Encounter/" + visitId);

		if (includeObservations) {
			addVisitObservations(timeline, visitId);
		}

		if (includeProcedures) {
			addVisitProcedures(timeline, visitId);
		}

		return timeline;
	}

	public List<Encounter> getVisitsByStatus(String status, int count) {
		SearchParameterMap searchMap = new SearchParameterMap();
		searchMap.add(Encounter.SP_STATUS, new TokenParam(status));
		searchMap.add("_sort", new StringParam("-date"));
		searchMap.setCount(count);

		return encounterDao.search(searchMap)
				.getResources(0, count)
				.stream()
				.map(resource -> (Encounter) resource)
				.collect(Collectors.toList());
	}

	public List<Encounter> getRecentVisits(String patientId, int count) {
		SearchParameterMap searchMap = new SearchParameterMap();
		searchMap.add(Encounter.SP_PATIENT, new ReferenceParam(patientId));
		searchMap.add("_sort", new StringParam("-date"));
		searchMap.setCount(count);

		return encounterDao.search(searchMap)
				.getResources(0, count)
				.stream()
				.map(resource -> (Encounter) resource)
				.collect(Collectors.toList());
	}

	public MethodOutcome dischargeVisit(String visitId) {
		Encounter visit = getVisitById(visitId);
		if (visit == null) {
			throw new RuntimeException("Visit not found: " + visitId);
		}

		visit.setStatus(Encounter.EncounterStatus.FINISHED);
		if (visit.getPeriod() != null && visit.getPeriod().getEnd() == null) {
			visit.getPeriod().setEnd(new Date());
		}

		addAuditExtension(visit, "discharged");
		return encounterDao.update(visit);
	}

	private void addAuditExtension(Encounter visit, String action) {
		Extension auditExtension = new Extension();
		auditExtension.setUrl("http://hospital.local/fhir/StructureDefinition/audit-trail");
		auditExtension.addExtension("action", new StringType(action));
		auditExtension.addExtension("timestamp", new DateTimeType(new Date()));
		auditExtension.addExtension("user", new StringType("system"));

		visit.addExtension(auditExtension);
	}

	private void preserveSystemGeneratedData(Encounter existing, Encounter updated) {
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

	private boolean identifierExists(Encounter encounter, Identifier identifier) {
		return encounter.getIdentifier().stream()
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

	private Location getLocationById(String locationId) {
		try {
			return locationDao.read(new IdType(locationId));
		} catch (Exception e) {
			return null;
		}
	}

	private void addVisitObservations(Bundle bundle, String visitId) {
    try {
        SearchParameterMap searchMap = new SearchParameterMap();
        searchMap.add(Observation.SP_ENCOUNTER, new ReferenceParam("Encounter/" + visitId));
        // REMOVE: searchMap.add("_sort", new StringParam("date"));

        observationDao.search(searchMap)
                .getAllResources()
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

	private void addVisitProcedures(Bundle bundle, String visitId) {
    try {
        SearchParameterMap searchMap = new SearchParameterMap();
        searchMap.add(Procedure.SP_ENCOUNTER, new ReferenceParam("Encounter/" + visitId));
        // REMOVE: searchMap.add("_sort", new StringParam("date"));

        procedureDao.search(searchMap)
                .getAllResources()
                .forEach(resource -> {
                    Procedure proc = (Procedure) resource;
                    bundle.addEntry()
                            .setResource(proc)
                            .setFullUrl("Procedure/" + proc.getIdElement().getIdPart());
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