package com.svasamm.fhir.biobank.provider;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.svasamm.fhir.biobank.service.SpecimenService;
import com.svasamm.fhir.config.HospitalConfig;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Random;

public class SpecimenResourceProvider implements IResourceProvider {

    @Autowired
    private SpecimenService specimenService;

    @Autowired
    private HospitalConfig hospitalConfig;

    @Override
    public Class<Specimen> getResourceType() {
        return Specimen.class;
    }

    @Read
    public Specimen read(@IdParam IdType theId) {
        Specimen specimen = specimenService.getSpecimenById(theId.getIdPart());
        if (specimen == null) {
            throw new ResourceNotFoundException(theId);
        }
        return specimen;
    }

    @Create
    public MethodOutcome create(@ResourceParam Specimen theSpecimen) {
        enrichSpecimenForBiobank(theSpecimen);
        return specimenService.createSpecimen(theSpecimen);
    }

    @Update
    public MethodOutcome update(@IdParam IdType theId, @ResourceParam Specimen theSpecimen) {
        return specimenService.updateSpecimen(theId.getIdPart(), theSpecimen);
    }

    @Search
    public List<Specimen> search(
            @OptionalParam(name = Specimen.SP_SUBJECT) ReferenceParam theSubject,
            @OptionalParam(name = Specimen.SP_IDENTIFIER) TokenParam theIdentifier,
            @OptionalParam(name = Specimen.SP_TYPE) TokenParam theType,
            @OptionalParam(name = Specimen.SP_STATUS) TokenParam theStatus,
            @OptionalParam(name = Specimen.SP_COLLECTED) DateRangeParam theCollected,
            @OptionalParam(name = "_count") NumberParam theCount) {
        
        return specimenService.searchSpecimens(theSubject, theIdentifier, theType, 
                                             theStatus, theCollected, theCount);
    }

    @Operation(name = "$chain-of-custody", idempotent = true)
    public Bundle chainOfCustody(@IdParam IdType theSpecimenId) {
        return specimenService.getChainOfCustody(theSpecimenId.getIdPart());
    }

    @Operation(name = "$specimen-location", idempotent = true)
    public Location specimenLocation(@IdParam IdType theSpecimenId) {
        return specimenService.getCurrentLocation(theSpecimenId.getIdPart());
    }

    @Operation(name = "$update-location")
    public MethodOutcome updateLocation(
            @IdParam IdType theSpecimenId,
            @OperationParam(name = "location", min = 1) Reference location,
            @OperationParam(name = "notes") String notes) {
        
        return specimenService.updateLocation(theSpecimenId.getIdPart(), location, 
                                            notes != null ? notes : null);
    }

    @Operation(name = "$batch-update-location")
    public Bundle batchUpdateLocation(
            @OperationParam(name = "specimens", min = 1, max = OperationParam.MAX_UNLIMITED) 
            List<IdType> specimenIds,
            @OperationParam(name = "location", min = 1) Reference location) {
        
        return specimenService.batchUpdateLocation(specimenIds, location);
    }

    private void enrichSpecimenForBiobank(Specimen specimen) {
        // Generate biobank identifier if not present
        boolean hasBiobankId = specimen.getIdentifier().stream()
            .anyMatch(id -> id.getSystem().contains("biobank"));
        
        if (!hasBiobankId) {
            Identifier biobankId = new Identifier();
            biobankId.setSystem("urn:biobank:" + hospitalConfig.getIdentifier());
            biobankId.setValue(generateBiobankId());
            biobankId.setUse(Identifier.IdentifierUse.USUAL);
            
            specimen.addIdentifier(biobankId);
        }

        // Add collection facility
        if (specimen.getCollection() == null) {
            specimen.setCollection(new Specimen.SpecimenCollectionComponent());
        }
        
        if (specimen.getCollection().getCollector() == null) {
            specimen.getCollection().setCollector(
                new Reference("Organization/" + hospitalConfig.getIdentifier())
                    .setDisplay(hospitalConfig.getName())
            );
        }

        // Add biobank metadata
        if (specimen.getMeta() == null) {
            specimen.setMeta(new Meta());
        }
        specimen.getMeta()
            .addProfile("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/biobank-specimen")
            .addTag()
                .setSystem("http://" + hospitalConfig.getIdentifier() + "/fhir/CodeSystem/data-source")
                .setCode("biobank")
                .setDisplay("Biobank");

        // Add initial location extension
        Extension locationExt = new Extension();
        locationExt.setUrl("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/current-location");
        locationExt.setValue(new StringType("Receiving Area"));
        specimen.addExtension(locationExt);
    }

    private String generateBiobankId() {
        return hospitalConfig.getIdentifier() + "-BB-" + System.currentTimeMillis() + 
               String.format("%03d", new Random().nextInt(1000));
    }
}