package com.svasamm.fhir.ehr.dto.visits;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VisitDto {

    @JsonProperty("resourceType")
    private String resourceType = "Encounter";
    private String id;
    private String status;
    private VisitClass visitClass;
    private List<VisitType> type;
    private VisitSubject subject;
    private VisitPeriod period;
    private List<VisitReasonCode> reasonCode;
    private VisitServiceProvider serviceProvider;
    private List<VisitLocation> location;
    private VisitHospitalization hospitalization;

    // Getters and Setters
    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public VisitClass getVisitClass() {
        return visitClass;
    }

    public void setVisitClass(VisitClass visitClass) {
        this.visitClass = visitClass;
    }

    public List<VisitType> getType() {
        return type;
    }

    public void setType(List<VisitType> type) {
        this.type = type;
    }

    public VisitSubject getSubject() {
        return subject;
    }

    public void setSubject(VisitSubject subject) {
        this.subject = subject;
    }

    public VisitPeriod getPeriod() {
        return period;
    }

    public void setPeriod(VisitPeriod period) {
        this.period = period;
    }

    public List<VisitReasonCode> getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(List<VisitReasonCode> reasonCode) {
        this.reasonCode = reasonCode;
    }

    public VisitServiceProvider getServiceProvider() {
        return serviceProvider;
    }

    public void setServiceProvider(VisitServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    public List<VisitLocation> getLocation() {
        return location;
    }

    public void setLocation(List<VisitLocation> location) {
        this.location = location;
    }

    public VisitHospitalization getHospitalization() {
        return hospitalization;
    }

    public void setHospitalization(VisitHospitalization hospitalization) {
        this.hospitalization = hospitalization;
    }

    // Inner classes
    public static class VisitClass {
        private String system;
        private String code;
        private String display;

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getDisplay() {
            return display;
        }

        public void setDisplay(String display) {
            this.display = display;
        }
    }

    public static class VisitType {
        private String system;
        private String code;
        private String display;
        private String text;

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getDisplay() {
            return display;
        }

        public void setDisplay(String display) {
            this.display = display;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public static class VisitSubject {
        private String reference;
        private String display;

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        public String getDisplay() {
            return display;
        }

        public void setDisplay(String display) {
            this.display = display;
        }
    }

    public static class VisitPeriod {
        @JsonProperty("start")
        private String start;
        @JsonProperty("end")
        private String end;

        public String getStart() {
            return start;
        }

        public void setStart(String start) {
            this.start = start;
        }

        public String getEnd() {
            return end;
        }

        public void setEnd(String end) {
            this.end = end;
        }
    }

    public static class VisitReasonCode {
        private String system;
        private String code;
        private String display;
        private String text;

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getDisplay() {
            return display;
        }

        public void setDisplay(String display) {
            this.display = display;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public static class VisitServiceProvider {
        private String reference;
        private String display;

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        public String getDisplay() {
            return display;
        }

        public void setDisplay(String display) {
            this.display = display;
        }
    }

    public static class VisitLocation {
        private VisitLocationReference location;
        private String status;
        private VisitPeriod period;

        public VisitLocationReference getLocation() {
            return location;
        }

        public void setLocation(VisitLocationReference location) {
            this.location = location;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public VisitPeriod getPeriod() {
            return period;
        }

        public void setPeriod(VisitPeriod period) {
            this.period = period;
        }
    }

    public static class VisitLocationReference {
        private String reference;
        private String display;

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        public String getDisplay() {
            return display;
        }

        public void setDisplay(String display) {
            this.display = display;
        }
    }

    public static class VisitHospitalization {
        private VisitAdmitSource admitSource;
        private VisitDischargeDisposition dischargeDisposition;

        public VisitAdmitSource getAdmitSource() {
            return admitSource;
        }

        public void setAdmitSource(VisitAdmitSource admitSource) {
            this.admitSource = admitSource;
        }

        public VisitDischargeDisposition getDischargeDisposition() {
            return dischargeDisposition;
        }

        public void setDischargeDisposition(VisitDischargeDisposition dischargeDisposition) {
            this.dischargeDisposition = dischargeDisposition;
        }
    }

    public static class VisitAdmitSource {
        private String system;
        private String code;
        private String display;

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getDisplay() {
            return display;
        }

        public void setDisplay(String display) {
            this.display = display;
        }
    }

    public static class VisitDischargeDisposition {
        private String system;
        private String code;
        private String display;

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getDisplay() {
            return display;
        }

        public void setDisplay(String display) {
            this.display = display;
        }
    }
}