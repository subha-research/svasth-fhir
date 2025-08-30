package com.svasamm.fhir.ehr.dto.vitalsigns;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VitalSignsDto {

	@JsonProperty("resourceType")
	private String resourceType = "Observation";
	private String id;
	private String status;
	private VitalSignsCategory category;
	private VitalSignsCode code;
	private VitalSignsSubject subject;
	@JsonProperty("effectiveDateTime")
	private String effectiveDateTime;
	private VitalSignsValue value;
	private VitalSignsEncounter encounter;

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

	public VitalSignsCategory getCategory() {
		return category;
	}

	public void setCategory(VitalSignsCategory category) {
		this.category = category;
	}

	public VitalSignsCode getCode() {
		return code;
	}

	public void setCode(VitalSignsCode code) {
		this.code = code;
	}

	public VitalSignsSubject getSubject() {
		return subject;
	}

	public void setSubject(VitalSignsSubject subject) {
		this.subject = subject;
	}

	public String getEffectiveDateTime() {
		return effectiveDateTime;
	}

	public void setEffectiveDateTime(String effectiveDateTime) {
		this.effectiveDateTime = effectiveDateTime;
	}

	public VitalSignsValue getValue() {
		return value;
	}

	public void setValue(VitalSignsValue value) {
		this.value = value;
	}

	public VitalSignsEncounter getEncounter() {
		return encounter;
	}

	public void setEncounter(VitalSignsEncounter encounter) {
		this.encounter = encounter;
	}

	// Inner classes
	public static class VitalSignsCategory {
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

	public static class VitalSignsCode {
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

	public static class VitalSignsSubject {
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

	public static class VitalSignsValue {
		private String type; // "quantity", "string", "codeableConcept"
		private VitalSignsQuantity quantity;
		private String stringValue;
		private VitalSignsCodeableConcept codeableConcept;

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public VitalSignsQuantity getQuantity() {
			return quantity;
		}

		public void setQuantity(VitalSignsQuantity quantity) {
			this.quantity = quantity;
		}

		public String getStringValue() {
			return stringValue;
		}

		public void setStringValue(String stringValue) {
			this.stringValue = stringValue;
		}

		public VitalSignsCodeableConcept getCodeableConcept() {
			return codeableConcept;
		}

		public void setCodeableConcept(VitalSignsCodeableConcept codeableConcept) {
			this.codeableConcept = codeableConcept;
		}
	}

	public static class VitalSignsQuantity {
		private String value;
		private String unit;
		private String system;
		private String code;

		public VitalSignsQuantity() {
		}

		public VitalSignsQuantity(String value, String unit, String system, String code) {
			this.value = value;
			this.unit = unit;
			this.system = system;
			this.code = code;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}

		public String getUnit() {
			return unit;
		}

		public void setUnit(String unit) {
			this.unit = unit;
		}

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
	}

	public static class VitalSignsCodeableConcept {
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

	public static class VitalSignsEncounter {
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
}