#!/bin/bash

BASE_URL="${1:-http://localhost:8080/fhir}"

echo "=== Testing FHIR Endpoints ==="
echo "Base URL: $BASE_URL"

# Test metadata endpoint
echo "1. Testing metadata endpoint..."
METADATA=$(curl -s "$BASE_URL/metadata")
if echo "$METADATA" | grep -q "fhirVersion"; then
    echo "✅ Metadata endpoint working"
    
    # Check which modules are enabled
    if echo "$METADATA" | grep -q '"type": "Patient"'; then
        echo "✅ EHR Module enabled (Patient resources available)"
        EHR_ENABLED=true
    else
        echo "❌ EHR Module disabled (Patient resources not available)"
        EHR_ENABLED=false
    fi
    
    if echo "$METADATA" | grep -q '"type": "Specimen"'; then
        echo "✅ Biobank Module enabled (Specimen resources available)"
        BIOBANK_ENABLED=true
    else
        echo "❌ Biobank Module disabled (Specimen resources not available)"
        BIOBANK_ENABLED=false
    fi
else
    echo "❌ Metadata endpoint failed"
    exit 1
fi

# Test EHR endpoints if enabled
if [ "$EHR_ENABLED" = true ]; then
    echo ""
    echo "2. Testing EHR endpoints..."
    
    # Create a test patient
    PATIENT_RESPONSE=$(curl -s -X POST "$BASE_URL/Patient" \
      -H "Content-Type: application/fhir+json" \
      -d '{
        "resourceType": "Patient",
        "name": [{"family": "TestPatient", "given": ["John"]}],
        "birthDate": "1980-01-01",
        "gender": "male"
      }')
    
    PATIENT_ID=$(echo $PATIENT_RESPONSE | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
    
    if [ -n "$PATIENT_ID" ]; then
        echo "✅ Patient created with ID: $PATIENT_ID"
        
        # Test patient read
        if curl -s "$BASE_URL/Patient/$PATIENT_ID" | grep -q "TestPatient"; then
            echo "✅ Patient read successful"
        else
            echo "❌ Patient read failed"
        fi
        
        # Test patient summary operation
        if curl -s "$BASE_URL/Patient/$PATIENT_ID/\$patient-summary" | grep -q "Bundle"; then
            echo "✅ Patient summary operation successful"
        else
            echo "❌ Patient summary operation failed"
        fi
    else
        echo "❌ Patient creation failed"
    fi
fi

# Test Biobank endpoints if enabled
if [ "$BIOBANK_ENABLED" = true ]; then
    echo ""
    echo "3. Testing Biobank endpoints..."
    
    # Use existing patient or create minimal reference
    if [ -n "$PATIENT_ID" ]; then
        SUBJECT_REF="Patient/$PATIENT_ID"
    else
        SUBJECT_REF="Patient/test-patient"
    fi
    
    # Create a test specimen
    SPECIMEN_RESPONSE=$(curl -s -X POST "$BASE_URL/Specimen" \
      -H "Content-Type: application/fhir+json" \
      -d "{
        \"resourceType\": \"Specimen\",
        \"subject\": {\"reference\": \"$SUBJECT_REF\"},
        \"type\": {
          \"coding\": [{
            \"system\": \"http://snomed.info/sct\",
            \"code\": \"119297000\",
            \"display\": \"Blood specimen\"
          }]
        },
        \"collection\": {
          \"collectedDateTime\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"
        }
      }")
    
    SPECIMEN_ID=$(echo $SPECIMEN_RESPONSE | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
    
    if [ -n "$SPECIMEN_ID" ]; then
        echo "✅ Specimen created with ID: $SPECIMEN_ID"
        
        # Test specimen read
        if curl -s "$BASE_URL/Specimen/$SPECIMEN_ID" | grep -q "Blood specimen"; then
            echo "✅ Specimen read successful"
        else
            echo "❌ Specimen read failed"
        fi
        
        # Test chain of custody operation
        if curl -s "$BASE_URL/Specimen/$SPECIMEN_ID/\$chain-of-custody" | grep -q "Bundle"; then
            echo "✅ Chain of custody operation successful"
        else
            echo "❌ Chain of custody operation failed"
        fi
    else
        echo "❌ Specimen creation failed"
    fi
fi

echo ""
echo "=== Testing Complete ==="