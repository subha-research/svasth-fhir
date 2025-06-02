package com.svasamm.fhir.config;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.svasamm.fhir.ehr.provider.PatientResourceProvider;
import com.svasamm.fhir.ehr.provider.PractitionerResourceProvider;
import com.svasamm.fhir.biobank.provider.SpecimenResourceProvider;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Specimen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced configuration that ensures custom providers are properly registered
 * Only active when NOT in test profile and when R4 classes are available
 */
@Configuration
@ConditionalOnClass({Patient.class, Specimen.class, Practitioner.class})
@ConditionalOnProperty(name = "hapi.fhir.fhir_version", havingValue = "R4", matchIfMissing = true)
@Profile("!test")  // Exclude from test profile
public class CustomResourceProviderConfig implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(CustomResourceProviderConfig.class);

    @Autowired(required = false)
    private IFhirResourceDao<Patient> patientDao;

    @Autowired(required = false)
    private IFhirResourceDao<Specimen> specimenDao;

    @Autowired(required = false)
    private IFhirResourceDao<Practitioner> practitionerDao;

    @Autowired(required = false)
    private RestfulServer restfulServer;

    // Inject the Spring beans that we created
    @Autowired(required = false)
    @Qualifier("patientResourceProvider")
    private BaseJpaResourceProvider<Patient> customPatientProvider;

    @Autowired(required = false)
    @Qualifier("specimenResourceProvider")
    private BaseJpaResourceProvider<Specimen> customSpecimenProvider;

    @Autowired(required = false)
    @Qualifier("practitionerResourceProvider")
    private BaseJpaResourceProvider<Practitioner> customPractitionerProvider;

    private boolean providersRegistered = false;

    /**
     * Custom Patient Resource Provider Bean
     */
    @Bean(name = "patientResourceProvider")
    @Primary
    @ConditionalOnClass(Patient.class)
    public BaseJpaResourceProvider<Patient> patientResourceProvider() {
        if (patientDao == null) {
            logger.warn("PatientDao not available, skipping custom provider creation");
            return null;
        }
        logger.info("🏥 Creating PRIMARY Custom PatientResourceProvider Bean with JPA DAO");
        return new PatientResourceProvider(patientDao);
    }

    /**
     * Custom Specimen Resource Provider Bean
     */
    @Bean(name = "specimenResourceProvider")
    @Primary
    @ConditionalOnClass(Specimen.class)
    public BaseJpaResourceProvider<Specimen> specimenResourceProvider() {
        if (specimenDao == null) {
            logger.warn("SpecimenDao not available, skipping custom provider creation");
            return null;
        }
        logger.info("🔬 Creating PRIMARY Custom SpecimenResourceProvider Bean with JPA DAO");
        return new SpecimenResourceProvider(specimenDao);
    }

    /**
     * Custom Practitioner Resource Provider Bean
     */
    @Bean(name = "practitionerResourceProvider")
    @Primary
    @ConditionalOnClass(Practitioner.class)
    public BaseJpaResourceProvider<Practitioner> practitionerResourceProvider() {
        if (practitionerDao == null) {
            logger.warn("PractitionerDao not available, skipping custom provider creation");
            return null;
        }
        logger.info("👨‍⚕️ Creating PRIMARY Custom PractitionerResourceProvider Bean with JPA DAO");
        return new PractitionerResourceProvider(practitionerDao);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // Skip if not R4 or dependencies not available
        if (providersRegistered || restfulServer == null || patientDao == null) {
            return;
        }

        logger.info("🚀 APPLICATION READY - ENSURING CUSTOM PROVIDERS ARE ACTIVE 🚀");

        try {
            // Log all current providers
            List<IResourceProvider> currentProviders = restfulServer.getResourceProviders();
            logger.info("📋 Current registered providers:");
            for (IResourceProvider provider : currentProviders) {
                logger.info("  - {} handles {}",
                    provider.getClass().getSimpleName(),
                    provider.getResourceType().getSimpleName());
            }

            // Check if our custom providers are already registered
            boolean hasCustomPatient = currentProviders.stream()
                .anyMatch(p -> p instanceof PatientResourceProvider);
            boolean hasCustomSpecimen = currentProviders.stream()
                .anyMatch(p -> p instanceof SpecimenResourceProvider);
            boolean hasCustomPractitioner = currentProviders.stream()
                .anyMatch(p -> p instanceof PractitionerResourceProvider);

            if (hasCustomPatient && hasCustomSpecimen && hasCustomPractitioner) {
                logger.info("✅ All custom providers are already registered!");
                providersRegistered = true;
                return;
            }

            // If not, force registration
            logger.warn("⚠️ Custom providers not found, forcing registration...");

            // Remove existing providers and register custom ones
            replaceProviders();
            providersRegistered = true;

        } catch (Exception e) {
            logger.error("❌ Error ensuring custom providers: ", e);
        }
    }

    private void replaceProviders() {
        if (customPatientProvider == null || customSpecimenProvider == null || customPractitionerProvider == null) {
            logger.warn("Custom providers not available, skipping replacement");
            return;
        }

        List<IResourceProvider> currentProviders = new ArrayList<>(restfulServer.getResourceProviders());

        // Remove existing Patient provider
        removeExistingProvider(currentProviders, Patient.class, "Patient");

        // Remove existing Specimen provider
        removeExistingProvider(currentProviders, Specimen.class, "Specimen");

        // Remove existing Practitioner provider
        removeExistingProvider(currentProviders, Practitioner.class, "Practitioner");

        // Register the Spring-managed beans (with proper dependency injection)
        logger.info("🔄 Registering CUSTOM PatientResourceProvider (Spring Bean)");
        restfulServer.registerProvider(customPatientProvider);

        logger.info("🔄 Registering CUSTOM SpecimenResourceProvider (Spring Bean)");
        restfulServer.registerProvider(customSpecimenProvider);

        logger.info("🔄 Registering CUSTOM PractitionerResourceProvider (Spring Bean)");
        restfulServer.registerProvider(customPractitionerProvider);

        logger.info("🎯 Custom providers force-registered successfully!");
    }

    private void removeExistingProvider(List<IResourceProvider> providers, Class<?> resourceType, String typeName) {
        IResourceProvider existingProvider = providers.stream()
            .filter(p -> p.getResourceType().equals(resourceType))
            .findFirst().orElse(null);

        if (existingProvider != null) {
            logger.info("🗑️ Unregistering existing {} provider: {}", typeName, existingProvider.getClass().getName());
            restfulServer.unregisterProvider(existingProvider);
        }
    }
}