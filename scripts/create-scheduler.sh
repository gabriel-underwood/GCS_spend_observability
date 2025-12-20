#!/bin/bash
set -e

# Configuration
PROJECT_ID="${GCP_PROJECT_ID:?Error: GCP_PROJECT_ID environment variable must be set}"
SERVICE_ACCOUNT="${SERVICE_ACCOUNT:?Error: SERVICE_ACCOUNT environment variable must be set}"
REGION="${CLOUD_RUN_REGION:-us-central1}"
JOB_NAME="${JOB_NAME:-gcs-metrics-collector}"
SCHEDULER_NAME="${SCHEDULER_NAME:-gcs-metrics-daily}"
SCHEDULE="${SCHEDULE:-0 2 * * *}"  # Daily at 2 AM
TIMEZONE="${TIMEZONE:-America/Los_Angeles}"

echo "Creating Cloud Scheduler for project: $PROJECT_ID"
echo "Schedule: $SCHEDULE ($TIMEZONE)"
echo "Job: $JOB_NAME"
echo "Service Account: $SERVICE_ACCOUNT"
echo ""

# Create or update scheduler using HTTP target (Cloud Run Jobs API)
# This is a bit confusing - Cloud Scheduler makes an HTTP post to the Cloud Run Jobs API (not our container).
# This runs the job.  The container itself does not have an HTTP server.
echo "Creating Cloud Scheduler job..."
gcloud scheduler jobs create http "$SCHEDULER_NAME" \
  --project="$PROJECT_ID" \
  --location="$REGION" \
  --schedule="$SCHEDULE" \
  --time-zone="$TIMEZONE" \
  --max-retry-attempts=1 \
  --uri="https://$REGION-run.googleapis.com/apis/run.googleapis.com/v1/namespaces/$PROJECT_ID/jobs/$JOB_NAME:run" \
  --http-method=POST \
  --oauth-service-account-email="$SERVICE_ACCOUNT" \
  --description="Triggers GCS storage metrics collection daily" \
  2>&1 | grep -v "already exists" || echo "Scheduler job already exists"

echo ""
echo "✓ Cloud Scheduler created successfully!"
echo "  Name: $SCHEDULER_NAME"
echo "  Schedule: $SCHEDULE ($TIMEZONE)"
echo ""
echo "Test the scheduler with:"
echo "  gcloud scheduler jobs run $SCHEDULER_NAME --project=$PROJECT_ID --location=$REGION"