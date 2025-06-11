package com.svasamm.fhir.ehr.dto.patient;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PatientDto {

    @JsonProperty("resourceType")
    private String resourceType = "Patient";
    private String id;
    private boolean active;
    private PatientIdentifier identifier;
    private PatientName name;
    private String gender;
    @JsonProperty("birthDate")
    private String birthDate;
    private PatientTelecom telecom;
    private PatientContact contact;

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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public PatientIdentifier getIdentifier() {
        return identifier;
    }

    public void setIdentifier(PatientIdentifier identifier) {
        this.identifier = identifier;
    }

    public PatientName getName() {
        return name;
    }

    public void setName(PatientName name) {
        this.name = name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public PatientTelecom getTelecom() {
        return telecom;
    }

    public void setTelecom(PatientTelecom telecom) {
        this.telecom = telecom;
    }

    public PatientContact getContact() {
        return contact;
    }

    public void setContact(PatientContact contact) {
        this.contact = contact;
    }

    public static class PatientIdentifier {

        private String primary;
        @JsonProperty("mrnObject")
        private MrnObject mrnObject;

        public String getPrimary() {
            return primary;
        }

        public void setPrimary(String primary) {
            this.primary = primary;
        }

        public MrnObject getMrnObject() {
            return mrnObject;
        }

        public void setMrnObject(MrnObject mrnObject) {
            this.mrnObject = mrnObject;
        }
    }

    public static class MrnObject {

        private String system;
        private String value;

        public MrnObject() {
        }

        public MrnObject(String system, String value) {
            this.system = system;
            this.value = value;
        }

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class PatientName {

        private List<String> given;
        private String family;
        private String full;

        public List<String> getGiven() {
            return given;
        }

        public void setGiven(List<String> given) {
            this.given = given;
        }

        public String getFamily() {
            return family;
        }

        public void setFamily(String family) {
            this.family = family;
        }

        public String getFull() {
            return full;
        }

        public void setFull(String full) {
            this.full = full;
        }
    }

    public static class PatientTelecom {

        private String phone;
        private String email;

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class PatientContact {

        private String name;
        private String relationship;
        private ContactTelecom telecom;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRelationship() {
            return relationship;
        }

        public void setRelationship(String relationship) {
            this.relationship = relationship;
        }

        public ContactTelecom getTelecom() {
            return telecom;
        }

        public void setTelecom(ContactTelecom telecom) {
            this.telecom = telecom;
        }
    }

    public static class ContactTelecom {

        private String phone;

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }
}
