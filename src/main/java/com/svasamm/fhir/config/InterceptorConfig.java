package com.svasamm.fhir.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.svasamm.fhir.interceptor.JwtAuthorizationInterceptor;

import ca.uhn.fhir.rest.server.RestfulServer;

@Component
public class InterceptorConfig {

    @Autowired
    private JwtAuthorizationInterceptor jwtInterceptor;

    @Autowired
    private RestfulServer restfulServer;

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        // Register your interceptor after context is loaded
        restfulServer.registerInterceptor(jwtInterceptor);
    }
}
