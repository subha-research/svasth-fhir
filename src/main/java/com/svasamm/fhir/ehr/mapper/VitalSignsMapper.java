package com.svasamm.fhir.ehr.mapper;

import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.stereotype.Component;

import com.svasamm.fhir.ehr.dto.vitalsigns.VitalSignsDto;
import com.svasamm.fhir.ehr.dto.vitalsigns.VitalSignsDto.VitalSignsCategory;
import com.svasamm.fhir.ehr.dto.vitalsigns.VitalSignsDto.VitalSignsCode;
import com.svasamm.fhir.ehr.dto.vitalsigns.VitalSignsDto.VitalSignsSubject;
import com.svasamm.fhir.ehr.dto.vitalsigns.VitalSignsDto.VitalSignsValue;
import com.svasamm.fhir.ehr.dto.vitalsigns.VitalSignsDto.VitalSignsQuantity;
import com.svasamm.fhir.ehr.dto.vitalsigns.VitalSignsDto.VitalSignsCodeableConcept;
import com.svasamm.fhir.ehr.dto.vitalsigns.VitalSignsDto.VitalSignsEncounter;

@Component
public class VitalSignsMapper {

	public VitalSignsDto mapToDTO(Observation fhirVitalSign) {
		VitalSignsDto dto = new VitalSignsDto();

		if (fhirVitalSign.hasId()) {
			dto.setId(fhirVitalSign.getIdElement().getIdPart());
		}

		// Map status
		if (fhirVitalSign.hasStatus()) {
			dto.setStatus(fhirVitalSign.getStatus().toCode());
		}

		// Map category (should be vital-signs)
		dto.setCategory(mapCategory(fhirVitalSign));

		// Map code
		dto.setCode(mapCode(fhirVitalSign));

		// Map subject
		dto.setSubject(mapSubject(fhirVitalSign));

		// Map effective date time
		if (fhirVitalSign.hasEffectiveDateTimeType()) {
			dto.setEffectiveDateTime(fhirVitalSign.getEffectiveDateTimeType().getValueAsString());
		}

		// Map value
		dto.setValue(mapValue(fhirVitalSign));

		// Map encounter
		dto.setEncounter(mapEncounter(fhirVitalSign));

		return dto;
	}

	private VitalSignsCategory mapCategory(Observation fhirVitalSign) {
		if (!fhirVitalSign.hasCategory()) {
			return null;
		}

		// Get the vital-signs category
		return fhirVitalSign.getCategory().stream()
				.filter(category -> category.getCoding().stream()
						.anyMatch(coding -> "vital-signs".equals(coding.getCode())))
				.findFirst()
				.map(category -> {
					VitalSignsCategory cat = new VitalSignsCategory();
					if (category.hasCoding()) {
						Coding firstCoding = category.getCodingFirstRep();
						cat.setSystem(firstCoding.getSystem());
						cat.setCode(firstCoding.getCode());
						cat.setDisplay(firstCoding.getDisplay());
					}
					return cat;
				})
				.orElse(null);
	}

	private VitalSignsCode mapCode(Observation fhirVitalSign) {
		if (!fhirVitalSign.hasCode()) {
			return null;
		}

		VitalSignsCode code = new VitalSignsCode();

		if (fhirVitalSign.getCode().hasCoding()) {
			Coding firstCoding = fhirVitalSign.getCode().getCodingFirstRep();
			code.setSystem(firstCoding.getSystem());
			code.setCode(firstCoding.getCode());
			code.setDisplay(firstCoding.getDisplay());
		}

		if (fhirVitalSign.getCode().hasText()) {
			code.setText(fhirVitalSign.getCode().getText());
		}

		return code;
	}

	private VitalSignsSubject mapSubject(Observation fhirVitalSign) {
		if (!fhirVitalSign.hasSubject()) {
			return null;
		}

		VitalSignsSubject subject = new VitalSignsSubject();
		subject.setReference(fhirVitalSign.getSubject().getReference());
		subject.setDisplay(fhirVitalSign.getSubject().getDisplay());

		return subject;
	}

	private VitalSignsValue mapValue(Observation fhirVitalSign) {
		VitalSignsValue value = new VitalSignsValue();

		if (fhirVitalSign.hasValueQuantity()) {
			value.setType("quantity");

			VitalSignsQuantity quantity = new VitalSignsQuantity();
			quantity.setValue(fhirVitalSign.getValueQuantity().getValue().toString());
			quantity.setUnit(fhirVitalSign.getValueQuantity().getUnit());
			quantity.setSystem(fhirVitalSign.getValueQuantity().getSystem());
			quantity.setCode(fhirVitalSign.getValueQuantity().getCode());

			value.setQuantity(quantity);
		} else if (fhirVitalSign.hasValueStringType()) {
			value.setType("string");
			value.setStringValue(fhirVitalSign.getValueStringType().getValue());
		} else if (fhirVitalSign.hasValueCodeableConcept()) {
			value.setType("codeableConcept");

			VitalSignsCodeableConcept concept = new VitalSignsCodeableConcept();
			if (fhirVitalSign.getValueCodeableConcept().hasCoding()) {
				Coding firstCoding = fhirVitalSign.getValueCodeableConcept().getCodingFirstRep();
				concept.setSystem(firstCoding.getSystem());
				concept.setCode(firstCoding.getCode());
				concept.setDisplay(firstCoding.getDisplay());
			}
			if (fhirVitalSign.getValueCodeableConcept().hasText()) {
				concept.setText(fhirVitalSign.getValueCodeableConcept().getText());
			}

			value.setCodeableConcept(concept);
		} else {
			// No value found
			return null;
		}

		return value;
	}

	private VitalSignsEncounter mapEncounter(Observation fhirVitalSign) {
		if (!fhirVitalSign.hasEncounter()) {
			return null;
		}

		VitalSignsEncounter encounter = new VitalSignsEncounter();
		encounter.setReference(fhirVitalSign.getEncounter().getReference());
		encounter.setDisplay(fhirVitalSign.getEncounter().getDisplay());

		return encounter;
	}

	// Utility method to check if observation is a vital sign
	public boolean isVitalSign(Observation observation) {
		if (observation == null)
			return false;

		return observation.getCategory().stream()
				.anyMatch(category -> category.getCoding().stream()
						.anyMatch(coding -> "vital-signs".equals(coding.getCode()) &&
								"http://terminology.hl7.org/CodeSystem/observation-category"
										.equals(coding.getSystem())));
	}

	// Method to get vital sign type from LOINC code
	public String getVitalSignType(String loincCode) {
		switch (loincCode) {
			case "8310-5":
				return "Body temperature";
			case "8867-4":
				return "Heart rate";
			case "8480-6":
				return "Systolic blood pressure";
			case "8462-4":
				return "Diastolic blood pressure";
			case "9279-1":
				return "Respiratory rate";
			case "2708-6":
				return "Oxygen saturation";
			case "29463-7":
				return "Body weight";
			case "8302-2":
				return "Body height";
			case "39156-5":
				return "Body mass index";
			default:
				return "Unknown vital sign";
		}
	}

	// Method to get appropriate unit for vital sign
	public String getExpectedUnit(String loincCode) {
		switch (loincCode) {
			case "8310-5":
				return "Cel"; // Celsius
			case "8867-4":
				return "/min"; // beats per minute
			case "8480-6":
			case "8462-4":
				return "mm[Hg]"; // mmHg
			case "9279-1":
				return "/min"; // breaths per minute
			case "2708-6":
				return "%"; // percentage
			case "29463-7":
				return "kg"; // kilograms
			case "8302-2":
				return "cm"; // centimeters
			case "39156-5":
				return "kg/m2"; // BMI
			default:
				return "";
		}
	}
}