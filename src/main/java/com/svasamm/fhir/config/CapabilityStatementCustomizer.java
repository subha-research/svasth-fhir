package com.svasamm.fhir.config;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Interceptor
public class CapabilityStatementCustomizer {

    @Autowired
    private ModuleConfig moduleConfig;

    @Autowired
    private HospitalConfig hospitalConfig;

    @Hook(Pointcut.SERVER_CAPABILITY_STATEMENT_GENERATED)
    public void customize(CapabilityStatement theCapabilityStatement, RequestDetails theRequestDetails) {
        // Customize the capability statement
        theCapabilityStatement.setName(hospitalConfig.getName() + " FHIR Platform");
        theCapabilityStatement.setPublisher(hospitalConfig.getName());
        theCapabilityStatement.setDescription("FHIR R4 Server for " + hospitalConfig.getName());
        
        // Filter resources based on enabled modules
        if (theCapabilityStatement.getRest() != null && !theCapabilityStatement.getRest().isEmpty()) {
            CapabilityStatement.CapabilityStatementRestComponent rest = theCapabilityStatement.getRestFirstRep();
            
            // Remove EHR resources if EHR module is disabled
            if (!moduleConfig.getEhr().isEnabled()) {
                rest.getResource().removeIf(resource -> 
                    "Patient".equals(resource.getType()) || 
                    "Practitioner".equals(resource.getType()));
            }
            
            // Remove Biobank resources if Biobank module is disabled
            if (!moduleConfig.getBiobank().isEnabled()) {
                rest.getResource().removeIf(resource -> 
                    "Specimen".equals(resource.getType()));
            }
        }
    }
}