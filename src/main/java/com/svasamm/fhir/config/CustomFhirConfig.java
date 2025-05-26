package com.svasamm.fhir.config;

import com.svasamm.fhir.interceptor.AuditInterceptor;
import com.svasamm.fhir.interceptor.ValidationInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    ConditionalConfig.class,
    HospitalConfig.class,
    ModuleConfig.class
})
public class CustomFhirConfig {

    @Bean
    public AuditInterceptor auditInterceptor() {
        return new AuditInterceptor();
    }

    @Bean
    public ValidationInterceptor validationInterceptor() {
        return new ValidationInterceptor();
    }
}