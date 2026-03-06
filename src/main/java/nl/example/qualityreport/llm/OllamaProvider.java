package nl.example.qualityreport.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

public class OllamaProvider implements LlmProvider {

    static final String DEFAULT_BASE_URL = "http://localhost:11434";
    static final String DEFAULT_MODEL = "qwen2.5-coder:3b";
    static final Duration TIMEOUT = Duration.ofMinutes(5);
    static final int LOOP_CHECK_INTERVAL = 50;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String model;
    private final OllamaSamplingConfig sampling;
    private final boolean verbose;
    private final PrintStream verboseOut;

    public OllamaProvider() {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                resolveBaseUrl(),
                resolveModel(),
                0,
                resolveVerbose());
    }

    public OllamaProvider(String model) {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                resolveBaseUrl(),
                model,
                0,
                resolveVerbose());
    }

    public OllamaProvider(boolean verbose) {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                resolveBaseUrl(),
                resolveModel(),
                0,
                verbose);
    }

    public OllamaProvider(String model, boolean verbose) {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                resolveBaseUrl(),
                model,
                0,
                verbose);
    }

    public OllamaProvider(String model, int numCtx, boolean verbose) {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                resolveBaseUrl(),
                model != null ? model : resolveModel(),
                numCtx,
                verbose);
    }

    OllamaProvider(HttpClient httpClient, String baseUrl, String model) {
        this(httpClient, baseUrl, model, 0, false);
    }

    OllamaProvider(HttpClient httpClient, String baseUrl, String model, boolean verbose) {
        this(httpClient, baseUrl, model, 0, verbose);
    }

    OllamaProvider(HttpClient httpClient, String baseUrl, String model, int numCtx, boolean verbose) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.model = model;
        this.sampling = resolveSampling(model, numCtx);
        this.verbose = verbose;
        this.verboseOut = System.err;
    }

    private static OllamaSamplingConfig resolveSampling(String model, int numCtxOverride) {
        OllamaSamplingConfig profile = ModelSamplingProfiles.forModel(model);
        return profile.withOverrides(
                numCtxOverride > 0 ? numCtxOverride : resolveInt("OLLAMA_NUM_CTX", 0),
                resolveDouble("OLLAMA_REPEAT_PENALTY", 0),
                resolveInt("OLLAMA_REPEAT_LAST_N", 0),
                resolveDouble("OLLAMA_TEMPERATURE", 0),
                resolveInt("OLLAMA_TOP_K", 0),
                resolveDouble("OLLAMA_TOP_P", 0));
    }

    OllamaSamplingConfig samplingConfig() {
        return sampling;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        boolean stream = verbose;
        ObjectNode body = buildRequestBody(systemPrompt, userMessage, stream);

        if (verbose) {
            printRequest(body);
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (!stream) {
            reqBuilder.timeout(TIMEOUT);
        }
        HttpRequest request = reqBuilder.build();

        if (stream) {
            return chatStreaming(request);
        }
        return chatBlocking(request);
    }

    private String chatBlocking(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new LlmProviderException("Ollama request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmProviderException("Ollama request interrupted", e);
        }

        if (response.statusCode() != 200) {
            throw new LlmProviderException("Ollama returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return extractContent(response.body());
    }

    private String chatStreaming(HttpRequest request) {
        HttpResponse<java.io.InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new LlmProviderException("Ollama request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmProviderException("Ollama request interrupted", e);
        }

        if (response.statusCode() != 200) {
            String body;
            try {
                body = new String(response.body().readAllBytes());
            } catch (IOException e) {
                body = "(unreadable)";
            }
            throw new LlmProviderException("Ollama returned HTTP " + response.statusCode() + ": " + body);
        }

        var accumulated = new StringBuilder();
        var accumulatedThinking = new StringBuilder();
        long startMs = System.currentTimeMillis();
        boolean wasThinking = false;
        var loopDetector = new RepetitionDetector();
        int chunksSinceCheck = 0;
        int thinkingChunksSinceCheck = 0;

        verboseOut.println();
        verboseOut.println("--- response ---");

        try (var reader = new BufferedReader(new InputStreamReader(response.body()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode chunk = MAPPER.readTree(line);
                JsonNode message = chunk.path("message");

                String thinking = message.path("thinking").asText("");
                if (!thinking.isEmpty()) {
                    if (!wasThinking) {
                        verboseOut.print("[think] ");
                        wasThinking = true;
                    }
                    verboseOut.print(thinking);
                    verboseOut.flush();
                    accumulatedThinking.append(thinking);

                    thinkingChunksSinceCheck++;
                    if (thinkingChunksSinceCheck >= LOOP_CHECK_INTERVAL) {
                        thinkingChunksSinceCheck = 0;
                        String fragment = loopDetector.detectLoop(accumulatedThinking);
                        if (fragment != null) {
                            verboseOut.println();
                            verboseOut.println("[THINK-LOOP DETECTED] aborting stream after "
                                    + accumulatedThinking.length() + " thinking chars");
                            throw new LoopDetectedException(
                                    "Repetition loop in thinking tokens after " + accumulatedThinking.length()
                                            + " chars",
                                    accumulatedThinking.length(),
                                    fragment);
                        }
                    }
                }

                String content = message.path("content").asText("");
                if (!content.isEmpty()) {
                    if (wasThinking) {
                        verboseOut.println();
                        verboseOut.print("[answer] ");
                        wasThinking = false;
                    }
                    verboseOut.print(content);
                    verboseOut.flush();
                    accumulated.append(content);

                    chunksSinceCheck++;
                    if (chunksSinceCheck >= LOOP_CHECK_INTERVAL) {
                        chunksSinceCheck = 0;
                        String fragment = loopDetector.detectLoop(accumulated);
                        if (fragment != null) {
                            verboseOut.println();
                            verboseOut.println(
                                    "[LOOP DETECTED] aborting stream after " + accumulated.length() + " chars");
                            throw new LoopDetectedException(
                                    "Repetition loop detected after " + accumulated.length() + " chars",
                                    accumulated.length(),
                                    fragment);
                        }
                    }
                }

                if (chunk.path("done").asBoolean(false)) {
                    verboseOut.println();
                    printStats(chunk, startMs);
                }
            }
        } catch (LoopDetectedException e) {
            throw e;
        } catch (IOException e) {
            throw new LlmProviderException("Failed to read streaming response: " + e.getMessage(), e);
        }

        String result = accumulated.toString();
        result = stripInlineThinkBlocks(result);
        if (result.isEmpty()) {
            throw new LlmProviderException("Ollama streaming response produced no content");
        }
        return result;
    }

    private void printRequest(ObjectNode body) {
        verboseOut.println();
        verboseOut.println("=".repeat(60));
        verboseOut.println(">>> OLLAMA REQUEST");
        verboseOut.println("=".repeat(60));
        verboseOut.println("Model: " + body.path("model").asText());
        JsonNode opts = body.path("options");
        verboseOut.println("num_ctx: " + opts.path("num_ctx").asInt());
        verboseOut.println("repeat_penalty: " + opts.path("repeat_penalty").asDouble() + "  repeat_last_n: "
                + opts.path("repeat_last_n").asInt());
        verboseOut.println("temperature: " + opts.path("temperature").asDouble()
                + "  top_k: " + opts.path("top_k").asInt()
                + "  top_p: " + opts.path("top_p").asDouble());
        verboseOut.println("Stream: " + body.path("stream").asBoolean());
        verboseOut.println();

        JsonNode messages = body.path("messages");
        for (JsonNode msg : messages) {
            String role = msg.path("role").asText();
            String content = msg.path("content").asText();
            verboseOut.println("--- " + role + " ---");
            verboseOut.println(content);
            verboseOut.println();
        }
    }

    private void printStats(JsonNode finalChunk, long startMs) {
        long elapsedMs = System.currentTimeMillis() - startMs;

        verboseOut.println();
        verboseOut.println("--- stats ---");

        long promptEval = finalChunk.path("prompt_eval_count").asLong(0);
        long evalCount = finalChunk.path("eval_count").asLong(0);
        long evalDurationNs = finalChunk.path("eval_duration").asLong(0);
        long totalDurationNs = finalChunk.path("total_duration").asLong(0);

        double evalDurationSec = evalDurationNs / 1_000_000_000.0;
        double tokPerSec = evalDurationNs > 0 ? evalCount / evalDurationSec : 0;
        double totalSec = totalDurationNs > 0 ? totalDurationNs / 1_000_000_000.0 : elapsedMs / 1000.0;

        verboseOut.printf("prompt tokens: %d | response tokens: %d%n", promptEval, evalCount);
        verboseOut.printf("eval: %.1fs (%.1f tok/s) | total: %.1fs%n", evalDurationSec, tokPerSec, totalSec);
        verboseOut.println("=".repeat(60));
    }

    private ObjectNode buildRequestBody(String systemPrompt, String userMessage, boolean stream) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("stream", stream);

        ObjectNode options = body.putObject("options");
        options.put("num_ctx", sampling.numCtx());
        options.put("repeat_penalty", sampling.repeatPenalty());
        options.put("repeat_last_n", sampling.repeatLastN());
        options.put("temperature", sampling.temperature());
        options.put("top_k", sampling.topK());
        options.put("top_p", sampling.topP());

        ArrayNode messages = body.putArray("messages");

        ObjectNode sysMsg = messages.addObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        return body;
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode message = root.path("message");
            if (message.isMissingNode()) {
                throw new LlmProviderException("Ollama response missing 'message' field: " + responseBody);
            }
            JsonNode content = message.path("content");
            if (content.isMissingNode() || content.isNull() || content.asText().isEmpty()) {
                throw new LlmProviderException("Ollama response has empty content: " + responseBody);
            }
            return stripInlineThinkBlocks(content.asText());
        } catch (IOException e) {
            throw new LlmProviderException("Failed to parse Ollama response: " + responseBody, e);
        }
    }

    private static final Pattern THINK_BLOCK = Pattern.compile("<think>.*?</think>\\s*", Pattern.DOTALL);

    static String stripInlineThinkBlocks(String text) {
        if (text == null) return null;
        return THINK_BLOCK.matcher(text).replaceAll("").strip();
    }

    private static String resolveBaseUrl() {
        String env = System.getenv("OLLAMA_BASE_URL");
        return (env != null && !env.isBlank()) ? env : DEFAULT_BASE_URL;
    }

    private static String resolveModel() {
        String env = System.getenv("OLLAMA_MODEL");
        return (env != null && !env.isBlank()) ? env : DEFAULT_MODEL;
    }

    private static boolean resolveVerbose() {
        String env = System.getenv("OLLAMA_VERBOSE");
        return "true".equalsIgnoreCase(env);
    }

    private static double resolveDouble(String envVar, double defaultValue) {
        String env = System.getenv(envVar);
        if (env != null && !env.isBlank()) {
            try {
                return Double.parseDouble(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static int resolveInt(String envVar, int defaultValue) {
        String env = System.getenv(envVar);
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }
}
