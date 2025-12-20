#!/bin/bash
set -e

# Configuration
PROJECT_ID="${GCP_PROJECT_ID:?Error: GCP_PROJECT_ID environment variable must be set}"
REGION="${CLOUD_RUN_REGION:-us-central1}"
JOB_NAME="${JOB_NAME:-gcs-metrics-collector}"
DATASET_ID="${BQ_DATASET_ID:-gcs_storage_costs}"
TABLE_ID="${BQ_TABLE_ID:-bucket_snapshots}"

# Artifact Registry configuration
REPOSITORY="${ARTIFACT_REPOSITORY:-cloud-run-source-deploy}"
IMAGE_NAME="${IMAGE_NAME:-gcs-storage-metrics}"

echo "Deploying Cloud Run Job to project: $PROJECT_ID"
echo "Region: $REGION"
echo "Job Name: $JOB_NAME"
echo ""

# Create Artifact Registry repository if it doesn't exist
echo "Setting up Artifact Registry repository..."
gcloud artifacts repositories create "$REPOSITORY" \
  --project="$PROJECT_ID" \
  --repository-format=docker \
  --location="$REGION" \
  --description="Docker images for Cloud Run jobs" \
  2>/dev/null || echo "Repository already exists"

# Build and push image to Artifact Registry
echo "Building Docker image..."
IMAGE_URL="$REGION-docker.pkg.dev/$PROJECT_ID/$REPOSITORY/$IMAGE_NAME:latest"

gcloud builds submit \
  --project="$PROJECT_ID" \
  --region="$REGION" \
  --tag="$IMAGE_URL"

# Deploy Cloud Run Job
echo "Deploying Cloud Run Job..."
gcloud run jobs deploy "$JOB_NAME" \
  --project="$PROJECT_ID" \
  --region="$REGION" \
  --image="$IMAGE_URL" \
  --set-env-vars="GCP_PROJECT_ID=$PROJECT_ID,BQ_DATASET_ID=$DATASET_ID,BQ_TABLE_ID=$TABLE_ID" \
  --max-retries=3 \
  --task-timeout=10m \
  --memory=512Mi \
  --cpu=1

echo ""
echo "✓ Cloud Run Job deployed successfully!"
echo "  Job: $JOB_NAME"
echo "  Image: $IMAGE_URL"
echo ""
echo "Test the job with:"
echo "  gcloud run jobs execute $JOB_NAME --project=$PROJECT_ID --region=$REGION"