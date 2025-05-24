package com.svasamm.fhir.config;

import com.svasamm.fhir.ehr.config.EhrConfig;
import com.svasamm.fhir.biobank.config.BiobankConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
public class ConditionalConfig {

    @Configuration
    @ConditionalOnProperty(name = "modules.ehr.enabled", havingValue = "true", matchIfMissing = true)
    @Import(EhrConfig.class)
    static class EhrConditionalConfig {
        // EHR module will be loaded only if enabled
    }

    @Configuration
    @ConditionalOnProperty(name = "modules.biobank.enabled", havingValue = "true", matchIfMissing = true)
    @Import(BiobankConfig.class)
    static class BiobankConditionalConfig {
        // Biobank module will be loaded only if enabled
    }
}