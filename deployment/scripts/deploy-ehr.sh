#!/bin/bash

set -e

HOSPITAL_NAME="${1:-General Hospital}"
HOSPITAL_ID="${2:-GH-$(date +%s)}"

echo "=== Deploying EHR-Only FHIR Platform ==="
echo "Hospital: $HOSPITAL_NAME"
echo "ID: $HOSPITAL_ID"

# Create .env file for EHR deployment
cat > .env << EOF
DB_PASSWORD=ehr_secure_$(date +%s | tail -c 6)
HOSPITAL_NAME=$HOSPITAL_NAME
HOSPITAL_ID=$HOSPITAL_ID
HOSPITAL_TYPE=ehr
EHR_ENABLED=true
BIOBANK_ENABLED=false
SPRING_PROFILES_ACTIVE=r4,ehr
EOF

echo "Starting EHR deployment..."
docker-compose -f docker-compose-ehr.yml up -d --build

echo "Waiting for services to start..."
sleep 60

# Check if FHIR server is running
if curl -f http://localhost:8080/fhir/metadata > /dev/null 2>&1; then
    echo "✅ EHR FHIR Platform deployed successfully!"
    echo "📄 Metadata: http://localhost:8080/fhir/metadata"
    echo "🏥 Patient endpoint: http://localhost:8080/fhir/Patient"
    echo "👨‍⚕️ Practitioner endpoint: http://localhost:8080/fhir/Practitioner"
    echo "❌ Specimen endpoint: DISABLED (EHR-only mode)"
else
    echo "❌ Deployment failed. Check logs:"
    echo "docker-compose -f docker-compose-ehr.yml logs"
fi