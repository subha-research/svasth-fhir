package com.svasamm.fhir.biobank.config;

import com.svasamm.fhir.biobank.provider.SpecimenResourceProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "modules.biobank.enabled", havingValue = "true")
@ComponentScan(basePackages = "com.svasamm.fhir.biobank")
public class BiobankConfig {

    @Bean
    @ConditionalOnProperty(name = "modules.biobank.specimen-tracking", havingValue = "true", matchIfMissing = true)
    public SpecimenResourceProvider specimenResourceProvider() {
        return new SpecimenResourceProvider();
    }
}