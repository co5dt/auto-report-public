package nl.example.qualityreport.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OllamaProviderTest {

    private HttpServer server;
    private String baseUrl;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port;
        httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void extractsContentFromValidResponse() {
        serveJson(
                200, """
                {"message":{"role":"assistant","content":"Hello from Ollama"},"done":true}""");

        var provider = new OllamaProvider(httpClient, baseUrl, "test-model");
        String result = provider.chat("system", "hello");

        assertThat(result).isEqualTo("Hello from Ollama");
    }

    @Test
    void throwsOnNon200Status() {
        serveJson(500, """
                {"error":"model not found"}""");

        var provider = new OllamaProvider(httpClient, baseUrl, "test-model");

        assertThatThrownBy(() -> provider.chat("sys", "msg"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageContaining("model not found");
    }

    @Test
    void throwsOnMissingMessageField() {
        serveJson(200, """
                {"done":true}""");

        var provider = new OllamaProvider(httpClient, baseUrl, "test-model");

        assertThatThrownBy(() -> provider.chat("sys", "msg"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("missing 'message' field");
    }

    @Test
    void throwsOnEmptyContent() {
        serveJson(200, """
                {"message":{"role":"assistant","content":""},"done":true}""");

        var provider = new OllamaProvider(httpClient, baseUrl, "test-model");

        assertThatThrownBy(() -> provider.chat("sys", "msg"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("empty content");
    }

    @Test
    void throwsOnMalformedJson() {
        serveJson(200, "not json at all");

        var provider = new OllamaProvider(httpClient, baseUrl, "test-model");

        assertThatThrownBy(() -> provider.chat("sys", "msg"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void throwsOnConnectionRefused() {
        server.stop(0);
        var provider = new OllamaProvider(httpClient, "http://localhost:1", "test-model");

        assertThatThrownBy(() -> provider.chat("sys", "msg"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("failed");
    }

    @Test
    void usesCorrectDefaults() {
        assertThat(OllamaProvider.DEFAULT_BASE_URL).isEqualTo("http://localhost:11434");
        assertThat(OllamaProvider.DEFAULT_MODEL).isEqualTo("qwen2.5-coder:3b");

        OllamaSamplingConfig defaults = OllamaSamplingConfig.GLOBAL_DEFAULTS;
        assertThat(defaults.numCtx()).isEqualTo(32768);
        assertThat(defaults.repeatPenalty()).isEqualTo(1.1);
        assertThat(defaults.repeatLastN()).isEqualTo(64);
        assertThat(defaults.temperature()).isEqualTo(0.7);
        assertThat(defaults.topK()).isEqualTo(40);
        assertThat(defaults.topP()).isEqualTo(0.9);
    }

    @Test
    void modelProfileOverridesDefaults() {
        serveJson(200, """
                {"message":{"role":"assistant","content":"ok"},"done":true}""");

        var provider = new OllamaProvider(httpClient, baseUrl, "deepcoder:1.5b");
        OllamaSamplingConfig config = provider.samplingConfig();
        assertThat(config.temperature()).isEqualTo(0.3);
        assertThat(config.repeatPenalty()).isEqualTo(1.3);
        assertThat(config.repeatLastN()).isEqualTo(128);
    }

    @Test
    void unknownModelUsesGlobalDefaults() {
        serveJson(200, """
                {"message":{"role":"assistant","content":"ok"},"done":true}""");

        var provider = new OllamaProvider(httpClient, baseUrl, "qwen2.5-coder:7b");
        OllamaSamplingConfig config = provider.samplingConfig();
        assertThat(config).isEqualTo(OllamaSamplingConfig.GLOBAL_DEFAULTS);
    }

    @Test
    void samplingOptionsAreIncludedInRequest() throws Exception {
        var capturedBody = new AtomicReference<String>();
        server.createContext("/api/chat", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes()));
            byte[] resp =
                    """
                    {"message":{"role":"assistant","content":"ok"},"done":true}""".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();

        var provider = new OllamaProvider(httpClient, baseUrl, "test-model");
        provider.chat("sys", "msg");

        OllamaSamplingConfig defaults = OllamaSamplingConfig.GLOBAL_DEFAULTS;
        ObjectMapper mapper = new ObjectMapper();
        JsonNode options = mapper.readTree(capturedBody.get()).path("options");
        assertThat(options.path("repeat_penalty").asDouble()).isEqualTo(defaults.repeatPenalty());
        assertThat(options.path("repeat_last_n").asInt()).isEqualTo(defaults.repeatLastN());
        assertThat(options.path("temperature").asDouble()).isEqualTo(defaults.temperature());
        assertThat(options.path("top_k").asInt()).isEqualTo(defaults.topK());
        assertThat(options.path("top_p").asDouble()).isEqualTo(defaults.topP());
    }

    @Test
    void handlesMultilineContent() {
        serveJson(
                200,
                """
                {"message":{"role":"assistant","content":"line1\\nline2\\nline3"},"done":true}""");

        var provider = new OllamaProvider(httpClient, baseUrl, "test-model");
        String result = provider.chat("sys", "msg");

        assertThat(result).contains("line1\nline2\nline3");
    }

    @Test
    void handlesNullContentField() {
        serveJson(200, """
                {"message":{"role":"assistant","content":null},"done":true}""");

        var provider = new OllamaProvider(httpClient, baseUrl, "test-model");

        assertThatThrownBy(() -> provider.chat("sys", "msg"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("empty content");
    }

    // --- test helpers ---

    private void serveJson(int statusCode, String body) {
        server.createContext("/api/chat", exchange -> {
            byte[] responseBytes = body.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        server.start();
    }
}
