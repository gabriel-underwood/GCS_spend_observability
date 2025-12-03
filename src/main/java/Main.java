
import com.google.cloud.ServiceOptions;

import java.io.IOException;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main entry point for the GCS Storage Metrics Cloud Run service.
 * Modelled off the Google example code for a batch job at:
 * <a href="https://docs.cloud.google.com/run/docs/quickstarts/jobs/build-create-java">...</a>
 */
public class Main {
    
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    // For local testing in IDE, the default project Id is pulled from gcloud defaults
    private static final String PROJECT_ID = System.getenv().getOrDefault("GCP_PROJECT_ID", ServiceOptions.getDefaultProjectId());
    private static final String DATASET_ID = 
        System.getenv().getOrDefault("BQ_DATASET_ID", "gcs_storage_costs");
    private static final String TABLE_ID = 
        System.getenv().getOrDefault("BQ_TABLE_ID", "bucket_snapshots");

    // These values are provided automatically by the Cloud Run Jobs runtime.
    private static final String CLOUD_RUN_TASK_INDEX =
            System.getenv().getOrDefault("CLOUD_RUN_TASK_INDEX", "0");
    private static final String CLOUD_RUN_TASK_ATTEMPT =
            System.getenv().getOrDefault("CLOUD_RUN_TASK_ATTEMPT", "0");

    
    public static void main(String[] args) throws IOException {


        LOGGER.info(
                String.format(
                        "Starting Task #%s, Attempt #%s...", CLOUD_RUN_TASK_INDEX, CLOUD_RUN_TASK_ATTEMPT));
        LOGGER.info("Project ID: " + PROJECT_ID);
        LOGGER.info("BigQuery Dataset: " + DATASET_ID);
        LOGGER.info("BigQuery Table: " + TABLE_ID);

        try {
            validateConfiguration();
            processMetrics();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing metrics", e);
            LOGGER.log(Level.SEVERE,
                    String.format(
                            "Task #%s, Attempt #%s failed.", CLOUD_RUN_TASK_INDEX, CLOUD_RUN_TASK_ATTEMPT));
            // Catch error and denote process-level failure to retry Task
            System.exit(1);
        }

    }
    
    private static void validateConfiguration() {
        if (PROJECT_ID == null || PROJECT_ID.isBlank()) {
            throw new IllegalStateException(
                "GCP_PROJECT_ID environment variable must be set");
        }
    }
    

        
    private static void processMetrics() throws Exception {
        LOGGER.info("Starting GCS storage metrics collection for project: "
            + PROJECT_ID);

        // Fetch metrics from Cloud Monitoring
        MetricsCollector collector = new MetricsCollector(PROJECT_ID);
        List<GcsTotalBytesMetric> metrics = collector.collectMetrics();

        if (metrics.isEmpty()) {
            LOGGER.info("No metrics data found");
            return;
        }

        LOGGER.info("Retrieved " + metrics.size() + " metric records");

        // Write to BigQuery
        BigQueryWriter writer = new BigQueryWriter(PROJECT_ID, DATASET_ID, TABLE_ID);
        writer.writeMetrics(metrics);

        LOGGER.info("Successfully processed " + metrics.size() + " records");
    }
        


}
