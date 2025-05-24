package com.svasamm.fhir.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.svasamm.fhir.config.HospitalConfig;
import com.svasamm.fhir.config.ModuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Interceptor
public class AuditInterceptor {

    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");

    @Autowired
    private HospitalConfig hospitalConfig;

    @Autowired
    private ModuleConfig moduleConfig;

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
    public void auditIncomingRequest(RequestDetails theRequestDetails) {
        ServletRequestDetails servletDetails = (ServletRequestDetails) theRequestDetails;
        
        String module = determineModule(theRequestDetails.getResourceName());
        
        auditLogger.info("FHIR_ACCESS - Hospital: {}, Module: {}, Method: {}, Resource: {}, URL: {}, IP: {}, UserAgent: {}", 
            hospitalConfig.getIdentifier(),
            module,
            theRequestDetails.getRequestType(),
            theRequestDetails.getResourceName(),
            theRequestDetails.getCompleteUrl(),
            servletDetails.getServletRequest().getRemoteAddr(),
            servletDetails.getServletRequest().getHeader("User-Agent"));
    }

    @Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
    public void auditOutgoingResponse(RequestDetails theRequestDetails, 
                                    ca.uhn.fhir.rest.api.server.ResponseDetails theResponseDetails) {
        
        String module = determineModule(theRequestDetails.getResourceName());
        
        auditLogger.info("FHIR_RESPONSE - Hospital: {}, Module: {}, Status: {}, Resource: {}", 
            hospitalConfig.getIdentifier(),
            module,
            theResponseDetails.getResponseCode(),
            theResponseDetails.getResponseResource() != null ? 
                theResponseDetails.getResponseResource().getClass().getSimpleName() : "None");
    }

    private String determineModule(String resourceName) {
        if (resourceName == null) return "UNKNOWN";
        
        switch (resourceName.toLowerCase()) {
            case "patient":
            case "practitioner":
            case "encounter":
            case "observation":
                return "EHR";
            case "specimen":
            case "diagnosticreport":
                return "BIOBANK";
            default:
                return "CORE";
        }
    }
}