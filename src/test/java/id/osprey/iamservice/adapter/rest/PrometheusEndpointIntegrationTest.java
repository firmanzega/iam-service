package id.osprey.iamservice.adapter.rest;

import id.osprey.iamservice.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the Prometheus endpoint can be scraped without a token and that the
 * exported metric names match the alert rules in ops/prometheus/alerts.yml.
 *
 * The test uses a real servlet container instead of MockMvc because
 * http_server_requests_* is recorded by the server observation filter, which
 * does not run in a mock web environment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "iam.rate-limit.max-requests=10000")
@Import(TestcontainersConfiguration.class)
class PrometheusEndpointIntegrationTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void scrape_exposesSecurityAndHttpMetricsWithoutToken() throws Exception {
        // A rejected request creates the custom counter. Without it the
        // security metric would not exist yet and the scrape would only prove
        // that JVM metrics are registered.
        assertEquals(401, get("/v1/users").statusCode());

        HttpResponse<String> scrape = get("/actuator/prometheus");

        assertEquals(200, scrape.statusCode());
        assertTrue(scrape.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"),
                "Prometheus expects a text exposition format");

        String body = scrape.body();
        assertTrue(body.contains("iam_security_rejected_total"),
                "security rejection counter must reach the registry");
        assertTrue(body.contains("reason=\"unauthorized\""),
                "the rejection reason tag is part of the alert contract");
        assertTrue(body.contains("http_server_requests_seconds_count"),
                "HTTP server metrics are referenced by the alert rules");
        assertTrue(body.contains("jvm_memory_used_bytes"),
                "JVM memory metrics back the heap alert");
        assertTrue(body.contains("hikaricp_connections_pending"),
                "the connection pool alert depends on this metric");
    }

    @Test
    void metricsEndpoint_stillRequiresAuthentication() throws Exception {
        assertEquals(401, get("/actuator/metrics").statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
