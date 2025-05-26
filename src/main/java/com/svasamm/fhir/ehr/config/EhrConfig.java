package com.svasamm.fhir.ehr.config;

import com.svasamm.fhir.ehr.provider.PatientResourceProvider;
import com.svasamm.fhir.ehr.provider.PractitionerResourceProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "modules.ehr.enabled", havingValue = "true")
@ComponentScan(basePackages = "com.svasamm.fhir.ehr")
public class EhrConfig {

    @Bean
    @ConditionalOnProperty(name = "modules.ehr.patient-management", havingValue = "true", matchIfMissing = true)
    public PatientResourceProvider patientResourceProvider() {
        return new PatientResourceProvider();
    }

    @Bean
    @ConditionalOnProperty(name = "modules.ehr.clinical-workflows", havingValue = "true", matchIfMissing = true)
    public PractitionerResourceProvider practitionerResourceProvider() {
        return new PractitionerResourceProvider();
    }
}