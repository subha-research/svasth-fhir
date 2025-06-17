package com.svasamm.fhir.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.svasamm.fhir.interceptor.JwtValidationInterceptor;

import ca.uhn.fhir.rest.server.RestfulServer;

@Component
public class InterceptorConfig {

    private static final Logger logger = LoggerFactory.getLogger(InterceptorConfig.class);

    @Autowired
    private JwtValidationInterceptor jwtValidationInterceptor;

    @Autowired
    private RestfulServer restfulServer;

    @EventListener
    public void registerInterceptors(ContextRefreshedEvent event) {
        logger.info("🔧 Registering JWT validation interceptor...");
        
        try {
            restfulServer.registerInterceptor(jwtValidationInterceptor);
            logger.info("✅ JWT validation interceptor registered successfully");
        } catch (Exception e) {
            logger.error("❌ Failed to register JWT interceptor: {}", e.getMessage());
        }
    }
}