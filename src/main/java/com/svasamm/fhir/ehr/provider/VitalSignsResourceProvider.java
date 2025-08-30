package com.svasamm.fhir.ehr.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.svasamm.fhir.config.HospitalConfig;
import com.svasamm.fhir.ehr.dto.vitalsigns.VitalSignsDto;
import com.svasamm.fhir.ehr.mapper.VitalSignsMapper;
import com.svasamm.fhir.ehr.service.VitalService;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.ConditionalUrlParam;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Custom Vital Signs Resource Provider that extends HAPI's JPA provider
 * Vital Signs are implemented as Observations with vital-signs category
 */
public class VitalSignsResourceProvider extends BaseJpaResourceProvider<Observation> {

    private static final Logger logger = LoggerFactory.getLogger(VitalSignsResourceProvider.class);

    @Autowired
    private VitalService vitalSignsService;

    @Autowired
    private HospitalConfig hospitalConfig;

    @Autowired
    private VitalSignsMapper vitalSignsMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public VitalSignsResourceProvider(IFhirResourceDao<Observation> theDao) {
        super(theDao);
        logger.info("🚀 CUSTOM VitalSignsResourceProvider CONSTRUCTOR CALLED with JPA DAO 🚀");
    }

    @Override
    public Class<Observation> getResourceType() {
        logger.info("CUSTOM VitalSignsResourceProvider.getResourceType() called");
        return Observation.class;
    }

    @Create
    public MethodOutcome create(@ResourceParam Observation theVitalSign, RequestDetails theRequestDetails) {
        logger.info("Custom VitalSignsResourceProvider.create() called");
        logger.info("Vital Sign code: {}", 
            theVitalSign.getCode() != null && theVitalSign.getCode().getCodingFirstRep() != null ? 
            theVitalSign.getCode().getCodingFirstRep().getDisplay() : "Unknown");

        try {
            // Apply vital signs specific enrichment
            enrichVitalSignForEHR(theVitalSign);

            // Use DAO directly instead of super.create()
            MethodOutcome result = getDao().create(theVitalSign, theRequestDetails);

            logger.info("Vital Sign created with ID: {}", result.getId());
            return result;

        } catch (Exception e) {
            logger.error("Error in custom create vital sign provider: ", e);
            throw e;
        }
    }

    @Update
    public MethodOutcome update(
            HttpServletRequest theRequest,
            @IdParam IdType theId,
            @ResourceParam Observation theVitalSign,
            @ConditionalUrlParam String theConditional,
            RequestDetails theRequestDetails) {

        logger.info("Custom VitalSignsResourceProvider.update() called for ID: {}", theId);

        try {
            // Apply vital signs specific enrichment
            enrichVitalSignForEHR(theVitalSign);

            // Call parent JPA implementation - this handles optimistic locking automatically
            MethodOutcome result = super.update(theRequest, theVitalSign, theId, theConditional, theRequestDetails);

            // Custom post-processing
            if (vitalSignsService != null) {
                logger.info("Vital Sign updated successfully with ID: {}", result.getId());
            }

            return result;

        } catch (Exception e) {
            logger.error("Error in CUSTOM update: ", e);
            throw e;
        }
    }

    @Read
    public Observation read(HttpServletRequest theRequest, @IdParam IdType theId, RequestDetails theRequestDetails) {
        logger.info("CUSTOM VitalSignsResourceProvider.read() called for ID: {}", theId);

        try {
            // Use the parent JPA implementation for reading
            Observation vitalSign = super.read(theRequest, theId, theRequestDetails);

            // Verify it's actually a vital sign
            if (vitalSignsService != null && !vitalSignsService.isVitalSign(vitalSign)) {
                throw new ResourceNotFoundException("Resource is not a vital sign: " + theId);
            }

            return vitalSign;

        } catch (Exception e) {
            logger.error("Error reading vital sign {}: ", theId, e);
            throw new ResourceNotFoundException(theId);
        }
    }

    /**
     * Custom operation to get vital sign in mapped format
     * Usage: GET /Observation/{id}/$vital-mapped
     */
    @Operation(name = "$vital-mapped", idempotent = true)
    public void getVitalSignMapped(
            @IdParam IdType theId,
            HttpServletRequest theRequest,
            HttpServletResponse theResponse,
            RequestDetails theRequestDetails) {

        logger.info("Custom VitalSignsResourceProvider.getVitalSignMapped() called for ID: {}", theId);

        try {
            // Get the observation using DAO
            Observation observation = getDao().read(theId, theRequestDetails);

            if (observation == null) {
                sendErrorResponse(theResponse, HttpServletResponse.SC_NOT_FOUND, "Vital sign not found");
                return;
            }

            // Check if it's a vital sign
            if (!isVitalSign(observation)) {
                sendErrorResponse(theResponse, HttpServletResponse.SC_BAD_REQUEST, "Observation is not a vital sign");
                return;
            }

            // Map to vital signs format
            VitalSignsDto mappedVitalSign = vitalSignsMapper.mapToDTO(observation);
            String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mappedVitalSign);

            sendSuccessResponse(theResponse, jsonResponse);
            logger.info("Mapped vital sign response sent for ID: {}", theId);

        } catch (Exception e) {
            logger.error("Error getting mapped vital sign {}: ", theId, e);
            sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    /**
     * Custom operation to search vital signs and return them in mapped format
     * Usage: GET /Observation/$vital-search-mapped?patient=Patient/123&code=8867-4
     */
    @Operation(name = "$vital-search-mapped", idempotent = true)
    public void searchMappedVitalSigns(
            @OperationParam(name = "subject") ReferenceParam theSubject,
            @OperationParam(name = "patient") ReferenceParam thePatient,
            @OperationParam(name = "code") TokenParam theCode,
            @OperationParam(name = "date") DateRangeParam theDate,
            @OperationParam(name = "status") TokenParam theStatus,
            @OperationParam(name = "encounter") ReferenceParam theEncounter,
            @OperationParam(name = "_count") NumberParam theCount,
            HttpServletRequest theRequest,
            HttpServletResponse theResponse,
            RequestDetails theRequestDetails) {

        logger.info("Custom VitalSignsResourceProvider.searchMappedVitalSigns() called");

        try {
            if (vitalSignsService == null) {
                sendErrorResponse(theResponse, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Vital Signs service not available");
                return;
            }

            // Perform search using service
            List<Observation> vitalSigns = vitalSignsService.searchVitalSigns(
                    theSubject, thePatient, theCode, theDate, theStatus, theEncounter, theCount);

            // Build bundle response
            String jsonResponse = buildVitalSignsBundleResponse(vitalSigns, "searchset", null);
            
            sendSuccessResponse(theResponse, jsonResponse);
            logger.info("Mapped vital signs search response sent, {} vital signs found", vitalSigns.size());

        } catch (Exception e) {
            logger.error("Error in search mapped vital signs: ", e);
            sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    /**
     * Custom operation to get patient vital signs in mapped format
     * Usage: GET /Observation/$patient-vitals-mapped?patient=Patient/123&period=24
     */
    @Operation(name = "$patient-vitals-mapped", idempotent = true)
    public void getPatientVitalSignsMapped(
            @OperationParam(name = "patient", min = 1) ReferenceParam thePatient,
            @OperationParam(name = "code") TokenParam theCode,
            @OperationParam(name = "period") NumberParam thePeriodHours,
            @OperationParam(name = "start-date") DateParam theStartDate,
            @OperationParam(name = "end-date") DateParam theEndDate,
            @OperationParam(name = "_count") NumberParam theCount,
            HttpServletRequest theRequest,
            HttpServletResponse theResponse,
            RequestDetails theRequestDetails) {

        logger.info("Custom VitalSignsResourceProvider.getPatientVitalSignsMapped() called for patient: {}",
                thePatient != null ? thePatient.getValue() : "null");

        try {
            if (thePatient == null) {
                sendErrorResponse(theResponse, HttpServletResponse.SC_BAD_REQUEST, "Patient parameter is required");
                return;
            }

            if (vitalSignsService == null) {
                sendErrorResponse(theResponse, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Vital Signs service not available");
                return;
            }

            // Create date range
            DateRangeParam dateRange = buildDateRange(thePeriodHours, theStartDate, theEndDate);

            // Create subject parameter
            ReferenceParam subjectParam = new ReferenceParam(thePatient.getValue());

            // Perform search using service
            List<Observation> vitalSigns = vitalSignsService.searchVitalSigns(
                    subjectParam, thePatient, theCode, dateRange, null, null, theCount);

            // Build bundle response with patient info
            String jsonResponse = buildVitalSignsBundleResponse(vitalSigns, "searchset", thePatient.getValue());
            
            sendSuccessResponse(theResponse, jsonResponse);
            logger.info("Mapped patient vital signs response sent, {} vital signs found for patient {}",
                    vitalSigns.size(), thePatient.getValue());

        } catch (Exception e) {
            logger.error("Error in get patient vital signs mapped: ", e);
            sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    /**
     * Custom operation to get vital signs summary in mapped format
     * Usage: GET /Observation/{patientId}/$vital-summary-mapped?encounter=Encounter/123
     */
    @Operation(name = "$vital-summary-mapped", idempotent = true)
    public void vitalSignsSummaryMapped(
            @IdParam IdType thePatientId,
            @OperationParam(name = "encounter") ReferenceParam theEncounter,
            HttpServletRequest theRequest,
            HttpServletResponse theResponse,
            RequestDetails theRequestDetails) {

        logger.info("Custom VitalSignsResourceProvider.vitalSignsSummaryMapped() called for patient: {}", thePatientId);

        try {
            if (vitalSignsService == null) {
                sendErrorResponse(theResponse, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Vital Signs service not available");
                return;
            }

            // Get the latest vital signs for the patient
            ReferenceParam patientParam = new ReferenceParam("Patient/" + thePatientId.getIdPart());
            
            // Create date range for last 24 hours
            DateRangeParam dateRange = buildDateRange(new NumberParam(24), null, null);

            // Perform search using service
            List<Observation> vitalSigns = vitalSignsService.searchVitalSigns(
                    patientParam, patientParam, null, dateRange, null, theEncounter, new NumberParam(50));

            // Build collection bundle response
            String jsonResponse = buildVitalSignsBundleResponse(vitalSigns, "collection", thePatientId.getIdPart(), 
                    "patientId", thePatientId.getIdPart());
            
            sendSuccessResponse(theResponse, jsonResponse);
            logger.info("Vital signs summary response sent for patient: {}", thePatientId);

        } catch (Exception e) {
            logger.error("Error in vital signs summary: ", e);
            sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    /**
     * Custom operation to get latest vital signs in mapped format
     * Usage: GET /Observation/{patientId}/$latest-vitals-mapped?period=24
     */
    @Operation(name = "$latest-vitals-mapped", idempotent = true)
    public void latestVitalSignsMapped(
            @IdParam IdType thePatientId,
            @OperationParam(name = "period") NumberParam thePeriodHours,
            @OperationParam(name = "_count") NumberParam theCount,
            HttpServletRequest theRequest,
            HttpServletResponse theResponse,
            RequestDetails theRequestDetails) {

        logger.info("Custom VitalSignsResourceProvider.latestVitalSignsMapped() called for patient: {}", thePatientId);

        try {
            if (vitalSignsService == null) {
                sendErrorResponse(theResponse, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Vital Signs service not available");
                return;
            }

            int periodHours = thePeriodHours != null ? thePeriodHours.getValue().intValue() : 24;
            
            // Create date range for the specified period
            DateRangeParam dateRange = buildDateRange(new NumberParam(periodHours), null, null);
            ReferenceParam patientParam = new ReferenceParam("Patient/" + thePatientId.getIdPart());

            // Perform search using service
            List<Observation> vitalSigns = vitalSignsService.searchVitalSigns(
                    patientParam, patientParam, null, dateRange, null, null, theCount);

            // Build latest vitals response
            String jsonResponse = buildLatestVitalSignsResponse(vitalSigns, thePatientId.getIdPart(), periodHours);
            
            sendSuccessResponse(theResponse, jsonResponse);
            logger.info("Latest vital signs response sent for patient: {} (period: {} hours)", thePatientId, periodHours);

        } catch (Exception e) {
            logger.error("Error in latest vital signs: ", e);
            sendErrorResponse(theResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    // ============================================================================
    // EXISTING OPERATIONS (refactored to use common methods where applicable)
    // ============================================================================

    @Operation(name = "$vital-signs-panel", idempotent = true)
    public Bundle vitalSignsPanel(@IdParam IdType thePatientId) {
        logger.info("Custom VitalSignsResourceProvider.vitalSignsPanel() called for patient: {}", thePatientId);
        
        try {
            if (vitalSignsService != null) {
                return vitalSignsService.generateVitalSignsPanel(thePatientId.getIdPart());
            }
            throw new UnsupportedOperationException("Vital Signs service not available");
        } catch (Exception e) {
            logger.error("Error in vital signs panel for patient {}: ", thePatientId, e);
            // Return empty bundle instead of throwing exception
            Bundle errorBundle = new Bundle();
            errorBundle.setType(Bundle.BundleType.COLLECTION);
            errorBundle.setTotal(0);
            return errorBundle;
        }
    }

    @Operation(name = "$vital-signs-trend", idempotent = true)
    public Bundle vitalSignsTrend(
            @IdParam IdType thePatientId,
            @OperationParam(name = "code") TokenParam code,
            @OperationParam(name = "start-date") DateParam startDate,
            @OperationParam(name = "end-date") DateParam endDate) {

        logger.info("Custom VitalSignsResourceProvider.vitalSignsTrend() called for patient: {}", thePatientId);
        
        try {
            if (vitalSignsService != null) {
                return vitalSignsService.getVitalSignsTrend(
                        thePatientId.getIdPart(),
                        code,
                        startDate != null ? startDate.getValue() : null,
                        endDate != null ? endDate.getValue() : null);
            }
            throw new UnsupportedOperationException("Vital Signs service not available");
        } catch (Exception e) {
            logger.error("Error in vital signs trend for patient {}: ", thePatientId, e);
            // Return empty bundle instead of throwing exception
            Bundle errorBundle = new Bundle();
            errorBundle.setType(Bundle.BundleType.COLLECTION);
            errorBundle.setTotal(0);
            return errorBundle;
        }
    }

    @Operation(name = "$latest-vital-signs", idempotent = true)
    public Bundle latestVitalSigns(
            @IdParam IdType thePatientId,
            @OperationParam(name = "period") NumberParam periodHours) {

        logger.info("Custom VitalSignsResourceProvider.latestVitalSigns() called for patient: {}", thePatientId);
        
        try {
            if (vitalSignsService != null) {
                return vitalSignsService.getLatestVitalSigns(
                        thePatientId.getIdPart(),
                        periodHours != null ? periodHours.getValue().intValue() : 24);
            }
            throw new UnsupportedOperationException("Vital Signs service not available");
        } catch (Exception e) {
            logger.error("Error in latest vital signs for patient {}: ", thePatientId, e);
            // Return empty bundle instead of throwing exception
            Bundle errorBundle = new Bundle();
            errorBundle.setType(Bundle.BundleType.COLLECTION);
            errorBundle.setTotal(0);
            return errorBundle;
        }
    }

    @Operation(name = "$vital-signs-summary", idempotent = true)
    public Bundle vitalSignsSummary(
            @IdParam IdType thePatientId,
            @OperationParam(name = "encounter") ReferenceParam encounter) {

        logger.info("Custom VitalSignsResourceProvider.vitalSignsSummary() called for patient: {}", thePatientId);
        
        try {
            if (vitalSignsService != null) {
                return vitalSignsService.getVitalSignsSummary(
                        thePatientId.getIdPart(),
                        encounter != null ? encounter.getValue() : null);
            }
            throw new UnsupportedOperationException("Vital Signs service not available");
        } catch (Exception e) {
            logger.error("Error in vital signs summary for patient {}: ", thePatientId, e);
            // Return empty bundle instead of throwing exception
            Bundle errorBundle = new Bundle();
            errorBundle.setType(Bundle.BundleType.COLLECTION);
            errorBundle.setTotal(0);
            return errorBundle;
        }
    }

    @Search
    public List<Observation> search(
            @OptionalParam(name = Observation.SP_SUBJECT) ReferenceParam theSubject,
            @OptionalParam(name = Observation.SP_PATIENT) ReferenceParam thePatient,
            @OptionalParam(name = Observation.SP_CODE) TokenParam theCode,
            @OptionalParam(name = Observation.SP_DATE) DateRangeParam theDate,
            @OptionalParam(name = Observation.SP_STATUS) TokenParam theStatus,
            @OptionalParam(name = Observation.SP_ENCOUNTER) ReferenceParam theEncounter,
            @OptionalParam(name = "_count") NumberParam theCount) {

        logger.info("CUSTOM VitalSignsResourceProvider.search() called");

        // Always filter for vital signs category
        if (vitalSignsService != null) {
            return vitalSignsService.searchVitalSigns(theSubject, thePatient, theCode,
                    theDate, theStatus, theEncounter, theCount);
        }

        // Otherwise, fall back to default JPA search with vital signs filter
        return searchByParameters(
                buildSearchParams(theSubject, thePatient, theCode, theDate, theStatus, theEncounter, theCount));
    }

    // ============================================================================
    // COMMON RESPONSE METHODS
    // ============================================================================

    /**
     * Common method to set response headers and send successful JSON response
     */
    private void sendSuccessResponse(HttpServletResponse response, String jsonContent) {
        try {
            setCommonResponseHeaders(response);
            response.setStatus(HttpServletResponse.SC_OK);
            
            response.getWriter().write(jsonContent);
            response.getWriter().flush();
            response.getWriter().close();
        } catch (IOException e) {
            logger.error("Error writing success response", e);
        }
    }

    /**
     * Common method to set response headers and send error response
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String errorMessage) {
        try {
            if (!response.isCommitted()) {
                setCommonResponseHeaders(response);
                response.setStatus(statusCode);
                
                String errorJson = String.format("{\"error\":\"%s\"}", errorMessage);
                response.getWriter().write(errorJson);
                response.getWriter().flush();
                response.getWriter().close();
            }
        } catch (IOException e) {
            logger.error("Error writing error response", e);
        }
    }

    /**
     * Common method to set standard response headers
     */
    private void setCommonResponseHeaders(HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
    }

    /**
     * Common method to build bundle response from vital signs list (mapped format)
     */
    private String buildVitalSignsBundleResponse(List<Observation> vitalSigns, String bundleType, String patientValue) {
        return buildVitalSignsBundleResponse(vitalSigns, bundleType, patientValue, null, null);
    }

    /**
     * Common method to build bundle response with additional metadata (mapped format)
     */
    private String buildVitalSignsBundleResponse(List<Observation> vitalSigns, String bundleType, String patientValue, 
            String additionalKey, String additionalValue) {
        try {
            StringBuilder jsonResponse = new StringBuilder();
            jsonResponse.append("{\"resourceType\":\"Bundle\",\"type\":\"").append(bundleType).append("\",\"total\":")
                    .append(vitalSigns.size());

            // Add patient info if provided
            if (patientValue != null && !patientValue.isEmpty()) {
                jsonResponse.append(",\"patient\":\"").append(patientValue).append("\"");
            }

            // Add additional metadata if provided
            if (additionalKey != null && additionalValue != null) {
                jsonResponse.append(",\"").append(additionalKey).append("\":\"").append(additionalValue).append("\"");
            }

            jsonResponse.append(",\"entry\":[");

            // Add all vital signs (mapped format)
            for (int i = 0; i < vitalSigns.size(); i++) {
                if (i > 0) {
                    jsonResponse.append(",");
                }
                VitalSignsDto mappedVitalSign = vitalSignsMapper.mapToDTO(vitalSigns.get(i));
                String vitalSignJson = objectMapper.writeValueAsString(mappedVitalSign);
                jsonResponse.append("{\"resource\":")
                        .append(vitalSignJson)
                        .append("}");
            }
            jsonResponse.append("]}");

            return jsonResponse.toString();
        } catch (Exception e) {
            logger.error("Error building vital signs bundle response", e);
            return "{\"error\":\"Error building response\"}";
        }
    }

    /**
     * Specialized method to build latest vital signs response
     */
    private String buildLatestVitalSignsResponse(List<Observation> vitalSigns, String patientId, int periodHours) {
        try {
            StringBuilder jsonResponse = new StringBuilder();
            jsonResponse.append("{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":")
                    .append(vitalSigns.size())
                    .append(",\"patient\":\"Patient/").append(patientId).append("\"")
                    .append(",\"period\":").append(periodHours)
                    .append(",\"category\":\"latest-vital-signs\"")
                    .append(",\"entry\":[");

            for (int i = 0; i < vitalSigns.size(); i++) {
                if (i > 0) {
                    jsonResponse.append(",");
                }
                VitalSignsDto mappedVitalSign = vitalSignsMapper.mapToDTO(vitalSigns.get(i));
                String vitalSignJson = objectMapper.writeValueAsString(mappedVitalSign);
                jsonResponse.append("{\"resource\":")
                        .append(vitalSignJson)
                        .append("}");
            }
            jsonResponse.append("]}");

            return jsonResponse.toString();
        } catch (Exception e) {
            logger.error("Error building latest vital signs response", e);
            return "{\"error\":\"Error building response\"}";
        }
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Helper method to check if observation is vital sign
     */
    private boolean isVitalSign(Observation observation) {
        if (observation == null || observation.getCategory() == null) {
            return false;
        }
        
        return observation.getCategory().stream()
                .anyMatch(category -> category.getCoding().stream()
                    .anyMatch(coding -> "vital-signs".equals(coding.getCode())));
    }

    /**
     * Helper method to build date range
     */
    private DateRangeParam buildDateRange(NumberParam periodHours, DateParam startDate, DateParam endDate) {
        DateRangeParam dateRange = null;
        
        if (startDate != null || endDate != null || periodHours != null) {
            dateRange = new DateRangeParam();
            
            if (periodHours != null) {
                // Calculate period range
                int hours = periodHours.getValue().intValue();
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.HOUR_OF_DAY, -hours);
                
                DateParam lowerBound = new DateParam();
                lowerBound.setPrefix(ParamPrefixEnum.GREATERTHAN_OR_EQUALS);
                lowerBound.setValue(cal.getTime());
                dateRange.setLowerBound(lowerBound);
            } else {
                if (startDate != null) {
                    DateParam lowerBound = new DateParam();
                    lowerBound.setPrefix(ParamPrefixEnum.GREATERTHAN_OR_EQUALS);
                    lowerBound.setValue(startDate.getValue());
                    dateRange.setLowerBound(lowerBound);
                }
                if (endDate != null) {
                    DateParam upperBound = new DateParam();
                    upperBound.setPrefix(ParamPrefixEnum.LESSTHAN_OR_EQUALS);
                    upperBound.setValue(endDate.getValue());
                    dateRange.setUpperBound(upperBound);
                }
            }
        }
        
        return dateRange;
    }

    private void enrichVitalSignForEHR(Observation vitalSign) {
        if (vitalSign == null) {
            return;
        }

        // Ensure vital signs category
        boolean hasVitalSignsCategory = vitalSign.getCategory().stream()
                .anyMatch(category -> category.getCoding().stream()
                        .anyMatch(coding -> "vital-signs".equals(coding.getCode())));

        if (!hasVitalSignsCategory) {
            CodeableConcept vitalSignsCategory = new CodeableConcept();
            vitalSignsCategory.addCoding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
                    .setCode("vital-signs")
                    .setDisplay("Vital Signs");
            vitalSign.addCategory(vitalSignsCategory);
        }

        // Set default status if not present
        if (vitalSign.getStatus() == null) {
            vitalSign.setStatus(Observation.ObservationStatus.FINAL);
        }

        // Add EHR metadata
        if (vitalSign.getMeta() == null) {
            vitalSign.setMeta(new Meta());
        }

        if (hospitalConfig != null) {
            vitalSign.getMeta()
                    .addProfile("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/ehr-vital-signs")
                    .addTag()
                    .setSystem("http://" + hospitalConfig.getIdentifier() + "/fhir/CodeSystem/data-source")
                    .setCode("ehr")
                    .setDisplay("Electronic Health Record");

            // Add audit extension
            Extension auditExtension = new Extension();
            auditExtension.setUrl("http://" + hospitalConfig.getIdentifier() + "/fhir/StructureDefinition/audit-trail");
            auditExtension.addExtension("created-date", new DateTimeType(new Date()));
            auditExtension.addExtension("created-by", new StringType("EHR System"));
            vitalSign.addExtension(auditExtension);
        }

        // Set effective date if not present
        if (vitalSign.getEffective() == null) {
            vitalSign.setEffective(new DateTimeType(new Date()));
        }

        // Add issued timestamp
        if (vitalSign.getIssued() == null) {
            vitalSign.setIssued(new Date());
        }
    }

    // Helper method to build search parameters
    private List<Observation> searchByParameters(SearchParameterMap searchParams) {
        try {
            return getDao().search(searchParams).getResources(0, 100)
                    .stream()
                    .map(resource -> (Observation) resource)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            logger.error("Error in search: ", e);
            return new ArrayList<>();
        }
    }

    private SearchParameterMap buildSearchParams(
            ReferenceParam theSubject, ReferenceParam thePatient, TokenParam theCode,
            DateRangeParam theDate, TokenParam theStatus, ReferenceParam theEncounter, NumberParam theCount) {

        SearchParameterMap searchParams = new SearchParameterMap();

        // Always filter for vital signs
        searchParams.add(Observation.SP_CATEGORY, new TokenParam("vital-signs"));

        if (theSubject != null) {
            searchParams.add(Observation.SP_SUBJECT, theSubject);
        }
        if (thePatient != null) {
            searchParams.add(Observation.SP_PATIENT, thePatient);
        }
        if (theCode != null) {
            searchParams.add(Observation.SP_CODE, theCode);
        }
        if (theDate != null) {
            searchParams.add(Observation.SP_DATE, theDate);
        }
        if (theStatus != null) {
            searchParams.add(Observation.SP_STATUS, theStatus);
        }
        if (theEncounter != null) {
            searchParams.add(Observation.SP_ENCOUNTER, theEncounter);
        }

        return searchParams;
    }
}