package com.svasamm.fhir.biobank.service;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.*;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SpecimenService {

    @Autowired
    private IFhirResourceDao<Specimen> specimenDao;

    @Autowired
    private IFhirResourceDao<Location> locationDao;

    @Autowired
    private IFhirResourceDao<Provenance> provenanceDao;

    public Specimen getSpecimenById(String specimenId) {
        try {
            return specimenDao.read(new IdType(specimenId));
        } catch (Exception e) {
            return null;
        }
    }

    public MethodOutcome createSpecimen(Specimen specimen) {
        // Add creation provenance
        addProvenanceRecord(specimen, "CREATE");
        
        // Initialize storage tracking
        initializeStorageTracking(specimen);
        
        return specimenDao.create(specimen);
    }

    public MethodOutcome updateSpecimen(String specimenId, Specimen specimen) {
        Specimen existingSpecimen = getSpecimenById(specimenId);
        if (existingSpecimen == null) {
            throw new RuntimeException("Specimen not found: " + specimenId);
        }
        
        // Track changes for audit
        trackSpecimenChanges(existingSpecimen, specimen);
        
        // Add update provenance
        addProvenanceRecord(specimen, "UPDATE");
        
        specimen.setId(specimenId);
        return specimenDao.update(specimen);
    }

    public List<Specimen> searchSpecimens(ReferenceParam subject, TokenParam identifier,
                                        TokenParam type, TokenParam status,
                                        DateRangeParam collected, NumberParam count) {
        SearchParameterMap searchMap = new SearchParameterMap();
        
        if (subject != null) {
            searchMap.add(Specimen.SP_SUBJECT, subject);
        }
        if (identifier != null) {
            searchMap.add(Specimen.SP_IDENTIFIER, identifier);
        }
        if (type != null) {
            searchMap.add(Specimen.SP_TYPE, type);
        }
        if (status != null) {
            searchMap.add(Specimen.SP_STATUS, status);
        }
        if (collected != null) {
            searchMap.add(Specimen.SP_COLLECTED, collected);
        }
        
        int searchCount = count != null ? count.getValue().intValue() : 50;
        searchMap.setCount(searchCount);
        
        return specimenDao.search(searchMap)
            .getResources(0, searchCount)
            .stream()
            .map(resource -> (Specimen) resource)
            .collect(Collectors.toList());
    }

    public Bundle getChainOfCustody(String specimenId) {
        Specimen specimen = getSpecimenById(specimenId);
        if (specimen == null) {
            throw new RuntimeException("Specimen not found: " + specimenId);
        }

        Bundle custody = new Bundle();
        custody.setType(Bundle.BundleType.COLLECTION);
        custody.setId(UUID.randomUUID().toString());
        custody.setTimestamp(new Date());
        
        // Add specimen
        custody.addEntry()
            .setResource(specimen)
            .setFullUrl("Specimen/" + specimenId);
        
        // Get all provenance records for this specimen
        try {
            SearchParameterMap provenanceSearch = new SearchParameterMap();
            provenanceSearch.add("target", new ReferenceParam("Specimen/" + specimenId));
            provenanceSearch.add("_sort", new StringParam("recorded"));
            
            // provenanceDao.search(provenanceSearch)
            //     .getAllResources()
            //     .forEach(resource -> 
            //         custody.addEntry()
            //             .setResource(resource)
            //             .setFullUrl(resource.fhirType() + "/" + resource.getIdElement().getIdPart())
            //     );

            provenanceDao.search(provenanceSearch)
            .getAllResources()
            .forEach(resource -> {
                Provenance prov = (Provenance) resource;
                custody.addEntry()
                    .setResource(prov)
                    .setFullUrl("Provenance/" + prov.getIdElement().getIdPart());
            });
        } catch (Exception e) {
            // Log error but don't fail the operation
        }
        
        return custody;
    }

    public Location getCurrentLocation(String specimenId) {
        Specimen specimen = getSpecimenById(specimenId);
        if (specimen == null) {
            return null;
        }
        
        // Get current location from extension
        Extension locationExt = specimen.getExtensionByUrl(
            "http://hospital.local/fhir/StructureDefinition/current-location"
        );
        
        if (locationExt != null) {
            if (locationExt.getValue() instanceof Reference) {
                Reference locationRef = (Reference) locationExt.getValue();
                String locationId = locationRef.getReferenceElement().getIdPart();
                try {
                    return locationDao.read(new IdType(locationId));
                } catch (Exception e) {
                    return null;
                }
            } else if (locationExt.getValue() instanceof StringType) {
                // Location is stored as string, create a simple location
                Location simpleLocation = new Location();
                simpleLocation.setName(((StringType) locationExt.getValue()).getValue());
                return simpleLocation;
            }
        }
        
        return null;
    }

    public MethodOutcome updateLocation(String specimenId, Reference newLocation, String notes) {
        Specimen specimen = getSpecimenById(specimenId);
        if (specimen == null) {
            throw new RuntimeException("Specimen not found: " + specimenId);
        }
        
        // Update location extension
        // specimen.removeExtension("http://hospital.local/fhir/StructureDefinition/current-location");
        Extension existingExt = specimen.getExtensionByUrl("http://hospital.local/fhir/StructureDefinition/current-location");
        if (existingExt != null) {
        specimen.getExtension().remove(existingExt);
        }
        
        Extension locationExt = new Extension();
        locationExt.setUrl("http://hospital.local/fhir/StructureDefinition/current-location");
        locationExt.setValue(newLocation);
        specimen.addExtension(locationExt);
        
        // Add location change to storage history
        Extension storageExt = specimen.getExtensionByUrl("http://hospital.local/fhir/StructureDefinition/storage-history");
        if (storageExt == null) {
            storageExt = new Extension();
            storageExt.setUrl("http://hospital.local/fhir/StructureDefinition/storage-history");
            specimen.addExtension(storageExt);
        }
        
        Extension storageEvent = storageExt.addExtension();
        storageEvent.setUrl("storage-event");
        storageEvent.addExtension("timestamp", new DateTimeType(new Date()));
        storageEvent.addExtension("action", new StringType("moved"));
        storageEvent.addExtension("location", newLocation);
        if (notes != null) {
            storageEvent.addExtension("notes", new StringType(notes));
        }
        
        // Add location change provenance
        addLocationChangeProvenance(specimen, newLocation, notes);
        
        return specimenDao.update(specimen);
    }

    public Bundle batchUpdateLocation(List<IdType> specimenIds, Reference location) {
        Bundle result = new Bundle();
        result.setType(Bundle.BundleType.BATCHRESPONSE);
        result.setId(UUID.randomUUID().toString());
        result.setTimestamp(new Date());
        
        for (IdType specimenId : specimenIds) {
            try {
                updateLocation(specimenId.getIdPart(), location, "Batch location update");
                
                Bundle.BundleEntryComponent entry = result.addEntry();
                entry.setResponse(new Bundle.BundleEntryResponseComponent())
                    .getResponse()
                    .setStatus("200")
                    .setLocation("Specimen/" + specimenId.getIdPart());
                    
            } catch (Exception e) {
                Bundle.BundleEntryComponent entry = result.addEntry();
                entry.setResponse(new Bundle.BundleEntryResponseComponent())
                    .getResponse()
                    .setStatus("500")
                    .setOutcome(createOperationOutcome("Error updating specimen: " + e.getMessage()));
            }
        }
        
        return result;
    }

    private void addProvenanceRecord(Specimen specimen, String activity) {
        try {
            Provenance provenance = new Provenance();
            provenance.setId(UUID.randomUUID().toString());
            provenance.addTarget(new Reference("Specimen/" + specimen.getIdElement().getIdPart()));
            provenance.setRecorded(new Date());
            
            // Add activity
            CodeableConcept activityCode = new CodeableConcept();
            activityCode.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/v3-DataOperation")
                .setCode(activity)
                .setDisplay(activity.toLowerCase());
            provenance.setActivity(activityCode);
            
            // Add agent
            // Provenance.ProvenanceAgentComponent agent = provenance.addAgent();
            // agent.setType(new CodeableConcept()
            //     .addCoding()
            //         .setSystem("http://terminology.hl7.org/CodeSystem/provenance-participant-type")
            //         .setCode("author")
            //         .setDisplay("Author"));
            // agent.setWho(new Reference().setDisplay("Biobank System"));

            Provenance.ProvenanceAgentComponent agent = provenance.addAgent();
            CodeableConcept agentType = new CodeableConcept();
            agentType.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/provenance-participant-type")
                .setCode("author")
                .setDisplay("Author");
            agent.setType(agentType);
            agent.setWho(new Reference().setDisplay("Biobank System"));
            
            provenanceDao.create(provenance);
        } catch (Exception e) {
            // Log error but don't fail the main operation
            org.slf4j.LoggerFactory.getLogger(SpecimenService.class)
                .error("Failed to create provenance record", e);
        }
    }

    private void initializeStorageTracking(Specimen specimen) {
        Extension storageExt = new Extension();
        storageExt.setUrl("http://hospital.local/fhir/StructureDefinition/storage-history");
        
        Extension initialStorage = storageExt.addExtension();
        initialStorage.setUrl("storage-event");
        initialStorage.addExtension("timestamp", new DateTimeType(new Date()));
        initialStorage.addExtension("action", new StringType("received"));
        initialStorage.addExtension("location", new StringType("Receiving Area"));
        
        specimen.addExtension(storageExt);
    }

    private void trackSpecimenChanges(Specimen existing, Specimen updated) {
        List<String> changes = new ArrayList<>();
        
        if (!Objects.equals(existing.getStatus(), updated.getStatus())) {
            changes.add("Status changed from " + existing.getStatus() + " to " + updated.getStatus());
        }
        
        if (!Objects.equals(existing.getType(), updated.getType())) {
            changes.add("Type changed");
        }
        
        if (!changes.isEmpty()) {
            Extension changeExt = new Extension();
            changeExt.setUrl("http://hospital.local/fhir/StructureDefinition/change-log");
            changeExt.addExtension("timestamp", new DateTimeType(new Date()));
            changeExt.addExtension("changes", new StringType(String.join("; ", changes)));
            
            updated.addExtension(changeExt);
        }
    }

    private void addLocationChangeProvenance(Specimen specimen, Reference newLocation, String notes) {
        try {
            Provenance provenance = new Provenance();
            provenance.setId(UUID.randomUUID().toString());
            provenance.addTarget(new Reference("Specimen/" + specimen.getIdElement().getIdPart()));
            provenance.setRecorded(new Date());
            
            // Add location change activity
            CodeableConcept activityCode = new CodeableConcept();
            activityCode.addCoding()
                .setSystem("http://hospital.local/fhir/CodeSystem/biobank-activities")
                .setCode("LOCATION_CHANGE")
                .setDisplay("Location Change");
            provenance.setActivity(activityCode);
            
            // Add location details
            Extension locationExt = new Extension();
            locationExt.setUrl("http://hospital.local/fhir/StructureDefinition/location-change");
            locationExt.addExtension("new-location", newLocation);
            if (notes != null) {
                locationExt.addExtension("notes", new StringType(notes));
            }
            provenance.addExtension(locationExt);
            
            // Add agent
            // Provenance.ProvenanceAgentComponent agent = provenance.addAgent();
            // agent.setType(new CodeableConcept()
            //     .addCoding()
            //         .setSystem("http://terminology.hl7.org/CodeSystem/provenance-participant-type")
            //         .setCode("author")
            //         .setDisplay("Author"));
            // agent.setWho(new Reference().setDisplay("Biobank System"));

            Provenance.ProvenanceAgentComponent agent = provenance.addAgent();
            CodeableConcept agentType = new CodeableConcept();
            agentType.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/provenance-participant-type")
                .setCode("author")
                .setDisplay("Author");
            agent.setType(agentType);
            agent.setWho(new Reference().setDisplay("Biobank System"));
            
            provenanceDao.create(provenance);
        } catch (Exception e) {
            // Log error but don't fail the main operation
            org.slf4j.LoggerFactory.getLogger(SpecimenService.class)
                .error("Failed to create location change provenance", e);
        }
    }

    private OperationOutcome createOperationOutcome(String message) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
            .setCode(OperationOutcome.IssueType.PROCESSING)
            .setDiagnostics(message);
        return outcome;
    }
}