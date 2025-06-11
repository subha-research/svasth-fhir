package com.svasamm.fhir.ehr.mapper;

import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

import com.svasamm.fhir.ehr.dto.patient.PatientDto;
import com.svasamm.fhir.ehr.dto.patient.PatientDto.ContactTelecom;
import com.svasamm.fhir.ehr.dto.patient.PatientDto.MrnObject;
import com.svasamm.fhir.ehr.dto.patient.PatientDto.PatientContact;
import com.svasamm.fhir.ehr.dto.patient.PatientDto.PatientIdentifier;
import com.svasamm.fhir.ehr.dto.patient.PatientDto.PatientName;
import com.svasamm.fhir.ehr.dto.patient.PatientDto.PatientTelecom;

@Component
public class PatientMapper {

    public PatientDto mapToDTO(Patient fhirPatient) {
        PatientDto dto = new PatientDto();

        if (fhirPatient.hasId()) {
            dto.setId(fhirPatient.getIdElement().getIdPart());
        }
        dto.setActive(fhirPatient.getActive());

        // Map identifier
        dto.setIdentifier(mapIdentifier(fhirPatient));

        // Map name
        dto.setName(mapName(fhirPatient));

        // Map gender
        if (fhirPatient.hasGender()) {
            dto.setGender(fhirPatient.getGender().toCode());
        }

        // Map birth date
        if (fhirPatient.hasBirthDate()) {
            dto.setBirthDate(fhirPatient.getBirthDateElement().getValueAsString());
        }

        // Map telecom
        dto.setTelecom(mapTelecom(fhirPatient));

        // Map contact
        dto.setContact(mapContact(fhirPatient));

        return dto;
    }

    private PatientIdentifier mapIdentifier(Patient fhirPatient) {
        PatientIdentifier identifier = new PatientIdentifier();

        Identifier mrnIdentifier = fhirPatient.getIdentifier().stream()
                .filter(id -> id.hasType()
                && id.getType().hasCoding()
                && "MR".equals(id.getType().getCodingFirstRep().getCode()))
                .findFirst()
                .orElse(null);

        if (mrnIdentifier != null) {
            identifier.setPrimary(mrnIdentifier.getValue());
            identifier.setMrnObject(new MrnObject(mrnIdentifier.getSystem(), mrnIdentifier.getValue()));
        }

        return identifier;
    }

    private PatientName mapName(Patient fhirPatient) {
        if (!fhirPatient.hasName()) {
            return null;
        }

        PatientName name = new PatientName();
        HumanName fhirName = fhirPatient.getNameFirstRep();

        if (fhirName.hasGiven()) {
            List<String> givenNames = fhirName.getGiven().stream()
                    .map(g -> g.getValue())
                    .collect(Collectors.toList());
            name.setGiven(givenNames);
        }

        if (fhirName.hasFamily()) {
            name.setFamily(fhirName.getFamily());
        }

        // Create full name
        String fullName = "";
        if (fhirName.hasGiven()) {
            fullName = fhirName.getGiven().stream()
                    .map(g -> g.getValue())
                    .collect(Collectors.joining(" "));
        }
        if (fhirName.hasFamily()) {
            if (!fullName.isEmpty()) {
                fullName += " ";
            }
            fullName += fhirName.getFamily();
        }
        if (!fullName.isEmpty()) {
            name.setFull(fullName);
        }

        return name;
    }

    private PatientTelecom mapTelecom(Patient fhirPatient) {
        if (!fhirPatient.hasTelecom()) {
            return null;
        }

        PatientTelecom telecom = new PatientTelecom();

        fhirPatient.getTelecom().stream()
                .filter(t -> ContactPoint.ContactPointSystem.PHONE.equals(t.getSystem()))
                .findFirst()
                .ifPresent(phone -> telecom.setPhone(phone.getValue()));

        fhirPatient.getTelecom().stream()
                .filter(t -> ContactPoint.ContactPointSystem.EMAIL.equals(t.getSystem()))
                .findFirst()
                .ifPresent(email -> telecom.setEmail(email.getValue()));

        return (telecom.getPhone() != null || telecom.getEmail() != null) ? telecom : null;
    }

    private PatientContact mapContact(Patient fhirPatient) {
        if (!fhirPatient.hasContact()) {
            return null;
        }

        Patient.ContactComponent fhirContact = fhirPatient.getContactFirstRep();
        PatientContact contact = new PatientContact();

        if (fhirContact.hasName()) {
            String contactFullName = "";
            if (fhirContact.getName().hasGiven()) {
                contactFullName = fhirContact.getName().getGiven().stream()
                        .map(g -> g.getValue())
                        .collect(Collectors.joining(" "));
            }
            if (fhirContact.getName().hasFamily()) {
                if (!contactFullName.isEmpty()) {
                    contactFullName += " ";
                }
                contactFullName += fhirContact.getName().getFamily();
            }
            if (!contactFullName.isEmpty()) {
                contact.setName(contactFullName);
            }
        }

        if (fhirContact.hasRelationship()) {
            fhirContact.getRelationshipFirstRep().getCoding().stream()
                    .findFirst()
                    .ifPresent(coding -> contact.setRelationship(coding.getDisplay()));
        }

        if (fhirContact.hasTelecom()) {
            ContactTelecom contactTelecom = new ContactTelecom();
            fhirContact.getTelecom().stream()
                    .filter(t -> ContactPoint.ContactPointSystem.PHONE.equals(t.getSystem()))
                    .findFirst()
                    .ifPresent(phone -> contactTelecom.setPhone(phone.getValue()));

            if (contactTelecom.getPhone() != null) {
                contact.setTelecom(contactTelecom);
            }
        }

        return contact;
    }
}
