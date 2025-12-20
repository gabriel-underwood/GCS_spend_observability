# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GCS Storage Metrics Collector - A Cloud Run service that collects Google Cloud Storage (GCS) bucket metrics from Cloud Monitoring and writes them to BigQuery for cost analysis and observability.

## Build and Test Commands

```bash
# Compile the project
mvn compile

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=MetricsCollectorTest

# Run a single test method
mvn test -Dtest=MetricsCollectorTest#testCollectMetrics

# Package into uber JAR (includes all dependencies)
mvn package

# Clean and rebuild
mvn clean package

# Run the application locally (requires environment variables)
java -jar target/gcs-storage-metrics-1.0.0.jar
```

## Architecture

### Core Components

1. **Main.java** - Cloud Function entry point
   - No HTTP or event listening, just a main function
   - Invoked either standalone or as a Cloud Run scheduled job
   - Reads configuration from environment variables
   - Orchestrates the metrics collection pipeline

2. **MetricsCollector.java** - Cloud Monitoring integration
   - Fetches `storage.googleapis.com/storage/v2/total_bytes` metric
   - Queries last 24 hours of data
   - Extracts bucket metadata: bucket_name, location, storage_class
   - Returns the most recent data point for each time series

3. **BigQueryWriter.java** - BigQuery persistence
   - Uses streaming inserts to write metrics
   - Formats timestamps as `yyyy-MM-dd HH:mm:ss.SSSSSS` (BigQuery TIMESTAMP format)
   - Maps metric fields to BigQuery schema

4. **GcsTotalBytesMetric.java** - Data model
   - Immutable Java record with validation
   - Represents a single metric observation with: observedAt, region, gcpProjectId, bucketName, storageClass, totalBytes

### Data Flow

1. Cloud Run scheduler or standalone execution invokes main 
2.  main calls MetricsCollector.collectMetrics()
3. MetricsCollector queries Cloud Monitoring API for GCS storage metrics
4. BigQueryWriter.writeMetrics() inserts data into BigQuery table

### Technology Stack

- Java 25 with records and modern features
- Google Cloud client libraries (Monitoring, BigQuery)
- JUnit 5 for testing
- Maven for build/dependency management
- Maven Shade Plugin creates executable uber JAR

## Environment Configuration

Required environment variables:
- `GCP_PROJECT_ID` - GCP project ID (required)
- `BQ_DATASET_ID` - BigQuery dataset (default: "gcs_storage_costs")
- `BQ_TABLE_ID` - BigQuery table (default: "bucket_snapshots")


## Testing Strategy

Tests use real GCP credentials and project IDs for integration testing:
- `MetricsCollectorTest` - Validates Cloud Monitoring API integration
- `BigQueryWriterTest` - Tests BigQuery row data mapping

Tests are currently integration tests that connect to real GCP services.

## BigQuery Schema

Expected table schema for bucket_snapshots:
- `observed_at` - TIMESTAMP
- `region` - STRING
- `gcp_project` - STRING
- `bucket_name` - STRING
- `storage_class` - STRING
- `total_bytes` - FLOAT64

## Deployment

This service is designed to run on Google Cloud Run:
1. Build uber JAR with `mvn package`
2. Deploy JAR to Cloud Run with environment variables configured
3. Schedule with Cloud Scheduler to trigger job periodically