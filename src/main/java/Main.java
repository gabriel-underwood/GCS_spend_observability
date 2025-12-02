import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main entry point for the GCS Storage Metrics Cloud Run service.
 * Uses Java's built-in HTTP server without external frameworks.
 */
public class Main {
    
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final String PROJECT_ID = System.getenv("GCP_PROJECT_ID");
    private static final String DATASET_ID = 
        System.getenv().getOrDefault("BQ_DATASET_ID", "gcs_storage_costs");
    private static final String TABLE_ID = 
        System.getenv().getOrDefault("BQ_TABLE_ID", "bucket_snapshots");
    private static final int PORT = Integer.parseInt(
        System.getenv().getOrDefault("PORT", "8080"));
    
    public static void main(String[] args) throws IOException {
        validateConfiguration();
        
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new MetricsHandler());
        server.setExecutor(null); // Use default executor
        server.start();
        
        LOGGER.info("Server started on port " + PORT);
        LOGGER.info("Project ID: " + PROJECT_ID);
        LOGGER.info("BigQuery Dataset: " + DATASET_ID);
        LOGGER.info("BigQuery Table: " + TABLE_ID);
    }
    
    private static void validateConfiguration() {
        if (PROJECT_ID == null || PROJECT_ID.isBlank()) {
            throw new IllegalStateException(
                "GCP_PROJECT_ID environment variable must be set");
        }
    }
    
    /**
     * HTTP request handler for metrics collection endpoint.
     */
    static class MetricsHandler implements HttpHandler {
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            
            // Cloud Scheduler uses POST, but accept GET for manual testing
            if (!method.equals("POST") && !method.equals("GET")) {
                sendResponse(exchange, 405, "Method not allowed");
                return;
            }
            
            try {
                processMetrics();
                sendResponse(exchange, 200, "Metrics processed successfully");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error processing metrics", e);
                sendResponse(exchange, 500, "Error: " + e.getMessage());
            }
        }
        
        private void processMetrics() throws Exception {
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
        
        private void sendResponse(HttpExchange exchange, int statusCode, 
                                 String message) throws IOException {
            byte[] response = message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(statusCode, response.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}
