package com.svasamm.fhir.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hospital")
public class HospitalConfig {
    private String name = "General Hospital";
    private String identifier = "GH-001";
    private String type = "both"; // "ehr", "biobank", "both"
    private String timezone = "UTC";
    private String locale = "en_US";
    private String address = "";
    private String phone = "";
    private String email = "";

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}