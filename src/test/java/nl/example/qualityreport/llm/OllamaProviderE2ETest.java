package nl.example.qualityreport.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration test that exercises OllamaProvider against a real local Ollama instance.
 *
 * Gated by the {@code e2e} JUnit 5 tag — excluded from default {@code mvn test},
 * run via {@code mvn test -Pe2e}.
 *
 * Prerequisites:
 * <ul>
 *   <li>Ollama running locally (default: http://localhost:11434)</li>
 *   <li>Model pulled (default: {@code qwen2.5-coder:3b}, override with {@code OLLAMA_MODEL} env var)</li>
 * </ul>
 */
@Tag("e2e")
class OllamaProviderE2ETest {

    private static final String BASE_URL = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
    private static final String MODEL = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5-coder:3b");
    private static final boolean VERBOSE = "true".equalsIgnoreCase(System.getenv("OLLAMA_VERBOSE"));

    @BeforeAll
    static void preflight() {
        assumeOllamaReachable();
        assumeModelAvailable();
    }

    @Test
    void chatReturnsNonEmptyResponse() {
        var provider = new OllamaProvider(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), BASE_URL, MODEL, VERBOSE);

        String response = provider.chat("You are a helpful assistant. Reply in one short sentence.", "What is 2 + 2?");

        assertThat(response).isNotNull().isNotBlank().hasSizeGreaterThan(1);
    }

    @Test
    void chatHandlesCodeAnalysisPrompt() {
        var provider = new OllamaProvider(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), BASE_URL, MODEL, VERBOSE);

        String response = provider.chat(
                "You are a code reviewer. Respond with a brief assessment.",
                "Review this Java method:\n```java\npublic int add(int a, int b) { return a + b; }\n```");

        assertThat(response).isNotNull().isNotBlank();
    }

    // --- preflight helpers ---

    private static void assumeOllamaReachable() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/tags"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assumeTrue(
                    resp.statusCode() == 200,
                    "Ollama not reachable at " + BASE_URL + " (HTTP " + resp.statusCode() + ")");
        } catch (IOException | InterruptedException e) {
            assumeTrue(false, "Ollama not reachable at " + BASE_URL + ": " + e.getMessage());
        }
    }

    private static void assumeModelAvailable() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/tags"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            boolean modelPresent = resp.body().contains(MODEL);
            assumeTrue(modelPresent, "Model " + MODEL + " not available. Run: ollama pull " + MODEL);
        } catch (IOException | InterruptedException e) {
            assumeTrue(false, "Failed to check model availability: " + e.getMessage());
        }
    }
}
