package com.svasamm.fhir.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import com.svasamm.fhir.config.ModuleConfig;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Specimen;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Interceptor
public class ValidationInterceptor {

    @Autowired
    private ModuleConfig moduleConfig;

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
    public void validateModuleAccess(RequestDetails theRequestDetails) {
        String resourceName = theRequestDetails.getResourceName();
        
        if (resourceName != null) {
            // Check if EHR resources are being accessed when EHR module is disabled
            if (isEhrResource(resourceName) && !moduleConfig.getEhr().isEnabled()) {
                throw new ForbiddenOperationException("EHR module is not enabled for this deployment");
            }
            
            // Check if Biobank resources are being accessed when Biobank module is disabled
            if (isBiobankResource(resourceName) && !moduleConfig.getBiobank().isEnabled()) {
                throw new ForbiddenOperationException("Biobank module is not enabled for this deployment");
            }
        }
    }

    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
    public void validateResourceCreation(RequestDetails theRequestDetails, Object theResource) {
        validateResource(theResource);
    }

    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED)
    public void validateResourceUpdate(RequestDetails theRequestDetails, Object theOldResource, Object theNewResource) {
        validateResource(theNewResource);
    }

    private void validateResource(Object resource) {
        if (resource instanceof Patient) {
            validatePatient((Patient) resource);
        } else if (resource instanceof Specimen) {
            validateSpecimen((Specimen) resource);
        }
    }

    private void validatePatient(Patient patient) {
        if (!moduleConfig.getEhr().isEnabled()) {
            throw new ForbiddenOperationException("Cannot create/update patients when EHR module is disabled");
        }
        
        if (patient.getName().isEmpty()) {
            throw new UnprocessableEntityException("Patient must have at least one name");
        }
        
        if (patient.getBirthDate() == null) {
            throw new UnprocessableEntityException("Patient birth date is required");
        }
    }

    private void validateSpecimen(Specimen specimen) {
        if (!moduleConfig.getBiobank().isEnabled()) {
            throw new ForbiddenOperationException("Cannot create/update specimens when Biobank module is disabled");
        }
        
        if (specimen.getSubject() == null) {
            throw new UnprocessableEntityException("Specimen must have a subject");
        }
        
        if (specimen.getType() == null || specimen.getType().getCoding().isEmpty()) {
            throw new UnprocessableEntityException("Specimen must have a type");
        }
    }

    private boolean isEhrResource(String resourceName) {
        switch (resourceName.toLowerCase()) {
            case "patient":
            case "practitioner":
            case "encounter":
            case "observation":
            case "condition":
            case "procedure":
            case "medicationrequest":
                return true;
            default:
                return false;
        }
    }

    private boolean isBiobankResource(String resourceName) {
        switch (resourceName.toLowerCase()) {
            case "specimen":
            case "diagnosticreport":
                return true;
            default:
                return false;
        }
    }
}