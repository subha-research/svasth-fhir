package com.svasamm.fhir.biobank.provider;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.*;
import jakarta.servlet.http.HttpServletRequest;

import com.svasamm.fhir.biobank.service.SpecimenService;
import com.svasamm.fhir.config.HospitalConfig;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Random;

/**
 * Custom Specimen Resource Provider that extends HAPI's JPA provider
 * This preserves all JPA functionality (versioning, locking, etc.) while adding custom biobank logic
 */
public class SpecimenResourceProvider extends BaseJpaResourceProvider<Specimen> {

    private static final Logger logger = LoggerFactory.getLogger(SpecimenResourceProvider.class);

    @Autowired
    private SpecimenService specimenService;

    @Autowired
    private HospitalConfig hospitalConfig;

    public SpecimenResourceProvider(IFhirResourceDao<Specimen> theDao) {
        super(theDao);
        logger.info("🔬 CUSTOM SpecimenResourceProvider CONSTRUCTOR CALLED with JPA DAO 🔬");
    }

    @Override
    public Class<Specimen> getResourceType() {
        return Specimen.class;
    }

    @Create
    public MethodOutcome create(
            @ResourceParam Specimen theSpecimen,
            RequestDetails theRequestDetails) {

        logger.info("CUSTOM SpecimenResourceProvider.create() called");

        try {
            // Apply custom biobank enrichment
            enrichSpecimenForBiobank(theSpecimen);

            // Call parent JPA implementation to handle versioning/locking properly
            MethodOutcome result =  getDao().create(theSpecimen, theRequestDetails);

            return result;

        } catch (Exception e) {
            logger.error("Error in CUSTOM specimen create: ", e);
            throw e;
        }
    }

    @Update
    // @Override
    public MethodOutcome update(
            HttpServletRequest theRequest,
            @IdParam IdType theId,
            @ResourceParam Specimen theSpecimen,
            @ConditionalUrlParam String theConditional,
            RequestDetails theRequestDetails) {

        logger.info("🔄 CUSTOM SpecimenResourceProvider.update() called for ID: {}", theId);

        try {
            // Apply custom biobank enrichment
            enrichSpecimenForBiobank(theSpecimen);

            // Call parent JPA implementation - this handles optimistic locking automatically
            MethodOutcome result = super.update(theRequest, theSpecimen, theId, theConditional, theRequestDetails);

            // Custom post-processing
            if (specimenService != null) {
                logger.info("Specimen updated successfully with ID: {}", result.getId());
            }

            return result;

        } catch (Exception e) {
            logger.error("Error in CUSTOM specimen update: ", e);
            throw e;
        }
    }

    @Search
    public List<Specimen> search(
            @OptionalParam(name = Specimen.SP_SUBJECT) ReferenceParam theSubject,
            @OptionalParam(name = Specimen.SP_IDENTIFIER) TokenParam theIdentifier,
            @OptionalParam(name = Specimen.SP_TYPE) TokenParam theType,
            @OptionalParam(name = Specimen.SP_STATUS) TokenParam theStatus,
            @OptionalParam(name = Specimen.SP_COLLECTED) DateRangeParam theCollected,
            @OptionalParam(name = "_count") NumberParam theCount) {

        // If you have custom search logic, use your service
        if (specimenService != null) {
            return specimenService.searchSpecimens(theSubject, theIdentifier, theType,
                                                 theStatus, theCollected, theCount);
        }

        // Otherwise, fall back to default JPA search
        return searchByParameters(buildSearchParams(theSubject, theIdentifier, theType, theStatus, theCollected, theCount));
    }

    // Your custom operations remain the same
    @Operation(name = "$chain-of-custody", idempotent = true)
    public Bundle chainOfCustody(@IdParam IdType theSpecimenId) {
        if (specimenService != null) {
            return specimenService.getChainOfCustody(theSpecimenId.getIdPart());
        }
        throw new UnsupportedOperationException("Specimen service not available");
    }

    @Operation(name = "$specimen-location", idempotent = true)
    public Location specimenLocation(@IdParam IdType theSpecimenId) {
        if (specimenService != null) {
            return specimenService.getCurrentLocation(theSpecimenId.getIdPart());
        }
        throw new UnsupportedOperationException("Specimen service not available");
    }

    @Operation(name = "$update-location")
    public MethodOutcome updateLocation(
            @IdParam IdType theSpecimenId,
            @OperationParam(name = "location", min = 1) Reference location,
            @OperationParam(name = "notes") String notes) {

        if (specimenService != null) {
            return specimenService.updateLocation(theSpecimenId.getIdPart(), location,
                                                notes != null ? notes : null);
        }
        throw new UnsupportedOperationException("Specimen service not available");
    }

    @Operation(name = "$batch-update-location")
    public Bundle batchUpdateLocation(
            @OperationParam(name = "specimens", min = 1, max = OperationParam.MAX_UNLIMITED)
            List<IdType> specimenIds,
            @OperationParam(name = "location", min = 1) Reference location) {

        if (specimenService != null) {
            return specimenService.batchUpdateLocation(specimenIds, location);
        }
        throw new UnsupportedOperationException("Specimen service not available");
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

    // Helper methods for search
    private List<Specimen> searchByParameters(ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams) {
        try {
            return getDao().search(searchParams).getResources(0, 100)
                .stream()
                .map(resource -> (Specimen) resource)
                .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            logger.error("Error in specimen search: ", e);
            return java.util.Collections.emptyList();
        }
    }

    private ca.uhn.fhir.jpa.searchparam.SearchParameterMap buildSearchParams(
            ReferenceParam theSubject, TokenParam theIdentifier, TokenParam theType,
            TokenParam theStatus, DateRangeParam theCollected, NumberParam theCount) {

        ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = new ca.uhn.fhir.jpa.searchparam.SearchParameterMap();

        if (theSubject != null) {
            searchParams.add(Specimen.SP_SUBJECT, theSubject);
        }
        if (theIdentifier != null) {
            searchParams.add(Specimen.SP_IDENTIFIER, theIdentifier);
        }
        if (theType != null) {
            searchParams.add(Specimen.SP_TYPE, theType);
        }
        if (theStatus != null) {
            searchParams.add(Specimen.SP_STATUS, theStatus);
        }
        if (theCollected != null) {
            searchParams.add(Specimen.SP_COLLECTED, theCollected);
        }

        return searchParams;
    }
}