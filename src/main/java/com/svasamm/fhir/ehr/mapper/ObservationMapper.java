package com.svasamm.fhir.ehr.mapper;

import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.springframework.stereotype.Component;

import com.svasamm.fhir.ehr.dto.observation.ObservationDto;
import com.svasamm.fhir.ehr.dto.observation.ObservationDto.ObservationCode;
import com.svasamm.fhir.ehr.dto.observation.ObservationDto.ObservationCoding;
import com.svasamm.fhir.ehr.dto.observation.ObservationDto.ObservationSubject;
import com.svasamm.fhir.ehr.dto.observation.ObservationDto.ObservationValueQuantity;
import com.svasamm.fhir.ehr.dto.observation.ObservationDto.ObservationValueCodeableConcept;
import com.svasamm.fhir.ehr.dto.observation.ObservationDto.ObservationComponent;
import com.svasamm.fhir.ehr.dto.observation.ObservationDto.ObservationPerformer;

/**
 * Mapper class to convert between FHIR Observation and ObservationDto
 */
@Component
public class ObservationMapper {

	/**
	 * Map FHIR Observation to ObservationDto
	 */
	public ObservationDto mapToDTO(Observation observation) {
		if (observation == null) {
			return null;
		}

		ObservationDto dto = new ObservationDto();

		// Basic fields
		dto.setId(observation.getIdElement().getIdPart());
		dto.setStatus(observation.getStatus() != null ? observation.getStatus().toCode() : null);

		// Code
		if (observation.hasCode()) {
			dto.setCode(mapCodeableConceptToObservationCode(observation.getCode()));
		}

		// Subject
		if (observation.hasSubject()) {
			dto.setSubject(mapReferenceToObservationSubject(observation.getSubject()));
		}

		// Effective DateTime
		if (observation.hasEffective()) {
			if (observation.getEffective() instanceof DateTimeType) {
				DateTimeType dateTime = (DateTimeType) observation.getEffective();
				dto.setEffectiveDateTime(dateTime.getValueAsString());
			}
		}

		// Value handling
		if (observation.hasValue()) {
			if (observation.getValue() instanceof Quantity) {
				Quantity quantity = (Quantity) observation.getValue();
				dto.setValueQuantity(mapQuantityToObservationValueQuantity(quantity));
			} else if (observation.getValue() instanceof StringType) {
				StringType stringValue = (StringType) observation.getValue();
				dto.setValueString(stringValue.getValue());
			} else if (observation.getValue() instanceof CodeableConcept) {
				CodeableConcept concept = (CodeableConcept) observation.getValue();
				dto.setValueCodeableConcept(mapCodeableConceptToObservationValueCodeableConcept(concept));
			}
		}

		// Components
		if (observation.hasComponent()) {
			List<ObservationComponent> components = observation.getComponent().stream()
					.map(this::mapObservationComponentToDto)
					.collect(Collectors.toList());
			dto.setComponent(components);
		}

		// Performer
		if (observation.hasPerformer() && !observation.getPerformer().isEmpty()) {
			Reference performerRef = observation.getPerformer().get(0);
			dto.setPerformer(mapReferenceToObservationPerformer(performerRef));
		}

		// Interpretation
		if (observation.hasInterpretation() && !observation.getInterpretation().isEmpty()) {
			CodeableConcept interpretation = observation.getInterpretation().get(0);
			dto.setInterpretation(interpretation.getText());
		}

		return dto;
	}

	/**
	 * Map ObservationDto to FHIR Observation
	 */
	public Observation mapToFHIR(ObservationDto dto) {
		if (dto == null) {
			return null;
		}

		Observation observation = new Observation();

		// Basic fields
		if (dto.getId() != null) {
			observation.setId(dto.getId());
		}

		if (dto.getStatus() != null) {
			observation.setStatus(Observation.ObservationStatus.fromCode(dto.getStatus()));
		}

		// Code
		if (dto.getCode() != null) {
			observation.setCode(mapObservationCodeToCodeableConcept(dto.getCode()));
		}

		// Subject
		if (dto.getSubject() != null) {
			observation.setSubject(mapObservationSubjectToReference(dto.getSubject()));
		}

		// Effective DateTime
		if (dto.getEffectiveDateTime() != null) {
			observation.setEffective(new DateTimeType(dto.getEffectiveDateTime()));
		}

		// Value handling
		if (dto.getValueQuantity() != null) {
			observation.setValue(mapObservationValueQuantityToQuantity(dto.getValueQuantity()));
		} else if (dto.getValueString() != null) {
			observation.setValue(new StringType(dto.getValueString()));
		} else if (dto.getValueCodeableConcept() != null) {
			observation.setValue(mapObservationValueCodeableConceptToCodeableConcept(dto.getValueCodeableConcept()));
		}

		// Components
		if (dto.getComponent() != null && !dto.getComponent().isEmpty()) {
			List<Observation.ObservationComponentComponent> components = dto.getComponent().stream()
					.map(this::mapDtoToObservationComponent)
					.collect(Collectors.toList());
			observation.setComponent(components);
		}

		return observation;
	}

	// Helper methods for mapping nested objects

	private ObservationCode mapCodeableConceptToObservationCode(CodeableConcept concept) {
		ObservationCode code = new ObservationCode();

		if (concept.hasCoding()) {
			List<ObservationCoding> codings = concept.getCoding().stream()
					.map(this::mapCodingToObservationCoding)
					.collect(Collectors.toList());
			code.setCoding(codings);
		}

		if (concept.hasText()) {
			code.setText(concept.getText());
		}

		return code;
	}

	private ObservationCoding mapCodingToObservationCoding(Coding coding) {
		return new ObservationCoding(
				coding.getSystem(),
				coding.getCode(),
				coding.getDisplay());
	}

	private ObservationSubject mapReferenceToObservationSubject(Reference reference) {
		ObservationSubject subject = new ObservationSubject();
		subject.setReference(reference.getReference());
		subject.setDisplay(reference.getDisplay());
		return subject;
	}

	private ObservationValueQuantity mapQuantityToObservationValueQuantity(Quantity quantity) {
		ObservationValueQuantity valueQuantity = new ObservationValueQuantity();
		valueQuantity.setValue(quantity.getValue() != null ? quantity.getValue().doubleValue() : null);
		valueQuantity.setUnit(quantity.getUnit());
		valueQuantity.setSystem(quantity.getSystem());
		valueQuantity.setCode(quantity.getCode());
		return valueQuantity;
	}

	private ObservationValueCodeableConcept mapCodeableConceptToObservationValueCodeableConcept(
			CodeableConcept concept) {
		ObservationValueCodeableConcept valueCodeableConcept = new ObservationValueCodeableConcept();

		if (concept.hasCoding()) {
			List<ObservationCoding> codings = concept.getCoding().stream()
					.map(this::mapCodingToObservationCoding)
					.collect(Collectors.toList());
			valueCodeableConcept.setCoding(codings);
		}

		if (concept.hasText()) {
			valueCodeableConcept.setText(concept.getText());
		}

		return valueCodeableConcept;
	}

	private ObservationComponent mapObservationComponentToDto(Observation.ObservationComponentComponent component) {
		ObservationComponent dtoComponent = new ObservationComponent();

		if (component.hasCode()) {
			dtoComponent.setCode(mapCodeableConceptToObservationCode(component.getCode()));
		}

		if (component.hasValue()) {
			if (component.getValue() instanceof Quantity) {
				Quantity quantity = (Quantity) component.getValue();
				dtoComponent.setValueQuantity(mapQuantityToObservationValueQuantity(quantity));
			} else if (component.getValue() instanceof StringType) {
				StringType stringValue = (StringType) component.getValue();
				dtoComponent.setValueString(stringValue.getValue());
			} else if (component.getValue() instanceof CodeableConcept) {
				CodeableConcept concept = (CodeableConcept) component.getValue();
				dtoComponent.setValueCodeableConcept(mapCodeableConceptToObservationValueCodeableConcept(concept));
			}
		}

		return dtoComponent;
	}

	private ObservationPerformer mapReferenceToObservationPerformer(Reference reference) {
		ObservationPerformer performer = new ObservationPerformer();
		performer.setReference(reference.getReference());
		performer.setDisplay(reference.getDisplay());
		return performer;
	}

	// Reverse mapping methods (DTO to FHIR)

	private CodeableConcept mapObservationCodeToCodeableConcept(ObservationCode code) {
		CodeableConcept concept = new CodeableConcept();

		if (code.getCoding() != null) {
			List<Coding> codings = code.getCoding().stream()
					.map(this::mapObservationCodingToCoding)
					.collect(Collectors.toList());
			concept.setCoding(codings);
		}

		if (code.getText() != null) {
			concept.setText(code.getText());
		}

		return concept;
	}

	private Coding mapObservationCodingToCoding(ObservationCoding observationCoding) {
		Coding coding = new Coding();
		coding.setSystem(observationCoding.getSystem());
		coding.setCode(observationCoding.getCode());
		coding.setDisplay(observationCoding.getDisplay());
		return coding;
	}

	private Reference mapObservationSubjectToReference(ObservationSubject subject) {
		Reference reference = new Reference();
		reference.setReference(subject.getReference());
		reference.setDisplay(subject.getDisplay());
		return reference;
	}

	private Quantity mapObservationValueQuantityToQuantity(ObservationValueQuantity valueQuantity) {
		Quantity quantity = new Quantity();
		quantity.setValue(valueQuantity.getValue());
		quantity.setUnit(valueQuantity.getUnit());
		quantity.setSystem(valueQuantity.getSystem());
		quantity.setCode(valueQuantity.getCode());
		return quantity;
	}

	private CodeableConcept mapObservationValueCodeableConceptToCodeableConcept(
			ObservationValueCodeableConcept valueCodeableConcept) {
		CodeableConcept concept = new CodeableConcept();

		if (valueCodeableConcept.getCoding() != null) {
			List<Coding> codings = valueCodeableConcept.getCoding().stream()
					.map(this::mapObservationCodingToCoding)
					.collect(Collectors.toList());
			concept.setCoding(codings);
		}

		if (valueCodeableConcept.getText() != null) {
			concept.setText(valueCodeableConcept.getText());
		}

		return concept;
	}

	private Observation.ObservationComponentComponent mapDtoToObservationComponent(ObservationComponent dtoComponent) {
		Observation.ObservationComponentComponent component = new Observation.ObservationComponentComponent();

		if (dtoComponent.getCode() != null) {
			component.setCode(mapObservationCodeToCodeableConcept(dtoComponent.getCode()));
		}

		if (dtoComponent.getValueQuantity() != null) {
			component.setValue(mapObservationValueQuantityToQuantity(dtoComponent.getValueQuantity()));
		} else if (dtoComponent.getValueString() != null) {
			component.setValue(new StringType(dtoComponent.getValueString()));
		} else if (dtoComponent.getValueCodeableConcept() != null) {
			component.setValue(
					mapObservationValueCodeableConceptToCodeableConcept(dtoComponent.getValueCodeableConcept()));
		}

		return component;
	}
}