package com.svasamm.fhir.config;

import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.svasamm.fhir.ehr.provider.PatientResourceProvider;
import com.svasamm.fhir.biobank.provider.SpecimenResourceProvider;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Specimen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CustomProviderRegistrar implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(CustomProviderRegistrar.class);

    @Autowired
    private RestfulServer restfulServer;

    @Autowired
    @Qualifier("customPatientResourceProvider") 
    private PatientResourceProvider customPatientProvider;  // Inject the bean

    @Autowired
    @Qualifier("customSpecimenResourceProvider")
    private SpecimenResourceProvider customSpecimenProvider;  // Inject the bean

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        logger.info("🚀 APPLICATION READY - REPLACING PROVIDERS 🚀");
        
        try {
            // Remove existing providers (same code as before)
            List<IResourceProvider> currentProviders = new ArrayList<>(restfulServer.getResourceProviders());
            
            // Remove existing Patient provider
            IResourceProvider existingPatientProvider = currentProviders.stream()
                .filter(p -> p.getResourceType().equals(Patient.class))
                .findFirst().orElse(null);
            
            if (existingPatientProvider != null) {
                logger.info("🗑️ Unregistering existing Patient provider: {}", existingPatientProvider.getClass().getName());
                restfulServer.unregisterProvider(existingPatientProvider);
            }
            
            // Remove existing Specimen provider
            IResourceProvider existingSpecimenProvider = currentProviders.stream()
                .filter(p -> p.getResourceType().equals(Specimen.class))
                .findFirst().orElse(null);
            
            if (existingSpecimenProvider != null) {
                logger.info("🗑️ Unregistering existing Specimen provider: {}", existingSpecimenProvider.getClass().getName());
                restfulServer.unregisterProvider(existingSpecimenProvider);
            }
            
            // Register your custom providers (WITH DEPENDENCY INJECTION)
            logger.info("✅ Registering CUSTOM PatientResourceProvider with DI: {}", customPatientProvider.getClass().getName());
            restfulServer.registerProvider(customPatientProvider);
            
            logger.info("✅ Registering CUSTOM SpecimenResourceProvider with DI: {}", customSpecimenProvider.getClass().getName());
            restfulServer.registerProvider(customSpecimenProvider);
            
            logger.info("🎯 Custom providers registered successfully!");
            
        } catch (Exception e) {
            logger.error("❌ Error replacing providers: ", e);
        }
    }
}