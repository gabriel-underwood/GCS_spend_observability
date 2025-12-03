import com.google.cloud.ServiceOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MetricsCollectorTest {
    private MetricsCollector collector;

    @BeforeEach
    void setUp() {
        // Get default project from gcloud configuration
        String projectId = ServiceOptions.getDefaultProjectId();
        collector = new MetricsCollector(projectId);
    }

    @Test
    @DisplayName("Confirm we can connect to Metrics Explorer and get the storage metrics")
    void testCollectMetrics() {

        try {

            List<GcsTotalBytesMetric>  metrics = collector.collectMetrics();
            // If you have no buckets in your project or it's been less than 24 hours since you created the buckets,
            // there are no metrics to harvest and this test case will fail.
            // See https://docs.cloud.google.com/monitoring/api/metrics_gcp_p_z#gcp-storage
            // "This value is measured once per day, and there might be a delay after measuring before the value
            // becomes available in Cloud Monitoring."
            GcsTotalBytesMetric metric = metrics.getFirst();
            assertEquals(metric.gcpProjectId(),collector.getProjectId());
            assertTrue (metric.totalBytes() >0);


        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}

