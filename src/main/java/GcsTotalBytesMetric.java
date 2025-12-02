import java.time.Instant;

/**
 * Represents the GCS Bucket Total Bytes  metric.
 * storage.googleapis.com/storage/v2/total_bytes
 * Using Java record for immutable data model.
 *
 * @param observedAt    Timestamp when the metric was observed
 * @param region        GCS bucket region/location
 * @param gcpProjectId    GCP project ID
 * @param bucketName    Name of the GCS bucket
 * @param storageClass  Storage class (STANDARD, NEARLINE, etc.)
 * @param storageType   Storage type
 * @param totalBytes    Total bytes stored
 */
public record GcsTotalBytesMetric(
    Instant observedAt,
    String region,
    String gcpProjectId,
    String bucketName,
    String storageClass,
    double totalBytes
) {
    
    /**
     * Compact constructor with validation.
     */
    public GcsTotalBytesMetric {
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt cannot be null");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("region cannot be null or empty");
        }
        if (gcpProjectId == null || gcpProjectId.isBlank()) {
            throw new IllegalArgumentException("gcpProjectId cannot be null or empty");
        }
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalArgumentException("bucketName cannot be null or empty");
        }
        if (storageClass == null || storageClass.isBlank()) {
            throw new IllegalArgumentException("storageClass cannot be null or empty");
        }
        if (totalBytes < 0) {
            throw new IllegalArgumentException("totalBytes cannot be negative");
        }
    }
    
    /**
     * Returns a human-readable string representation.
     */
    @Override
    public String toString() {
        return String.format(
            "GcsMetric[gcpProjectId=%s bucket=%s, region=%s, class=%s, bytes=%.2f, time=%s]",
            gcpProjectId, bucketName, region, storageClass, totalBytes, observedAt
        );
    }
}
