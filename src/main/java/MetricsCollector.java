import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.monitoring.v3.ListTimeSeriesRequest;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.protobuf.Timestamp;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Collects GCS storage metrics from Cloud Monitoring API.
 */
public class MetricsCollector {
    
    private static final Logger LOGGER = Logger.getLogger(MetricsCollector.class.getName());
    private static final String METRIC_TYPE = "storage.googleapis.com/storage/v2/total_bytes";
    private final String projectId;
    
    public MetricsCollector(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectId() { return projectId;}
    
    /**
     * Collects GCS storage metrics from Cloud Monitoring.
     *
     * @return List of GCS metrics
     * @throws IOException if API call fails
     */
    public List<GcsTotalBytesMetric> collectMetrics() throws IOException {
        List<GcsTotalBytesMetric> metrics = new ArrayList<>();
        
        try (MetricServiceClient client = MetricServiceClient.create()) {
            ProjectName projectName = ProjectName.of(projectId);
            
            //We want the last day
            Instant now = Instant.now();
            Instant dayAgo = now.minus(1, ChronoUnit.DAYS);
            
            TimeInterval interval = TimeInterval.newBuilder()
                .setEndTime(Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .build())
                .setStartTime(Timestamp.newBuilder()
                    .setSeconds(dayAgo.getEpochSecond())
                    .build())
                .build();
            
            // Build request
            ListTimeSeriesRequest request = ListTimeSeriesRequest.newBuilder()
                .setName(projectName.toString())
                .setFilter("metric.type=\"" + METRIC_TYPE + "\"")
                .setInterval(interval)
                .setView(ListTimeSeriesRequest.TimeSeriesView.FULL)
                .build();
            
            // Fetch and process time series
            MetricServiceClient.ListTimeSeriesPagedResponse response = 
                client.listTimeSeries(request);
            
            for (TimeSeries timeSeries : response.iterateAll()) {
                processTimeSeries(timeSeries, metrics);
            }
        }
        
        return metrics;
    }
    
    private void processTimeSeries(TimeSeries timeSeries, List<GcsTotalBytesMetric> metrics) {
        // Extract labels

        String projectId = timeSeries.getResource().getLabelsOrDefault("project_id", "unknown");
        String bucketName = timeSeries.getResource().getLabelsOrDefault("bucket_name", "unknown");
        String region = timeSeries.getResource().getLabelsOrDefault("location", "unknown");
        String storageClass = timeSeries.getMetric().getLabelsOrDefault("storage_class", "unknown");
        
        // Get the most recent point
        if (!timeSeries.getPointsList().isEmpty()) {
            Point point = timeSeries.getPoints(0); // Most recent point
            
            Instant observedAt = Instant.ofEpochSecond(
                point.getInterval().getEndTime().getSeconds(),
                point.getInterval().getEndTime().getNanos()
            );
            
            double totalBytes = point.getValue().getDoubleValue();

            
            GcsTotalBytesMetric metric = new GcsTotalBytesMetric(
                observedAt,
                region,
                projectId,
                bucketName,
                storageClass,
                totalBytes
            );
            
            metrics.add(metric);
        }
    }
}
