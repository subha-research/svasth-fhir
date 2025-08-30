package com.svasamm.fhir.ehr.mapper;

import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Medication;
import org.springframework.stereotype.Component;

import com.svasamm.fhir.ehr.dto.medication.MedicationDto;
import com.svasamm.fhir.ehr.dto.medication.MedicationDto.MedicationBatch;
import com.svasamm.fhir.ehr.dto.medication.MedicationDto.MedicationCode;
import com.svasamm.fhir.ehr.dto.medication.MedicationDto.MedicationForm;
import com.svasamm.fhir.ehr.dto.medication.MedicationDto.MedicationIngredient;
import com.svasamm.fhir.ehr.dto.medication.MedicationDto.MedicationManufacturer;
import com.svasamm.fhir.ehr.dto.medication.MedicationDto.MedicationStrength;

@Component
public class MedicationMapper {

	public MedicationDto mapToDTO(Medication fhirMedication) {
		MedicationDto dto = new MedicationDto();

		if (fhirMedication.hasId()) {
			dto.setId(fhirMedication.getIdElement().getIdPart());
		}

		// Map status
		if (fhirMedication.hasStatus()) {
			dto.setStatus(fhirMedication.getStatus().toCode());
		}

		// Map code
		dto.setCode(mapCode(fhirMedication));

		// Map form
		dto.setForm(mapForm(fhirMedication));

		// Map manufacturer
		dto.setManufacturer(mapManufacturer(fhirMedication));

		// Map batch
		dto.setBatch(mapBatch(fhirMedication));

		// Map ingredients
		dto.setIngredients(mapIngredients(fhirMedication));

		return dto;
	}

	private MedicationCode mapCode(Medication fhirMedication) {
		if (!fhirMedication.hasCode()) {
			return null;
		}

		MedicationCode code = new MedicationCode();

		if (fhirMedication.getCode().hasCoding()) {
			Coding firstCoding = fhirMedication.getCode().getCodingFirstRep();
			code.setSystem(firstCoding.getSystem());
			code.setCode(firstCoding.getCode());
			code.setDisplay(firstCoding.getDisplay());
		}

		if (fhirMedication.getCode().hasText()) {
			code.setText(fhirMedication.getCode().getText());
		}

		return code;
	}

	private MedicationForm mapForm(Medication fhirMedication) {
		if (!fhirMedication.hasForm()) {
			return null;
		}

		MedicationForm form = new MedicationForm();

		if (fhirMedication.getForm().hasCoding()) {
			Coding firstCoding = fhirMedication.getForm().getCodingFirstRep();
			form.setSystem(firstCoding.getSystem());
			form.setCode(firstCoding.getCode());
			form.setDisplay(firstCoding.getDisplay());
		}

		return form;
	}

	private MedicationManufacturer mapManufacturer(Medication fhirMedication) {
		if (!fhirMedication.hasManufacturer()) {
			return null;
		}

		MedicationManufacturer manufacturer = new MedicationManufacturer();
		manufacturer.setReference(fhirMedication.getManufacturer().getReference());
		manufacturer.setDisplay(fhirMedication.getManufacturer().getDisplay());

		return manufacturer;
	}

	private MedicationBatch mapBatch(Medication fhirMedication) {
		if (!fhirMedication.hasBatch()) {
			return null;
		}

		MedicationBatch batch = new MedicationBatch();

		if (fhirMedication.getBatch().hasLotNumber()) {
			batch.setLotNumber(fhirMedication.getBatch().getLotNumber());
		}

		if (fhirMedication.getBatch().hasExpirationDate()) {
			batch.setExpirationDate(fhirMedication.getBatch().getExpirationDateElement().getValueAsString());
		}

		return batch;
	}

	private List<MedicationIngredient> mapIngredients(Medication fhirMedication) {
		if (!fhirMedication.hasIngredient()) {
			return null;
		}

		return fhirMedication.getIngredient().stream()
				.map(this::mapSingleIngredient)
				.collect(Collectors.toList());
	}

	private MedicationIngredient mapSingleIngredient(Medication.MedicationIngredientComponent fhirIngredient) {
		MedicationIngredient ingredient = new MedicationIngredient();

		// Map ingredient name
		if (fhirIngredient.hasItemCodeableConcept()) {
			if (fhirIngredient.getItemCodeableConcept().hasCoding()) {
				Coding firstCoding = fhirIngredient.getItemCodeableConcept().getCodingFirstRep();
				ingredient.setName(firstCoding.getDisplay() != null ? firstCoding.getDisplay() : firstCoding.getCode());
			} else if (fhirIngredient.getItemCodeableConcept().hasText()) {
				ingredient.setName(fhirIngredient.getItemCodeableConcept().getText());
			}
		}

		// Map strength
		if (fhirIngredient.hasStrength()) {
			MedicationStrength strength = new MedicationStrength();

			if (fhirIngredient.getStrength().hasNumerator()) {
				String value = fhirIngredient.getStrength().getNumerator().getValue().toString();
				String unit = fhirIngredient.getStrength().getNumerator().getUnit();
				strength.setValue(value);
				strength.setUnit(unit);
			}

			ingredient.setStrength(strength);
		}

		return ingredient;
	}
}