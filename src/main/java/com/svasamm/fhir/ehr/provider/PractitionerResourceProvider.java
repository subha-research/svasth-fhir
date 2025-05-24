package com.svasamm.fhir.ehr.provider;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.svasamm.fhir.config.HospitalConfig;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
// import ca.uhn.fhir.jpa.dao.IFhirResourceDao;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PractitionerResourceProvider implements IResourceProvider {

    @Autowired
    private ca.uhn.fhir.jpa.api.dao.IFhirResourceDao<Practitioner> practitionerDao;

    @Autowired
    private HospitalConfig hospitalConfig;

    @Override
    public Class<Practitioner> getResourceType() {
        return Practitioner.class;
    }

    @Read
    public Practitioner read(@IdParam IdType theId) {
        try {
            return practitionerDao.read(theId);
        } catch (Exception e) {
            throw new ResourceNotFoundException(theId);
        }
    }

    @Create
    public MethodOutcome create(@ResourceParam Practitioner thePractitioner) {
        enrichPractitionerForEHR(thePractitioner);
        return practitionerDao.create(thePractitioner);
    }

    @Update
    public MethodOutcome update(@IdParam IdType theId, @ResourceParam Practitioner thePractitioner) {
        thePractitioner.setId(theId);
        return practitionerDao.update(thePractitioner);
    }

    @Search
    public List<Practitioner> search(
            @OptionalParam(name = Practitioner.SP_FAMILY) StringParam theFamily,
            @OptionalParam(name = Practitioner.SP_GIVEN) StringParam theGiven,
            @OptionalParam(name = Practitioner.SP_IDENTIFIER) TokenParam theIdentifier,
            @OptionalParam(name = Practitioner.SP_ACTIVE) TokenParam theActive) {
        
        // Simple implementation - in production you'd use proper search
        return practitionerDao.search(new ca.uhn.fhir.jpa.searchparam.SearchParameterMap())
            .getResources(0, 100)
            .stream()
            .map(resource -> (Practitioner) resource)
            .collect(Collectors.toList());
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
    }
}