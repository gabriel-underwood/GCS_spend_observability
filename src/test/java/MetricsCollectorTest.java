import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MetricsCollectorTest {
    private MetricsCollector collector;

   private final String testBucketName = "charged-atlas-465220-v8-example-bucket-1";

    @BeforeEach
    void setUp() {
        collector = new MetricsCollector("charged-atlas-465220-v8");
    }

    @Test
    @DisplayName("Confirm we can connect to Metrics Explorer and get the storage metrics")
    void testCollectMetrics() {

        try {

            List<GcsTotalBytesMetric>  metrics = collector.collectMetrics();
            assertTrue(!metrics.isEmpty());
            GcsTotalBytesMetric metric = metrics.get(0);
            assertEquals(metric.gcpProjectId(),collector.getProjectId());
            assertTrue (metric.totalBytes() >0);


        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}

