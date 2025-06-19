package com.svasamm.fhir.ehr.dto.observation;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ObservationDto - Custom DTO for Observation resource mapping
 */
public class ObservationDto {

	@JsonProperty("resourceType")
	private String resourceType = "Observation";

	private String id;
	private String status;
	private ObservationCode code;
	private ObservationSubject subject;

	@JsonProperty("effectiveDateTime")
	private String effectiveDateTime;

	@JsonProperty("valueQuantity")
	private ObservationValueQuantity valueQuantity;

	@JsonProperty("valueString")
	private String valueString;

	@JsonProperty("valueCodeableConcept")
	private ObservationValueCodeableConcept valueCodeableConcept;

	private List<ObservationComponent> component;
	private ObservationPerformer performer;
	private String interpretation;

	// Default constructor
	public ObservationDto() {
	}

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

	public ObservationCode getCode() {
		return code;
	}

	public void setCode(ObservationCode code) {
		this.code = code;
	}

	public ObservationSubject getSubject() {
		return subject;
	}

	public void setSubject(ObservationSubject subject) {
		this.subject = subject;
	}

	public String getEffectiveDateTime() {
		return effectiveDateTime;
	}

	public void setEffectiveDateTime(String effectiveDateTime) {
		this.effectiveDateTime = effectiveDateTime;
	}

	public ObservationValueQuantity getValueQuantity() {
		return valueQuantity;
	}

	public void setValueQuantity(ObservationValueQuantity valueQuantity) {
		this.valueQuantity = valueQuantity;
	}

	public String getValueString() {
		return valueString;
	}

	public void setValueString(String valueString) {
		this.valueString = valueString;
	}

	public ObservationValueCodeableConcept getValueCodeableConcept() {
		return valueCodeableConcept;
	}

	public void setValueCodeableConcept(ObservationValueCodeableConcept valueCodeableConcept) {
		this.valueCodeableConcept = valueCodeableConcept;
	}

	public List<ObservationComponent> getComponent() {
		return component;
	}

	public void setComponent(List<ObservationComponent> component) {
		this.component = component;
	}

	public ObservationPerformer getPerformer() {
		return performer;
	}

	public void setPerformer(ObservationPerformer performer) {
		this.performer = performer;
	}

	public String getInterpretation() {
		return interpretation;
	}

	public void setInterpretation(String interpretation) {
		this.interpretation = interpretation;
	}

	// Inner Classes
	public static class ObservationCode {
		private List<ObservationCoding> coding;
		private String text;

		public ObservationCode() {
		}

		public List<ObservationCoding> getCoding() {
			return coding;
		}

		public void setCoding(List<ObservationCoding> coding) {
			this.coding = coding;
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}
	}

	public static class ObservationCoding {
		private String system;
		private String code;
		private String display;

		public ObservationCoding() {
		}

		public ObservationCoding(String system, String code, String display) {
			this.system = system;
			this.code = code;
			this.display = display;
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

		public String getDisplay() {
			return display;
		}

		public void setDisplay(String display) {
			this.display = display;
		}
	}

	public static class ObservationSubject {
		private String reference;
		private String display;

		public ObservationSubject() {
		}

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

	public static class ObservationValueQuantity {
		private Double value;
		private String unit;
		private String system;
		private String code;

		public ObservationValueQuantity() {
		}

		public Double getValue() {
			return value;
		}

		public void setValue(Double value) {
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

	public static class ObservationValueCodeableConcept {
		private List<ObservationCoding> coding;
		private String text;

		public ObservationValueCodeableConcept() {
		}

		public List<ObservationCoding> getCoding() {
			return coding;
		}

		public void setCoding(List<ObservationCoding> coding) {
			this.coding = coding;
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}
	}

	public static class ObservationComponent {
		private ObservationCode code;

		@JsonProperty("valueQuantity")
		private ObservationValueQuantity valueQuantity;

		@JsonProperty("valueString")
		private String valueString;

		@JsonProperty("valueCodeableConcept")
		private ObservationValueCodeableConcept valueCodeableConcept;

		public ObservationComponent() {
		}

		public ObservationCode getCode() {
			return code;
		}

		public void setCode(ObservationCode code) {
			this.code = code;
		}

		public ObservationValueQuantity getValueQuantity() {
			return valueQuantity;
		}

		public void setValueQuantity(ObservationValueQuantity valueQuantity) {
			this.valueQuantity = valueQuantity;
		}

		public String getValueString() {
			return valueString;
		}

		public void setValueString(String valueString) {
			this.valueString = valueString;
		}

		public ObservationValueCodeableConcept getValueCodeableConcept() {
			return valueCodeableConcept;
		}

		public void setValueCodeableConcept(ObservationValueCodeableConcept valueCodeableConcept) {
			this.valueCodeableConcept = valueCodeableConcept;
		}
	}

	public static class ObservationPerformer {
		private String reference;
		private String display;

		public ObservationPerformer() {
		}

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