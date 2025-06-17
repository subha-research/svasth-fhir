package com.svasamm.fhir.ehr.provider;

import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.svasamm.fhir.config.HospitalConfig;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.annotation.ConditionalUrlParam;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Custom Practitioner Resource Provider that extends HAPI's JPA provider
 * This preserves all JPA functionality (versioning, locking, etc.) while adding custom EHR logic
 */
public class PractitionerResourceProvider extends BaseJpaResourceProvider<Practitioner> {

    private static final Logger logger = LoggerFactory.getLogger(PractitionerResourceProvider.class);

    @Autowired
    private HospitalConfig hospitalConfig;

    public PractitionerResourceProvider(IFhirResourceDao<Practitioner> theDao) {
        super(theDao);
        logger.info("CUSTOM PractitionerResourceProvider CONSTRUCTOR CALLED with JPA DAO");
    }

    @Override
    public Class<Practitioner> getResourceType() {
        return Practitioner.class;
    }

    @Create
    // @Override
    public MethodOutcome create(
            @ResourceParam Practitioner thePractitioner,
            RequestDetails theRequestDetails) {

        logger.info("👨‍⚕️ CUSTOM PractitionerResourceProvider.create() called");

        try {
            // Apply custom EHR enrichment
            enrichPractitionerForEHR(thePractitioner);

            // Call parent JPA implementation to handle versioning/locking properly
            MethodOutcome result = getDao().create(thePractitioner, theRequestDetails);

            logger.info("Practitioner created successfully with ID: {}", result.getId());
            return result;

        } catch (Exception e) {
            logger.error("Error in CUSTOM practitioner create: ", e);
            throw e;
        }
    }

    @Update
    // @Override
    public MethodOutcome update(
            HttpServletRequest theRequest,
            @IdParam IdType theId,
            @ResourceParam Practitioner thePractitioner,
            @ConditionalUrlParam String theConditional,
            RequestDetails theRequestDetails) {

        logger.info("CUSTOM PractitionerResourceProvider.update() called for ID: {}", theId);

        try {
            // Apply custom EHR enrichment
            enrichPractitionerForEHR(thePractitioner);

            // Call parent JPA implementation - this handles optimistic locking automatically
            MethodOutcome result = super.update(theRequest, thePractitioner, theId, theConditional, theRequestDetails);

            logger.info("Practitioner updated successfully with ID: {}", result.getId());
            return result;

        } catch (Exception e) {
            logger.error("Error in CUSTOM practitioner update: ", e);
            throw e;
        }
    }

    @Search
    public List<Practitioner> search(
            @OptionalParam(name = Practitioner.SP_FAMILY) StringParam theFamily,
            @OptionalParam(name = Practitioner.SP_GIVEN) StringParam theGiven,
            @OptionalParam(name = Practitioner.SP_IDENTIFIER) TokenParam theIdentifier,
            @OptionalParam(name = Practitioner.SP_ACTIVE) TokenParam theActive) {

        // Build search parameters and use JPA search
        ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = buildSearchParams(theFamily, theGiven, theIdentifier, theActive);

        try {
            return getDao().search(searchParams).getResources(0, 100)
                .stream()
                .map(resource -> (Practitioner) resource)
                .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            logger.error("Error in practitioner search: ", e);
            return java.util.Collections.emptyList();
        }
    }

    private void enrichPractitionerForEHR(Practitioner practitioner) {
        // Ensure NPI identifier
        boolean hasNPI = practitioner.getIdentifier().stream()
            .anyMatch(id -> "http://hl7.org/fhir/sid/us-npi".equals(id.getSystem()));

        if (!hasNPI) {
            Identifier npi = new Identifier();
            npi.setSystem("http://hl7.org/fhir/sid/us-npi");
            npi.setValue("1234567890"); // In production, generate or validate real NPI
            npi.setUse(Identifier.IdentifierUse.OFFICIAL);

            practitioner.addIdentifier(npi);
        }

        // Add hospital identifier
        Identifier hospitalId = new Identifier();
        hospitalId.setSystem("urn:practitioner:" + hospitalConfig.getIdentifier());
        hospitalId.setValue("PRAC-" + System.currentTimeMillis());
        hospitalId.setUse(Identifier.IdentifierUse.SECONDARY);
        practitioner.addIdentifier(hospitalId);

        // Add EHR metadata
        if (practitioner.getMeta() == null) {
            practitioner.setMeta(new Meta());
        }
        practitioner.getMeta()
            .addProfile("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/ehr-practitioner")
            .addTag()
                .setSystem("http://" + hospitalConfig.getIdentifier() + "/fhir/CodeSystem/data-source")
                .setCode("ehr")
                .setDisplay("Electronic Health Record");

        // Add audit extension
        Extension auditExtension = new Extension();
        auditExtension.setUrl("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/audit-trail");
        auditExtension.addExtension("created-date", new DateTimeType(new Date()));
        auditExtension.addExtension("created-by", new StringType("EHR System"));
        practitioner.addExtension(auditExtension);
    }

    private ca.uhn.fhir.jpa.searchparam.SearchParameterMap buildSearchParams(
            StringParam theFamily, StringParam theGiven, TokenParam theIdentifier, TokenParam theActive) {

        ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = new ca.uhn.fhir.jpa.searchparam.SearchParameterMap();

        if (theFamily != null) {
            searchParams.add(Practitioner.SP_FAMILY, theFamily);
        }
        if (theGiven != null) {
            searchParams.add(Practitioner.SP_GIVEN, theGiven);
        }
        if (theIdentifier != null) {
            searchParams.add(Practitioner.SP_IDENTIFIER, theIdentifier);
        }
        if (theActive != null) {
            searchParams.add(Practitioner.SP_ACTIVE, theActive);
        }

        return searchParams;
    }
}