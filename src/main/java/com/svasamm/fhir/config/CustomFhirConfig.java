
// package com.svasamm.fhir.config;

// import com.svasamm.fhir.biobank.config.BiobankConfig;
// import com.svasamm.fhir.ehr.config.EhrConfig;
// import com.svasamm.interceptor.AuditInterceptor;
// import com.svasamm.interceptor.ValidationInterceptor;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.context.annotation.Import;

// @Configuration
// @Import({
//     EhrConfig.class,
//     BiobankConfig.class,
//     HospitalConfig.class,
//     AuditInterceptor.class,
//     ValidationInterceptor.class
// })
// public class CustomFhirConfig {
//     // This class just imports all your custom configurations
// }

package com.svasamm.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.interceptor.LoggingInterceptor;
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

    @Bean
    public LoggingInterceptor customLoggingInterceptor() {
        LoggingInterceptor interceptor = new LoggingInterceptor();
        interceptor.setLoggerName("fhir.access");
        interceptor.setMessageFormat("${requestVerb} ${requestUrl} - ${responseEncodingNoDefault} - ${idOrResourceName}");
        return interceptor;
    }
}