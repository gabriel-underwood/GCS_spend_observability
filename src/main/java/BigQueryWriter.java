import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryError;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.TableId;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Writes GCS metrics to BigQuery.
 */
public class BigQueryWriter {
    
    private static final Logger LOGGER = Logger.getLogger(BigQueryWriter.class.getName());
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);
    
    private final String projectId;
    private final String datasetId;
    private final String tableId;
    
    public BigQueryWriter(String projectId, String datasetId, String tableId) {
        this.projectId = projectId;
        this.datasetId = datasetId;
        this.tableId = tableId;
    }
    
    /**
     * Writes metrics to BigQuery table.
     *
     * @param metrics List of metrics to write
     * @throws RuntimeException if insert fails
     */
    public void writeMetrics(List<GcsTotalBytesMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            LOGGER.info("No rows to insert");
            return;
        }
        
        BigQuery bigquery = BigQueryOptions.newBuilder()
            .setProjectId(projectId)
            .build()
            .getService();
        
        TableId table = TableId.of(projectId, datasetId, tableId);
        
        // Build insert request
        InsertAllRequest.Builder requestBuilder = InsertAllRequest.newBuilder(table);
        
        for (GcsTotalBytesMetric metric : metrics) {
            Map<String, Object> row = createRowData(metric);
            requestBuilder.addRow(row);
        }
        
        // Execute insert
        InsertAllResponse response = bigquery.insertAll(requestBuilder.build());
        
        // Check for errors
        if (response.hasErrors()) {
            StringBuilder errorMessage = new StringBuilder("BigQuery insert errors:\n");
            
            for (Map.Entry<Long, List<BigQueryError>> entry : 
                    response.getInsertErrors().entrySet()) {
                errorMessage.append("Row ").append(entry.getKey()).append(": ");
                for (BigQueryError error : entry.getValue()) {
                    errorMessage.append(error.getMessage()).append("; ");
                }
                errorMessage.append("\n");
            }
            
            throw new RuntimeException(errorMessage.toString());
        }
        
        LOGGER.info("Successfully inserted " + metrics.size() + " rows into " 
            + table.getDataset() + "." + table.getTable());
    }
    
    Map<String, Object> createRowData(GcsTotalBytesMetric metric) {
        Map<String, Object> row = new HashMap<>();
        
        // Format timestamp for BigQuery (TIMESTAMP type expects string format)
        row.put("observed_at", TIMESTAMP_FORMATTER.format(metric.observedAt()));
        row.put("region", metric.region());
        row.put("gcp_project", metric.gcpProjectId());
        row.put("bucket_name", metric.bucketName());
        row.put("storage_class", metric.storageClass());
        row.put("total_bytes", metric.totalBytes());
        
        return row;
    }
}
