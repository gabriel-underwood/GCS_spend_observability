-- BigQuery table schema for GCS storage metrics
-- This table stores snapshots of GCS bucket storage metrics over time

CREATE TABLE IF NOT EXISTS gcs_storage_costs.bucket_snapshots (
  observed_at TIMESTAMP OPTIONS(description="Timestamp when the metric was observed"),
  region STRING OPTIONS(description="GCS bucket location/region"),
  gcp_project STRING OPTIONS(description="GCP project ID that owns the bucket"),
  bucket_name STRING OPTIONS(description="Name of the GCS bucket"),
  storage_class STRING OPTIONS(description="Storage class (STANDARD, NEARLINE, COLDLINE, ARCHIVE)"),
  total_bytes FLOAT64 OPTIONS(description="Total bytes stored in the bucket")
)
PARTITION BY DATE(observed_at)
OPTIONS(
  description="GCS storage metrics snapshots collected from Cloud Monitoring",
  partition_expiration_days=null
);