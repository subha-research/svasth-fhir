package com.svasamm.fhir.config;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.svasamm.fhir.ehr.provider.PatientResourceProvider;
import com.svasamm.fhir.ehr.provider.PractitionerResourceProvider;
import com.svasamm.fhir.biobank.provider.SpecimenResourceProvider;
import com.svasamm.fhir.ehr.provider.MedicationResourceProvider;
import com.svasamm.fhir.ehr.provider.ObservationResourceProvider;
import org.hl7.fhir.r4.model.Observation;
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
import com.svasamm.fhir.ehr.provider.VisitResourceProvider;



/**
 * Enhanced configuration that ensures custom providers are properly registered
 * Only active when NOT in test profile and when R4 classes are available
 */
@Configuration
@ConditionalOnClass({ Patient.class, Specimen.class, Practitioner.class, Medication.class, Observation.class, Encounter.class  })
@ConditionalOnProperty(name = "hapi.fhir.fhir_version", havingValue = "R4", matchIfMissing = true)
@Profile("!test") // Exclude from test profile
public class CustomResourceProviderConfig implements ApplicationListener<ApplicationReadyEvent> {

	private static final Logger logger = LoggerFactory.getLogger(CustomResourceProviderConfig.class);

	@Autowired(required = false)
	private IFhirResourceDao<Patient> patientDao;

	@Autowired(required = false)
	private IFhirResourceDao<Encounter> EncounterDao;

	@Autowired(required = false)
	private IFhirResourceDao<Medication> medicationDao;

	@Autowired(required = false)
	private IFhirResourceDao<Specimen> specimenDao;

	@Autowired(required = false)
	private IFhirResourceDao<Practitioner> practitionerDao;

	@Autowired(required = false)
	private IFhirResourceDao<Observation> observationDao;

	@Autowired(required = false)
	private RestfulServer restfulServer;

	// Inject the Spring beans that we created
	@Autowired(required = false)
	@Qualifier("patientResourceProvider")
	private BaseJpaResourceProvider<Patient> customPatientProvider;

	@Autowired(required = false)
	@Qualifier("visitResourceProvider")
	private BaseJpaResourceProvider<Encounter> customEncounterProvider;

	@Autowired(required = false)
	@Qualifier("medicationResourceProvider")
	private BaseJpaResourceProvider<Medication> customMedicationProvider;

	@Autowired(required = false)
	@Qualifier("specimenResourceProvider")
	private BaseJpaResourceProvider<Specimen> customSpecimenProvider;

	@Autowired(required = false)
	@Qualifier("practitionerResourceProvider")
	private BaseJpaResourceProvider<Practitioner> customPractitionerProvider;

	@Autowired(required = false)
	@Qualifier("observationResourceProvider")
	private BaseJpaResourceProvider<Observation> customObservationProvider;

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

	@Bean(name = "medicationResourceProvider")
	@Primary
	@ConditionalOnClass(Medication.class)
	public BaseJpaResourceProvider<Medication> medicationResourceProvider() {
		if (medicationDao == null) {
			logger.warn("MedicationDao not available, skipping custom provider creation");
			return null;
		}
		logger.info("💊 Creating PRIMARY Custom MedicationResourceProvider Bean with JPA DAO");
		return new MedicationResourceProvider(medicationDao);
	}

	/**
	 * Custom Visit Resource Provider Bean
	 */
	@Bean(name = "visitResourceProvider")
	@Primary
	@ConditionalOnClass(Encounter.class)
	public BaseJpaResourceProvider<Encounter> visitResourceProvider() {
		if (EncounterDao == null) {
			logger.warn("EncounterDao not available, skipping custom provider creation");
			return null;
		}
		logger.info("🏥 Creating PRIMARY Custom visitResourceProvider Bean with JPA DAO");
		return new VisitResourceProvider(EncounterDao);
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

	/**
	 * Custom Observation Resource Provider Bean
	 */
	@Bean(name = "observationResourceProvider")
	@Primary
	@ConditionalOnClass(Observation.class)
	public BaseJpaResourceProvider<Observation> observationResourceProvider() {
		if (observationDao == null) {
			logger.warn("ObservationDao not available, skipping custom provider creation");
			return null;
		}
		logger.info("📊 Creating PRIMARY Custom ObservationResourceProvider Bean with JPA DAO");
		return new ObservationResourceProvider(observationDao);
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		// Skip if not R4 or dependencies not available
		if (providersRegistered || restfulServer == null || patientDao == null || medicationDao == null || EncounterDao == null) {
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
			boolean hasCustomMedication = currentProviders.stream()
					.anyMatch(p -> p instanceof MedicationResourceProvider);
			boolean hasCustomEncounter = currentProviders.stream()
					.anyMatch(p -> p instanceof VisitResourceProvider);
			boolean hasCustomObservation = currentProviders.stream()
					.anyMatch(p -> p instanceof ObservationResourceProvider);

			if (hasCustomPatient && hasCustomSpecimen && hasCustomPractitioner && hasCustomMedication && hasCustomEncounter && hasCustomObservation) {
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
		if (customPatientProvider == null || customSpecimenProvider == null || customPractitionerProvider == null || customMedicationProvider == null || customObservationProvider == null) {

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

		// Remove existing Medication provider
		removeExistingProvider(currentProviders, Medication.class, "Medication");

		// Remove existing Visit provider
		removeExistingProvider(currentProviders, Encounter.class, "Encounter");

		// Remove existing Observation provider
		removeExistingProvider(currentProviders, Observation.class, "Observation");

		// Register the Spring-managed beans (with proper dependency injection)
		logger.info("🔄 Registering CUSTOM PatientResourceProvider (Spring Bean)");
		restfulServer.registerProvider(customPatientProvider);

		logger.info("🔄 Registering CUSTOM SpecimenResourceProvider (Spring Bean)");
		restfulServer.registerProvider(customSpecimenProvider);

		logger.info("🔄 Registering CUSTOM PractitionerResourceProvider (Spring Bean)");
		restfulServer.registerProvider(customPractitionerProvider);

		logger.info("🔄 Registering CUSTOM MedicationResourceProvider (Spring Bean)");
		restfulServer.registerProvider(customMedicationProvider);

		logger.info("🔄 Registering CUSTOM MedicationResourceProvider (Spring Bean)");
		restfulServer.registerProvider(customEncounterProvider);

		logger.info("🔄 Registering CUSTOM ObservationResourceProvider (Spring Bean)");
		restfulServer.registerProvider(customObservationProvider);

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