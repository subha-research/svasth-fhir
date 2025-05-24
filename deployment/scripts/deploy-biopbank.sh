#!/bin/bash

set -e

HOSPITAL_NAME="${1:-Research Biobank}"
HOSPITAL_ID="${2:-BB-$(date +%s)}"

echo "=== Deploying Biobank-Only FHIR Platform ==="
echo "Biobank: $HOSPITAL_NAME"
echo "ID: $HOSPITAL_ID"

# Create .env file for Biobank deployment
cat > .env << EOF
DB_PASSWORD=biobank_secure_$(date +%s | tail -c 6)
HOSPITAL_NAME=$HOSPITAL_NAME
HOSPITAL_ID=$HOSPITAL_ID
HOSPITAL_TYPE=biobank
EHR_ENABLED=false
BIOBANK_ENABLED=true
SPRING_PROFILES_ACTIVE=r4,biobank
EOF

echo "Starting Biobank deployment..."
docker-compose -f docker-compose-biobank.yml up -d --build

echo "Waiting for services to start..."
sleep 60

# Check if FHIR server is running
if curl -f http://localhost:8080/fhir/metadata > /dev/null 2>&1; then
    echo "✅ Biobank FHIR Platform deployed successfully!"
    echo "📄 Metadata: http://localhost:8080/fhir/metadata"
    echo "🧬 Specimen endpoint: http://localhost:8080/fhir/Specimen"
    echo "🔬 Chain of custody: http://localhost:8080/fhir/Specimen/[id]/\$chain-of-custody"
    echo "❌ Patient endpoint: DISABLED (Biobank-only mode)"
else
    echo "❌ Deployment failed. Check logs:"
    echo "docker-compose -f docker-compose-biobank.yml logs"
fi