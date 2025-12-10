# GCS Storage Metrics Collector

A Cloud Run Job that collects Google Cloud Storage (GCS) bucket metrics from Cloud Monitoring and writes them to BigQuery for cost analysis and observability.

## Background
Prior to 2024, getting Monitoring metrics was the only way to get any level of reporting on the size (and cost) of GCS buckets within a single Project.
If you needed to dig into where the spend was going on GCS Storage, you could not get per-bucket cost information from the billing data. 

Note that since Feb 2024, GCP Billing now puts the bucket name as a resource identifier in the billing data: 
https://docs.cloud.google.com/billing/docs/release-notes#February_13_2024

They didn't used to,  so at my previous role I had somebody set up a scraper like this.
I put this Cloud Run job together as a toy programming project to play with Claude Code.  But this is obsolete.


This  metrics scraper follows a Google reference architecture: 
https://docs.cloud.google.com/architecture/monitoring-metric-export?hl=en

and inspiration from this article from DoiT International:
https://engineering.doit.com/exporting-google-cloud-monitoring-data-to-bigquery-seamlessly-260459e895ce



## Overview



This service:
- Collects `storage.googleapis.com/storage/v2/total_bytes` metrics from Cloud Monitoring
- Extracts bucket metadata: bucket_name, location, storage_class
- Writes timestamped snapshots to BigQuery for analysis
- Runs as a Cloud Run Job (scheduled or on-demand)

## Prerequisites

- Google Cloud Project with billing enabled
- `gcloud` CLI installed and configured
- Required APIs enabled:
  ```bash
  gcloud services enable \
    run.googleapis.com \
    cloudbuild.googleapis.com \
    cloudscheduler.googleapis.com \
    monitoring.googleapis.com \
    bigquery.googleapis.com
  ```
- Java 21+ (for local development)
- Maven 3.8+ (for local development)

## Deployment


### Step 0: Authenticate to GCloud locally 
```bash
gcloud auth login
```

### Step 1: Set Project ID. 
The env var needs to be set for the deployment scripts.
To run the JUnit tests locally in an IDE, also set the project locally with gcloud. 
Use the project ID, not the project name. 




```bash
export GCP_PROJECT_ID="your-project-id"
```

### Step 2:  Create Service Account

Set up a dedicated service account for the Cloud Run job to use (instead of running under your user account):

```bash
gcloud iam service-accounts create gcs-metrics-sa \
  --display-name="GCS Metrics Collector" \
  --project="$GCP_PROJECT_ID"

export SERVICE_ACCOUNT="gcs-metrics-sa@$GCP_PROJECT_ID.iam.gserviceaccount.com"
```



### Step 3: Configure Permissions

```bash
./scripts/setup-permissions.sh
```

This grants the service account:
- `monitoring.viewer` - Read Cloud Monitoring metrics
- `bigquery.dataEditor` - Write to BigQuery tables
- `bigquery.jobUser` - Execute BigQuery jobs

### Step 4: Create BigQuery Dataset and Table. 

```bash
./scripts/setup-bigquery.sh
```

Creates the dataset  `your-project-id.gcs_storage_costs` 
and the partitioned table  `your-project-id.gcs_storage_costs.bucket_snapshots`  to store the metrics.


### Step 5: Deploy Cloud Run Job

```bash
./scripts/deploy-cloud-run-job.sh
```

This:
- Builds Docker image using Cloud Build
- Deploys to Cloud Run Jobs with appropriate configuration
- Sets environment variables

### Step 6: Set Up Scheduled Execution

```bash
./scripts/create-scheduler.sh
```

Creates a Cloud Scheduler job that runs daily at 2 AM.

### Step 7: Test the Job

Run manually to verify:
```bash
gcloud run jobs execute gcs-metrics-collector \
  --project="$GCP_PROJECT_ID" \
  --region=us-central1
```

## Configuration

All scripts support environment variable overrides:

| Variable | Default | Description |
|----------|---------|-------------|
| `GCP_PROJECT_ID` | *(required)* | GCP project ID |
| `BQ_DATASET_ID` | `gcs_storage_costs` | BigQuery dataset name |
| `BQ_TABLE_ID` | `bucket_snapshots` | BigQuery table name |
| `BQ_LOCATION` | `US` | BigQuery dataset location |
| `CLOUD_RUN_REGION` | `us-central1` | Cloud Run region |
| `JOB_NAME` | `gcs-metrics-collector` | Cloud Run Job name |
| `SCHEDULE` | `0 2 * * *` | Cron schedule (2 AM daily) |
| `TIMEZONE` | `America/New_York` | Scheduler timezone |

## Local Development

Set the project ID in GCloud  if you want to run the tests from an IDE
```bash
gcloud config set project "your-project-id"
```


### Build and Test

```bash
# Compile
mvn compile

# Run tests
mvn test

# Build uber JAR
mvn package

# Run locally (requires GCP credentials)
export GCP_PROJECT_ID="your-project-id"
java -jar target/gcs-storage-metrics-1.0.0.jar
```

### Run with Local Credentials

```bash
# Authenticate with your user account
gcloud auth application-default login

# Set project
export GCP_PROJECT_ID="your-project-id"

# Run
java -jar target/gcs-storage-metrics-1.0.0.jar
```

## Architecture

- **Main.java** - Entry point for Cloud Run Job
- **MetricsCollector.java** - Queries Cloud Monitoring API
- **BigQueryWriter.java** - Writes metrics to BigQuery
- **GcsTotalBytesMetric.java** - Data model (Java record)

### Data Flow

1. Cloud Scheduler triggers Cloud Run Job (or manual execution)
2. Job queries Cloud Monitoring for last 24 hours of GCS metrics
3. Extracts most recent data point per bucket
4. Writes to BigQuery with timestamp
5. Job exits with status code

## Querying Data

Example BigQuery queries:

```sql
-- Latest snapshot per bucket
SELECT
  bucket_name,
  storage_class,
  region,
  total_bytes / POW(1024, 3) AS total_gb,
  observed_at
FROM `your-project.gcs_storage_costs.bucket_snapshots`
WHERE observed_at = (SELECT MAX(observed_at) FROM `your-project.gcs_storage_costs.bucket_snapshots`)
ORDER BY total_bytes DESC;

-- Storage growth over time
SELECT
  DATE(observed_at) AS date,
  bucket_name,
  AVG(total_bytes) / POW(1024, 3) AS avg_gb
FROM `your-project.gcs_storage_costs.bucket_snapshots`
GROUP BY date, bucket_name
ORDER BY date DESC, avg_gb DESC;
```

## Troubleshooting

### View Job Logs

```bash
gcloud logging read \
  "resource.type=cloud_run_job AND resource.labels.job_name=gcs-metrics-collector" \
  --project="$GCP_PROJECT_ID" \
  --limit=50 \
  --format=json
```

### Check Job Execution History

```bash
gcloud run jobs executions list \
  --job=gcs-metrics-collector \
  --project="$GCP_PROJECT_ID" \
  --region=us-central1
```

### Common Issues

- **No metrics found**: Ensure GCS buckets exist and have storage data. 
 If you just created the first bucket in the project, you need to wait a day before the metric is available.
- **Permission denied**: Run `./scripts/setup-permissions.sh` again
- **Table not found**: Run `./scripts/setup-bigquery.sh` to create table

## License

MIT
