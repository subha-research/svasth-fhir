package com.svasamm.fhir.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.svasamm.fhir.interceptor.AuditInterceptor;

@Configuration
@Component
@ConfigurationProperties(prefix = "modules")
@Profile("!test")
public class ModuleConfig {
    private EhrModule ehr = new EhrModule();
    private BiobankModule biobank = new BiobankModule();

    public static class EhrModule {
        private boolean enabled = true;
        private boolean patientManagement = true;
        private boolean clinicalWorkflows = true;
        private boolean scheduling = true;
        private boolean billing = false;

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isPatientManagement() { return patientManagement; }
        public void setPatientManagement(boolean patientManagement) { this.patientManagement = patientManagement; }

        public boolean isClinicalWorkflows() { return clinicalWorkflows; }
        public void setClinicalWorkflows(boolean clinicalWorkflows) { this.clinicalWorkflows = clinicalWorkflows; }

        public boolean isScheduling() { return scheduling; }
        public void setScheduling(boolean scheduling) { this.scheduling = scheduling; }

        public boolean isBilling() { return billing; }
        public void setBilling(boolean billing) { this.billing = billing; }
    }

    public static class BiobankModule {
        private boolean enabled = true;
        private boolean specimenTracking = true;
        private boolean storageManagement = true;
        private boolean consentManagement = true;
        private boolean researchWorkflows = true;

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isSpecimenTracking() { return specimenTracking; }
        public void setSpecimenTracking(boolean specimenTracking) { this.specimenTracking = specimenTracking; }

        public boolean isStorageManagement() { return storageManagement; }
        public void setStorageManagement(boolean storageManagement) { this.storageManagement = storageManagement; }

        public boolean isConsentManagement() { return consentManagement; }
        public void setConsentManagement(boolean consentManagement) { this.consentManagement = consentManagement; }

        public boolean isResearchWorkflows() { return researchWorkflows; }
        public void setResearchWorkflows(boolean researchWorkflows) { this.researchWorkflows = researchWorkflows; }
    }

    // Getters and setters
    public EhrModule getEhr() { return ehr; }
    public void setEhr(EhrModule ehr) { this.ehr = ehr; }

    public BiobankModule getBiobank() { return biobank; }
    public void setBiobank(BiobankModule biobank) { this.biobank = biobank; }
}