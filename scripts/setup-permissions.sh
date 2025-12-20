#!/bin/bash
set -e

# Configuration
PROJECT_ID="${GCP_PROJECT_ID:?Error: GCP_PROJECT_ID environment variable must be set}"
SERVICE_ACCOUNT="${SERVICE_ACCOUNT:?Error: SERVICE_ACCOUNT environment variable must be set}"

echo "Setting up IAM permissions for project: $PROJECT_ID"
echo "Service Account: $SERVICE_ACCOUNT"
echo ""

# Grant required permissions
echo "Granting Cloud Monitoring Viewer role..."
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$SERVICE_ACCOUNT" \
  --role="roles/monitoring.viewer"

echo "Granting BigQuery Data Editor role..."
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$SERVICE_ACCOUNT" \
  --role="roles/bigquery.dataEditor"

echo "Granting BigQuery Job User role..."
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$SERVICE_ACCOUNT" \
  --role="roles/bigquery.jobUser"

echo ""
echo "✓ IAM permissions configured successfully!"
echo ""
echo "Service account $SERVICE_ACCOUNT now has:"
echo "  - monitoring.viewer (read Cloud Monitoring metrics)"
echo "  - bigquery.dataEditor (write to BigQuery tables)"
echo "  - bigquery.jobUser (execute BigQuery jobs)"