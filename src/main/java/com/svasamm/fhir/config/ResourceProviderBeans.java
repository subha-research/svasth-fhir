package com.svasamm.fhir.config;

import com.svasamm.fhir.ehr.provider.PatientResourceProvider;
import com.svasamm.fhir.biobank.provider.SpecimenResourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResourceProviderBeans {

    private static final Logger logger = LoggerFactory.getLogger(ResourceProviderBeans.class);

    @Bean
    public PatientResourceProvider customPatientResourceProvider() {
        logger.info("Creating custom PatientResourceProvider Bean with dependency injection");
        return new PatientResourceProvider();
    }

    @Bean
    public SpecimenResourceProvider customSpecimenResourceProvider() {
        logger.info("Creating custom SpecimenResourceProvider Bean with dependency injection");
        return new SpecimenResourceProvider();
    }
}