#!/bin/bash
set -e

# Configuration
PROJECT_ID="${GCP_PROJECT_ID:?Error: GCP_PROJECT_ID environment variable must be set}"
DATASET_ID="${BQ_DATASET_ID:-gcs_storage_costs}"
TABLE_ID="${BQ_TABLE_ID:-bucket_snapshots}"
LOCATION="${BQ_LOCATION:-US}"

echo "Setting up BigQuery for project: $PROJECT_ID"
echo "Dataset: $DATASET_ID"
echo "Table: $TABLE_ID"
echo "Location: $LOCATION"
echo ""

# Create dataset if it doesn't exist
echo "Creating dataset..."
bq mk --dataset \
  --location="$LOCATION" \
  --description="GCS storage cost metrics" \
  "$PROJECT_ID:$DATASET_ID" || echo "Dataset already exists"

# Create table using SQL file
echo "Creating metrics table..."
bq query \
  --project_id="$PROJECT_ID" \
  --use_legacy_sql=false \
  < "$(dirname "$0")/create-bucket_snapshots_table.sql"

echo ""
echo "✓ BigQuery setup complete!"
echo "  Dataset: $PROJECT_ID:$DATASET_ID"
echo "  Table: $PROJECT_ID:$DATASET_ID.$TABLE_ID"