


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BigQueryWriter.
 */
class BigQueryWriterTest {

    private BigQueryWriter writer;

    @BeforeEach
    void setUp() {
        writer = new BigQueryWriter("charged-atlas-465220-v8", "gcs_storage_costs", "bucket_snapshots");
    }



    @Test
    @DisplayName("Should create row data with correct field mappings")
    void testWriteMetricToBQ() {
        Instant now = Instant.now();
        GcsTotalBytesMetric metric = new GcsTotalBytesMetric(
                now,
                "us-central1",
                "my-project",
                "my-bucket",
                "STANDARD",
                1048576.0
        );


        List<GcsTotalBytesMetric> metrics = new ArrayList<>();
        metrics.add(metric);
        writer.writeMetrics(metrics);
    }

}

