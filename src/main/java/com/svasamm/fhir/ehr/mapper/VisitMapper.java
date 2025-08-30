package com.svasamm.fhir.ehr.mapper;

import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.springframework.stereotype.Component;

import com.svasamm.fhir.ehr.dto.visits.VisitDto;
import com.svasamm.fhir.ehr.dto.visits.VisitDto.VisitAdmitSource;
import com.svasamm.fhir.ehr.dto.visits.VisitDto.VisitClass;
import com.svasamm.fhir.ehr.dto.visits.VisitDto.VisitDischargeDisposition;
import com.svasamm.fhir.ehr.dto.visits.VisitDto.VisitHospitalization;
import com.svasamm.fhir.ehr.dto.visits.VisitDto.VisitLocation;
import com.svasamm.fhir.ehr.dto.visits.VisitDto.VisitLocationReference;
import com.svasamm.fhir.ehr.dto.visits.VisitDto.VisitPeriod;
import com.svasamm.fhir.ehr.dto.visits.VisitDto.VisitReasonCode;
import com.svasamm.fhir.ehr.dto.visits.VisitDto.VisitServiceProvider;
import com.svasamm.fhir.ehr.dto.visits.VisitDto.VisitSubject;
import com.svasamm.fhir.ehr.dto.visits.VisitDto.VisitType;

@Component
public class VisitMapper {

    public VisitDto mapToDTO(Encounter fhirEncounter) {
        VisitDto dto = new VisitDto();

        if (fhirEncounter.hasId()) {
            dto.setId(fhirEncounter.getIdElement().getIdPart());
        }

        // Map status
        if (fhirEncounter.hasStatus()) {
            dto.setStatus(fhirEncounter.getStatus().toCode());
        }

        // Map class
        dto.setVisitClass(mapClass(fhirEncounter));

        // Map type
        dto.setType(mapType(fhirEncounter));

        // Map subject
        dto.setSubject(mapSubject(fhirEncounter));

        // Map period
        dto.setPeriod(mapPeriod(fhirEncounter));

        // Map reason codes
        dto.setReasonCode(mapReasonCode(fhirEncounter));

        // Map service provider
        dto.setServiceProvider(mapServiceProvider(fhirEncounter));

        // Map location
        dto.setLocation(mapLocation(fhirEncounter));

        // Map hospitalization
        dto.setHospitalization(mapHospitalization(fhirEncounter));

        return dto;
    }

    private VisitClass mapClass(Encounter fhirEncounter) {
        if (!fhirEncounter.hasClass_()) {
            return null;
        }

        VisitClass visitClass = new VisitClass();
        Coding classCoding = fhirEncounter.getClass_();
        visitClass.setSystem(classCoding.getSystem());
        visitClass.setCode(classCoding.getCode());
        visitClass.setDisplay(classCoding.getDisplay());

        return visitClass;
    }

    private List<VisitType> mapType(Encounter fhirEncounter) {
        if (!fhirEncounter.hasType()) {
            return null;
        }

        return fhirEncounter.getType().stream()
                .flatMap(typeCC -> typeCC.getCoding().stream())
                .map(coding -> {
                    VisitType visitType = new VisitType();
                    visitType.setSystem(coding.getSystem());
                    visitType.setCode(coding.getCode());
                    visitType.setDisplay(coding.getDisplay());
                    return visitType;
                })
                .collect(Collectors.toList());
    }

    private VisitSubject mapSubject(Encounter fhirEncounter) {
        if (!fhirEncounter.hasSubject()) {
            return null;
        }

        VisitSubject subject = new VisitSubject();
        subject.setReference(fhirEncounter.getSubject().getReference());
        subject.setDisplay(fhirEncounter.getSubject().getDisplay());

        return subject;
    }

    private VisitPeriod mapPeriod(Encounter fhirEncounter) {
        if (!fhirEncounter.hasPeriod()) {
            return null;
        }

        VisitPeriod period = new VisitPeriod();
        
        if (fhirEncounter.getPeriod().hasStart()) {
            period.setStart(fhirEncounter.getPeriod().getStartElement().getValueAsString());
        }
        
        if (fhirEncounter.getPeriod().hasEnd()) {
            period.setEnd(fhirEncounter.getPeriod().getEndElement().getValueAsString());
        }

        return period;
    }

    private List<VisitReasonCode> mapReasonCode(Encounter fhirEncounter) {
        if (!fhirEncounter.hasReasonCode()) {
            return null;
        }

        return fhirEncounter.getReasonCode().stream()
                .flatMap(reasonCC -> reasonCC.getCoding().stream())
                .map(coding -> {
                    VisitReasonCode reasonCode = new VisitReasonCode();
                    reasonCode.setSystem(coding.getSystem());
                    reasonCode.setCode(coding.getCode());
                    reasonCode.setDisplay(coding.getDisplay());
                    return reasonCode;
                })
                .collect(Collectors.toList());
    }

    private VisitServiceProvider mapServiceProvider(Encounter fhirEncounter) {
        if (!fhirEncounter.hasServiceProvider()) {
            return null;
        }

        VisitServiceProvider serviceProvider = new VisitServiceProvider();
        serviceProvider.setReference(fhirEncounter.getServiceProvider().getReference());
        serviceProvider.setDisplay(fhirEncounter.getServiceProvider().getDisplay());

        return serviceProvider;
    }

    private List<VisitLocation> mapLocation(Encounter fhirEncounter) {
        if (!fhirEncounter.hasLocation()) {
            return null;
        }

        return fhirEncounter.getLocation().stream()
                .map(this::mapSingleLocation)
                .collect(Collectors.toList());
    }

    private VisitLocation mapSingleLocation(Encounter.EncounterLocationComponent fhirLocation) {
        VisitLocation location = new VisitLocation();

        // Map location reference
        if (fhirLocation.hasLocation()) {
            VisitLocationReference locationRef = new VisitLocationReference();
            locationRef.setReference(fhirLocation.getLocation().getReference());
            locationRef.setDisplay(fhirLocation.getLocation().getDisplay());
            location.setLocation(locationRef);
        }

        // Map status
        if (fhirLocation.hasStatus()) {
            location.setStatus(fhirLocation.getStatus().toCode());
        }

        // Map period
        if (fhirLocation.hasPeriod()) {
            VisitPeriod period = new VisitPeriod();
            if (fhirLocation.getPeriod().hasStart()) {
                period.setStart(fhirLocation.getPeriod().getStartElement().getValueAsString());
            }
            if (fhirLocation.getPeriod().hasEnd()) {
                period.setEnd(fhirLocation.getPeriod().getEndElement().getValueAsString());
            }
            location.setPeriod(period);
        }

        return location;
    }

    private VisitHospitalization mapHospitalization(Encounter fhirEncounter) {
        if (!fhirEncounter.hasHospitalization()) {
            return null;
        }

        VisitHospitalization hospitalization = new VisitHospitalization();
        Encounter.EncounterHospitalizationComponent fhirHosp = fhirEncounter.getHospitalization();

        // Map admit source
        if (fhirHosp.hasAdmitSource()) {
            VisitAdmitSource admitSource = new VisitAdmitSource();
            if (fhirHosp.getAdmitSource().hasCoding()) {
                Coding firstCoding = fhirHosp.getAdmitSource().getCodingFirstRep();
                admitSource.setSystem(firstCoding.getSystem());
                admitSource.setCode(firstCoding.getCode());
                admitSource.setDisplay(firstCoding.getDisplay());
            }
            hospitalization.setAdmitSource(admitSource);
        }

        // Map discharge disposition
        if (fhirHosp.hasDischargeDisposition()) {
            VisitDischargeDisposition dischargeDisposition = new VisitDischargeDisposition();
            if (fhirHosp.getDischargeDisposition().hasCoding()) {
                Coding firstCoding = fhirHosp.getDischargeDisposition().getCodingFirstRep();
                dischargeDisposition.setSystem(firstCoding.getSystem());
                dischargeDisposition.setCode(firstCoding.getCode());
                dischargeDisposition.setDisplay(firstCoding.getDisplay());
            }
            hospitalization.setDischargeDisposition(dischargeDisposition);
        }

        return hospitalization;
    }

    // Utility method to get visit type from code
    public String getVisitType(String classCode) {
        switch (classCode) {
            case "AMB": return "Ambulatory";
            case "EMER": return "Emergency";
            case "FLD": return "Field";
            case "HH": return "Home health";
            case "IMP": return "Inpatient encounter";
            case "ACUTE": return "Inpatient acute";
            case "NONAC": return "Inpatient non-acute";
            case "OBSENC": return "Observation encounter";
            case "PRENC": return "Pre-admission";
            case "SS": return "Short stay";
            case "VR": return "Virtual";
            default: return "Unknown visit type";
        }
    }

    // Method to get visit status display
    public String getStatusDisplay(String statusCode) {
        switch (statusCode) {
            case "planned": return "Planned";
            case "arrived": return "Arrived";
            case "triaged": return "Triaged";
            case "in-progress": return "In Progress";
            case "onleave": return "On Leave";
            case "finished": return "Finished";
            case "cancelled": return "Cancelled";
            case "entered-in-error": return "Entered in Error";
            case "unknown": return "Unknown";
            default: return statusCode;
        }
    }

    // Method to check if encounter is inpatient
    public boolean isInpatient(Encounter encounter) {
        if (!encounter.hasClass_()) return false;
        String classCode = encounter.getClass_().getCode();
        return "IMP".equals(classCode) || "ACUTE".equals(classCode) || "NONAC".equals(classCode);
    }

    // Method to check if encounter is outpatient
    public boolean isOutpatient(Encounter encounter) {
        if (!encounter.hasClass_()) return false;
        String classCode = encounter.getClass_().getCode();
        return "AMB".equals(classCode);
    }

    // Method to check if encounter is emergency
    public boolean isEmergency(Encounter encounter) {
        if (!encounter.hasClass_()) return false;
        String classCode = encounter.getClass_().getCode();
        return "EMER".equals(classCode);
    }

    // Method to get visit duration in hours
    public Long getVisitDurationHours(Encounter encounter) {
        if (!encounter.hasPeriod() || !encounter.getPeriod().hasStart()) {
            return null;
        }
        
        long startTime = encounter.getPeriod().getStart().getTime();
        long endTime = encounter.getPeriod().hasEnd() ? 
            encounter.getPeriod().getEnd().getTime() : 
            System.currentTimeMillis();
            
        return (endTime - startTime) / (1000 * 60 * 60); // Convert to hours
    }
}