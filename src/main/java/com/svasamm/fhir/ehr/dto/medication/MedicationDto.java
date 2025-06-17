package com.svasamm.fhir.ehr.dto.medication;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MedicationDto {

	@JsonProperty("resourceType")
	private String resourceType = "Medication";
	private String id;
	private String status;
	private MedicationCode code;
	private MedicationForm form;
	private MedicationManufacturer manufacturer;
	private MedicationBatch batch;
	private List<MedicationIngredient> ingredients;

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

	public MedicationCode getCode() {
		return code;
	}

	public void setCode(MedicationCode code) {
		this.code = code;
	}

	public MedicationForm getForm() {
		return form;
	}

	public void setForm(MedicationForm form) {
		this.form = form;
	}

	public MedicationManufacturer getManufacturer() {
		return manufacturer;
	}

	public void setManufacturer(MedicationManufacturer manufacturer) {
		this.manufacturer = manufacturer;
	}

	public MedicationBatch getBatch() {
		return batch;
	}

	public void setBatch(MedicationBatch batch) {
		this.batch = batch;
	}

	public List<MedicationIngredient> getIngredients() {
		return ingredients;
	}

	public void setIngredients(List<MedicationIngredient> ingredients) {
		this.ingredients = ingredients;
	}

	// Inner classes
	public static class MedicationCode {
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

	public static class MedicationForm {
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

	public static class MedicationManufacturer {
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

	public static class MedicationBatch {
		private String lotNumber;
		@JsonProperty("expirationDate")
		private String expirationDate;

		public String getLotNumber() {
			return lotNumber;
		}

		public void setLotNumber(String lotNumber) {
			this.lotNumber = lotNumber;
		}

		public String getExpirationDate() {
			return expirationDate;
		}

		public void setExpirationDate(String expirationDate) {
			this.expirationDate = expirationDate;
		}
	}

	public static class MedicationIngredient {
		private String name;
		private MedicationStrength strength;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public MedicationStrength getStrength() {
			return strength;
		}

		public void setStrength(MedicationStrength strength) {
			this.strength = strength;
		}
	}

	public static class MedicationStrength {
		private String value;
		private String unit;

		public MedicationStrength() {
		}

		public MedicationStrength(String value, String unit) {
			this.value = value;
			this.unit = unit;
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
	}
}