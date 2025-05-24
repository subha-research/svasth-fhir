#!/bin/bash

set -e

HOSPITAL_NAME="${1:-University Medical Center}"
HOSPITAL_ID="${2:-UMC-$(date +%s)}"

echo "=== Deploying Full FHIR Platform (EHR + Biobank) ==="
echo "Hospital: $HOSPITAL_NAME"
echo "ID: $HOSPITAL_ID"

# Create .env file for full deployment
cat > .env << EOF
DB_PASSWORD=full_secure_$(date +%s | tail -c 6)
HOSPITAL_NAME=$HOSPITAL_NAME
HOSPITAL_ID=$HOSPITAL_ID
HOSPITAL_TYPE=both
EHR_ENABLED=true
BIOBANK_ENABLED=true
SPRING_PROFILES_ACTIVE=r4,full
EOF

echo "Starting full platform deployment..."
docker-compose up -d --build

echo "Waiting for services to start..."
sleep 60

# Check if FHIR server is running
if curl -f http://localhost:8080/fhir/metadata > /dev/null 2>&1; then
    echo "✅ Full FHIR Platform deployed successfully!"
    echo "📄 Metadata: http://localhost:8080/fhir/metadata"
    echo "🏥 Patient endpoint: http://localhost:8080/fhir/Patient"
    echo "👨‍⚕️ Practitioner endpoint: http://localhost:8080/fhir/Practitioner"
    echo "🧬 Specimen endpoint: http://localhost:8080/fhir/Specimen"
    echo "📊 All operations enabled"
else
    echo "❌ Deployment failed. Check logs:"
    echo "docker-compose logs"
fi