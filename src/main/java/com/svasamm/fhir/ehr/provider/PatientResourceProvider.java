// package com.svasamm.fhir.ehr.provider;
// import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
// import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
// import ca.uhn.fhir.rest.annotation.*;
// import ca.uhn.fhir.rest.api.MethodOutcome;
// import ca.uhn.fhir.rest.api.server.RequestDetails;
// import ca.uhn.fhir.rest.param.*;
// import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
// import jakarta.servlet.http.HttpServletRequest;
// import com.svasamm.fhir.ehr.service.PatientService;
// import com.svasamm.fhir.config.HospitalConfig;
// import org.hl7.fhir.r4.model.*;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import java.util.Date;
// import java.util.List;
// import java.util.Random;
// /**
//  * Custom Patient Resource Provider that extends HAPI's JPA provider
//  * This preserves all JPA functionality (versioning, locking, etc.) while adding custom logic
//  */
// public class PatientResourceProvider extends BaseJpaResourceProvider<Patient> {
//     private static final Logger logger = LoggerFactory.getLogger(PatientResourceProvider.class);
//     @Autowired
//     private PatientService patientService;
//     @Autowired
//     private HospitalConfig hospitalConfig;
//     public PatientResourceProvider(IFhirResourceDao<Patient> theDao) {
//         super(theDao);
//         logger.info("🚀 CUSTOM PatientResourceProvider CONSTRUCTOR CALLED with JPA DAO 🚀");
//     }
//     @Override
//     public Class<Patient> getResourceType() {
//         logger.info("CUSTOM PatientResourceProvider.getResourceType() called");
//         return Patient.class;
//     }
//     @Create
//     public MethodOutcome create(@ResourceParam Patient thePatient, RequestDetails theRequestDetails) {
//         logger.error("Custom PatientResourceProvider.create() called");
//         logger.error("Patient name: {}", thePatient.getName().isEmpty() ? "No name" : thePatient.getName().get(0).getFamily());
//         try {
//             // Apply custom EHR enrichment
//             enrichPatientForEHR(thePatient);
//             // Use DAO directly instead of super.create()
//             MethodOutcome result = getDao().create(thePatient, theRequestDetails);
//             logger.error("Patient created with ID: {} ", result.getId());
//             return result;
//         } catch (Exception e) {
//             logger.error("Error in custom create patient provider: ", e);
//             throw e;
//         }
//     }
//     @Update
//     // @Override
//     public MethodOutcome update(
//             HttpServletRequest theRequest,
//             @IdParam IdType theId,
//             @ResourceParam Patient thePatient,
//             @ConditionalUrlParam String theConditional,
//             RequestDetails theRequestDetails) {
//         logger.info("Custom PatientResourceProvider.update() called for ID: {}", theId);
//         try {
//             // Apply custom EHR enrichment
//             enrichPatientForEHR(thePatient);
//             // Call parent JPA implementation - this handles optimistic locking automatically
//             MethodOutcome result = super.update(theRequest, thePatient, theId, theConditional, theRequestDetails);
//             // Custom post-processing
//             if (patientService != null) {
//                 // Add any additional custom logic here
//                 logger.info("Patient updated successfully with ID: {}", result.getId());
//             }
//             return result;
//         } catch (Exception e) {
//             logger.error("Error in CUSTOM update: ", e);
//             throw e;
//         }
//     }
//     @Read
//     // @Override
//     public Patient read(HttpServletRequest theRequest, @IdParam IdType theId, RequestDetails theRequestDetails) {
//         logger.info("CUSTOM PatientResourceProvider.read() called for ID: {}", theId);
//         try {
//             // Use the parent JPA implementation for reading
//             Patient patient = super.read(theRequest, theId, theRequestDetails);
//             // Add any custom post-read processing if needed
//             if (patientService != null) {
//                 // Custom logic after reading
//                 logger.debug("Patient read successfully: {}", theId);
//             }
//             return patient;
//         } catch (Exception e) {
//             logger.error("Error reading patient {}: ", theId, e);
//             throw new ResourceNotFoundException(theId);
//         }
//     }
//     // Keep your custom search if you need special logic, otherwise inherit from parent
//     @Search
//     public List<Patient> search(
//             @OptionalParam(name = Patient.SP_FAMILY) StringParam theFamily,
//             @OptionalParam(name = Patient.SP_GIVEN) StringParam theGiven,
//             @OptionalParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier,
//             @OptionalParam(name = Patient.SP_BIRTHDATE) DateParam theBirthDate,
//             @OptionalParam(name = Patient.SP_ACTIVE) TokenParam theActive,
//             @OptionalParam(name = "_count") NumberParam theCount) {
//         // If you have custom search logic, use your service
//         if (patientService != null) {
//             return patientService.searchPatients(theFamily, theGiven, theIdentifier,
//                                                theBirthDate, theActive, theCount);
//         }
//         // Otherwise, fall back to default JPA search
//         return searchByParameters(buildSearchParams(theFamily, theGiven, theIdentifier, theBirthDate, theActive, theCount));
//     }
//     // Your custom operations remain the same
//     @Operation(name = "$patient-summary", idempotent = true)
//     public Bundle patientSummary(@IdParam IdType thePatientId) {
//         if (patientService != null) {
//             return patientService.generatePatientSummary(thePatientId.getIdPart());
//         }
//         throw new UnsupportedOperationException("Patient service not available");
//     }
//     @Operation(name = "$patient-chart", idempotent = true)
//     public Bundle patientChart(
//             @IdParam IdType thePatientId,
//             @OperationParam(name = "start-date") DateParam startDate,
//             @OperationParam(name = "end-date") DateParam endDate) {
//         if (patientService != null) {
//             return patientService.generatePatientChart(
//                 thePatientId.getIdPart(),
//                 startDate != null ? startDate.getValue() : null,
//                 endDate != null ? endDate.getValue() : null
//             );
//         }
//         throw new UnsupportedOperationException("Patient service not available");
//     }
//     @Operation(name = "$merge-patients")
//     public MethodOutcome mergePatients(
//             @OperationParam(name = "source-patient", min = 1) IdType sourcePatient,
//             @OperationParam(name = "target-patient", min = 1) IdType targetPatient) {
//         if (patientService != null) {
//             return patientService.mergePatients(sourcePatient.getIdPart(), targetPatient.getIdPart());
//         }
//         throw new UnsupportedOperationException("Patient service not available");
//     }
//     private void enrichPatientForEHR(Patient patient) {
//         // Generate MRN if not present
//         boolean hasMRN = patient.getIdentifier().stream()
//             .anyMatch(id -> "MR".equals(id.getType().getCodingFirstRep().getCode()));
//         if (!hasMRN) {
//             Identifier mrn = new Identifier();
//             mrn.setSystem("urn:mrn:" + hospitalConfig.getIdentifier());
//             mrn.setValue(generateMRN());
//             mrn.getType().addCoding()
//                 .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
//                 .setCode("MR")
//                 .setDisplay("Medical Record Number");
//             mrn.setUse(Identifier.IdentifierUse.USUAL);
//             patient.addIdentifier(mrn);
//         }
//         // Add managing organization
//         if (patient.getManagingOrganization() == null) {
//             patient.setManagingOrganization(
//                 new Reference("Organization/" + hospitalConfig.getIdentifier())
//                     .setDisplay(hospitalConfig.getName())
//             );
//         }
//         // Add EHR metadata
//         if (patient.getMeta() == null) {
//             patient.setMeta(new Meta());
//         }
//         patient.getMeta()
//             .addProfile("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/ehr-patient")
//             .addTag()
//                 .setSystem("http://" + hospitalConfig.getIdentifier() + "/fhir/CodeSystem/data-source")
//                 .setCode("ehr")
//                 .setDisplay("Electronic Health Record");
//         // Add audit extension
//         Extension auditExtension = new Extension();
//         auditExtension.setUrl("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/audit-trail");
//         auditExtension.addExtension("created-date", new DateTimeType(new Date()));
//         auditExtension.addExtension("created-by", new StringType("EHR System"));
//         patient.addExtension(auditExtension);
//     }
//     private String generateMRN() {
//         return hospitalConfig.getIdentifier() + "-MRN-" + System.currentTimeMillis() +
//                String.format("%03d", new Random().nextInt(1000));
//     }
//     // Helper method to build search parameters
//     private List<Patient> searchByParameters(ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams) {
//         try {
//             return getDao().search(searchParams).getResources(0, 100)
//                 .stream()
//                 .map(resource -> (Patient) resource)
//                 .collect(java.util.stream.Collectors.toList());
//         } catch (Exception e) {
//             logger.error("Error in search: ", e);
//             return java.util.Collections.emptyList();
//         }
//     }
//     private ca.uhn.fhir.jpa.searchparam.SearchParameterMap buildSearchParams(
//             StringParam theFamily, StringParam theGiven, TokenParam theIdentifier,
//             DateParam theBirthDate, TokenParam theActive, NumberParam theCount) {
//         ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = new ca.uhn.fhir.jpa.searchparam.SearchParameterMap();
//         if (theFamily != null) {
//             searchParams.add(Patient.SP_FAMILY, theFamily);
//         }
//         if (theGiven != null) {
//             searchParams.add(Patient.SP_GIVEN, theGiven);
//         }
//         if (theIdentifier != null) {
//             searchParams.add(Patient.SP_IDENTIFIER, theIdentifier);
//         }
//         if (theBirthDate != null) {
//             searchParams.add(Patient.SP_BIRTHDATE, theBirthDate);
//         }
//         if (theActive != null) {
//             searchParams.add(Patient.SP_ACTIVE, theActive);
//         }
//         return searchParams;
//     }
// }
package com.svasamm.fhir.ehr.provider;

import java.util.Date;
import java.util.List;
import java.util.Random;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.svasamm.fhir.config.HospitalConfig;
import com.svasamm.fhir.ehr.service.PatientService;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.annotation.ConditionalUrlParam;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

public class PatientResourceProvider extends BaseJpaResourceProvider<Patient> {

    private static final Logger logger = LoggerFactory.getLogger(PatientResourceProvider.class);

    @Autowired
    private PatientService patientService;

    @Autowired
    private HospitalConfig hospitalConfig;

    public PatientResourceProvider(IFhirResourceDao<Patient> theDao) {
        super(theDao);
        logger.info("🚀 CUSTOM PatientResourceProvider CONSTRUCTOR CALLED with JPA DAO 🚀");
    }

    @Override
    public Class<Patient> getResourceType() {
        logger.info("CUSTOM PatientResourceProvider.getResourceType() called");
        return Patient.class;
    }

    @Create
    public MethodOutcome create(@ResourceParam Patient thePatient, RequestDetails theRequestDetails) {
        logger.error("Custom PatientResourceProvider.create() called");

        // JWT Token validation
        validateTokenPermissions(theRequestDetails, "Patient", "create");
        String userId = getUserFromToken(theRequestDetails);

        logger.error("Patient creation by user: {}", userId);
        logger.error("Patient name: {}", thePatient.getName().isEmpty() ? "No name" : thePatient.getName().get(0).getFamily());

        try {
            enrichPatientForEHR(thePatient);
            MethodOutcome result = getDao().create(thePatient, theRequestDetails);
            logger.error("Patient created with ID: {} ", result.getId());
            return result;

        } catch (Exception e) {
            logger.error("Error in custom create patient provider: ", e);
            throw e;
        }
    }

    @Update
    public MethodOutcome update(
            HttpServletRequest theRequest,
            @IdParam IdType theId,
            @ResourceParam Patient thePatient,
            @ConditionalUrlParam String theConditional,
            RequestDetails theRequestDetails) {

        logger.info("Custom PatientResourceProvider.update() called for ID: {}", theId);

        // JWT Token validation
        validateTokenPermissions(theRequestDetails, "Patient", "update");
        String userId = getUserFromToken(theRequestDetails);

        logger.info("Patient update by user: {}", userId);

        try {
            enrichPatientForEHR(thePatient);
            MethodOutcome result = super.update(theRequest, thePatient, theId, theConditional, theRequestDetails);

            if (patientService != null) {
                logger.info("Patient updated successfully with ID: {}", result.getId());
            }

            return result;

        } catch (Exception e) {
            logger.error("Error in CUSTOM update: ", e);
            throw e;
        }
    }

    @Read
    public Patient read(HttpServletRequest theRequest, @IdParam IdType theId, RequestDetails theRequestDetails) {
        logger.info("CUSTOM PatientResourceProvider.read() called for ID: {}", theId);

        // JWT Token validation
        validateTokenPermissions(theRequestDetails, "Patient", "read");
        String userId = getUserFromToken(theRequestDetails);

        logger.info("Patient read by user: {}", userId);

        try {
            Patient patient = super.read(theRequest, theId, theRequestDetails);

            if (patientService != null) {
                logger.debug("Patient read successfully: {}", theId);
            }

            return patient;

        } catch (Exception e) {
            logger.error("Error reading patient {}: ", theId, e);
            throw new ResourceNotFoundException(theId);
        }
    }

    @Search
    public List<Patient> search(
            @OptionalParam(name = Patient.SP_FAMILY) StringParam theFamily,
            @OptionalParam(name = Patient.SP_GIVEN) StringParam theGiven,
            @OptionalParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier,
            @OptionalParam(name = Patient.SP_BIRTHDATE) DateParam theBirthDate,
            @OptionalParam(name = Patient.SP_ACTIVE) TokenParam theActive,
            @OptionalParam(name = "_count") NumberParam theCount,
            RequestDetails theRequestDetails) {

        // JWT Token validation
        validateTokenPermissions(theRequestDetails, "Patient", "search");
        String userId = getUserFromToken(theRequestDetails);

        logger.info("Patient search by user: {}", userId);

        if (patientService != null) {
            return patientService.searchPatients(theFamily, theGiven, theIdentifier,
                    theBirthDate, theActive, theCount);
        }

        return searchByParameters(buildSearchParams(theFamily, theGiven, theIdentifier, theBirthDate, theActive, theCount));
    }

    @Operation(name = "$patient-summary", idempotent = true)
    public Bundle patientSummary(@IdParam IdType thePatientId, RequestDetails theRequestDetails) {

        // JWT Token validation
        validateTokenPermissions(theRequestDetails, "Patient", "summary");
        String userId = getUserFromToken(theRequestDetails);

        logger.info("Patient summary requested by user: {}", userId);

        if (patientService != null) {
            return patientService.generatePatientSummary(thePatientId.getIdPart());
        }
        throw new UnsupportedOperationException("Patient service not available");
    }

    // Helper methods for JWT token handling
    private void validateTokenPermissions(RequestDetails requestDetails, String resource, String operation) {
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) requestDetails.getAttribute("userRoles");

        if (roles == null || roles.isEmpty()) {
            throw new ForbiddenOperationException("No user roles found in token");
        }

        // Check permissions based on operation
        switch (operation) {
            case "read":
            case "search":
            case "summary":
                if (!hasReadPermission(roles)) {
                    throw new ForbiddenOperationException("Insufficient permissions for " + operation);
                }
                break;
            case "create":
            case "update":
                if (!hasWritePermission(roles)) {
                    throw new ForbiddenOperationException("Insufficient permissions for " + operation);
                }
                break;
            default:
                throw new ForbiddenOperationException("Unknown operation: " + operation);
        }

        logger.info("User with roles {} authorized for {} operation on {}", roles, operation, resource);
    }

    private String getUserFromToken(RequestDetails requestDetails) {
        String userId = (String) requestDetails.getAttribute("userId");
        return userId != null ? userId : "unknown";
    }

    private boolean hasReadPermission(List<String> roles) {
        return roles.contains("USER") || roles.contains("DOCTOR") || roles.contains("NURSE") || roles.contains("ADMIN");
    }

    private boolean hasWritePermission(List<String> roles) {
        return roles.contains("DOCTOR") || roles.contains("ADMIN");
    }

    // Rest of your existing methods remain the same...
    private void enrichPatientForEHR(Patient patient) {
        boolean hasMRN = patient.getIdentifier().stream()
                .anyMatch(id -> "MR".equals(id.getType().getCodingFirstRep().getCode()));

        if (!hasMRN) {
            Identifier mrn = new Identifier();
            mrn.setSystem("urn:mrn:" + hospitalConfig.getIdentifier());
            mrn.setValue(generateMRN());
            mrn.getType().addCoding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                    .setCode("MR")
                    .setDisplay("Medical Record Number");
            mrn.setUse(Identifier.IdentifierUse.USUAL);

            patient.addIdentifier(mrn);
        }

        if (patient.getManagingOrganization() == null) {
            patient.setManagingOrganization(
                    new Reference("Organization/" + hospitalConfig.getIdentifier())
                            .setDisplay(hospitalConfig.getName())
            );
        }

        if (patient.getMeta() == null) {
            patient.setMeta(new Meta());
        }
        patient.getMeta()
                .addProfile("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/ehr-patient")
                .addTag()
                .setSystem("http://" + hospitalConfig.getIdentifier() + "/fhir/CodeSystem/data-source")
                .setCode("ehr")
                .setDisplay("Electronic Health Record");

        Extension auditExtension = new Extension();
        auditExtension.setUrl("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/audit-trail");
        auditExtension.addExtension("created-date", new DateTimeType(new Date()));
        auditExtension.addExtension("created-by", new StringType("EHR System"));
        patient.addExtension(auditExtension);
    }

    private String generateMRN() {
        return hospitalConfig.getIdentifier() + "-MRN-" + System.currentTimeMillis()
                + String.format("%03d", new Random().nextInt(1000));
    }

    private List<Patient> searchByParameters(ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams) {
        try {
            return getDao().search(searchParams).getResources(0, 100)
                    .stream()
                    .map(resource -> (Patient) resource)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            logger.error("Error in search: ", e);
            return java.util.Collections.emptyList();
        }
    }

    private ca.uhn.fhir.jpa.searchparam.SearchParameterMap buildSearchParams(
            StringParam theFamily, StringParam theGiven, TokenParam theIdentifier,
            DateParam theBirthDate, TokenParam theActive, NumberParam theCount) {

        ca.uhn.fhir.jpa.searchparam.SearchParameterMap searchParams = new ca.uhn.fhir.jpa.searchparam.SearchParameterMap();

        if (theFamily != null) {
            searchParams.add(Patient.SP_FAMILY, theFamily);
        }
        if (theGiven != null) {
            searchParams.add(Patient.SP_GIVEN, theGiven);
        }
        if (theIdentifier != null) {
            searchParams.add(Patient.SP_IDENTIFIER, theIdentifier);
        }
        if (theBirthDate != null) {
            searchParams.add(Patient.SP_BIRTHDATE, theBirthDate);
        }
        if (theActive != null) {
            searchParams.add(Patient.SP_ACTIVE, theActive);
        }

        return searchParams;
    }
}
