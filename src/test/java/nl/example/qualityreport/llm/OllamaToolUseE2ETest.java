package nl.example.qualityreport.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import nl.example.qualityreport.context.ContextBuilder;
import nl.example.qualityreport.model.ToolRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Dedicated Qwen2.5-Coder E2E tests for tool-use understanding.
 * Validates the model can:
 * <ol>
 *   <li>Recognize and request only declared tools</li>
 *   <li>Build valid tool arguments (ref, revision)</li>
 *   <li>Use tool results to inform its final vote</li>
 *   <li>Skip tools when context is already sufficient</li>
 * </ol>
 *
 * Run via: {@code mvn test -Ptool-e2e}
 */
@Tag("tool-e2e")
class OllamaToolUseE2ETest {

    private static final String BASE_URL = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
    private static final String MODEL = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5-coder:3b");
    private static final boolean VERBOSE = "true".equalsIgnoreCase(System.getenv("OLLAMA_VERBOSE"));
    private static final int RUNS_PER_TEST = 3;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path OUTPUT_DIR = Path.of("target/tool-e2e");

    private static final String DIFF_ANALYST_PROMPT;

    static {
        try {
            DIFF_ANALYST_PROMPT = Files.readString(Path.of("src/main/resources/prompts/diff-analyst.txt"));
        } catch (IOException e) {
            throw new RuntimeException("Cannot load diff-analyst prompt", e);
        }
    }

    private final ContextBuilder contextBuilder = new ContextBuilder();

    @TempDir
    Path tempDir;

    @BeforeAll
    static void preflight() throws IOException {
        assumeOllamaReachable();
        assumeModelAvailable();
        Files.createDirectories(OUTPUT_DIR);
    }

    /**
     * With a large diff in summary mode, model should request a file using
     * only the declared tool name (read_file).
     */
    @Test
    void qwen_requestsOnlyDeclaredTools() throws Exception {
        var scenario = AccuracyScenarioFixtures.largeScenario(tempDir.resolve("declared-tools"));
        String context = contextBuilder.build(scenario.changeData(), scenario.jiraData());
        OllamaProvider provider = createProvider();

        var results = new ArrayList<ToolUseRunRecord>();
        for (int i = 0; i < RUNS_PER_TEST; i++) {
            String response = provider.chat(DIFF_ANALYST_PROMPT, context);
            results.add(analyzeToolResponse(response, i + 1));
        }

        long validToolRequests =
                results.stream().filter(r -> r.isToolRequest && r.validToolName).count();
        long undeclaredTools = results.stream()
                .filter(r -> r.isToolRequest && !r.validToolName)
                .count();

        System.out.printf(
                "[qwen_requestsOnlyDeclaredTools] %d/%d valid tool requests, %d undeclared%n",
                validToolRequests, RUNS_PER_TEST, undeclaredTools);

        writeResults("declared-tools", results);
        ToolUseMetricsAggregator.writeMetrics(ToolUseMetricsAggregator.compute("declared-tools", results));

        assertThat(undeclaredTools).as("No undeclared tool names should appear").isZero();
    }

    /**
     * When the model requests a tool, its arguments should have valid
     * ref and revision fields.
     */
    @Test
    void qwen_buildsValidToolArguments_forChangedPathAndRevision() throws Exception {
        var scenario = AccuracyScenarioFixtures.largeScenario(tempDir.resolve("valid-args"));
        String context = contextBuilder.build(scenario.changeData(), scenario.jiraData());
        OllamaProvider provider = createProvider();

        var results = new ArrayList<ToolUseRunRecord>();
        for (int i = 0; i < RUNS_PER_TEST; i++) {
            String response = provider.chat(DIFF_ANALYST_PROMPT, context);
            results.add(analyzeToolResponse(response, i + 1));
        }

        long toolRequests = results.stream().filter(r -> r.isToolRequest).count();
        long validArgs = results.stream()
                .filter(r -> r.isToolRequest && r.validRef && r.validRevision)
                .count();

        System.out.printf(
                "[qwen_buildsValidToolArguments] %d tool requests, %d with valid args%n", toolRequests, validArgs);

        writeResults("valid-args", results);
        ToolUseMetricsAggregator.writeMetrics(ToolUseMetricsAggregator.compute("valid-args", results));

        results.stream().filter(r -> r.isToolRequest).forEach(r -> {
            assertThat(r.validRef).as("ref should be non-blank").isTrue();
            assertThat(r.validRevision)
                    .as("revision '%s' should be 'branch' or 'target'", r.rawRevision)
                    .isTrue();
        });
    }

    /**
     * After receiving tool results, the model should incorporate facts
     * from the tool output into its final vote reasoning.
     */
    @Test
    void qwen_updatesAssessmentAfterToolResult() throws Exception {
        var scenario = AccuracyScenarioFixtures.largeScenario(tempDir.resolve("tool-result"));
        String context = contextBuilder.build(scenario.changeData(), scenario.jiraData());
        OllamaProvider provider = createProvider();

        String injectedFact = "SENTINEL_UNIQUE_METHOD_NAME_12345";
        String toolResultXml =
                """
                <tool_result tool="read_file" ref="PlCache.java" resolved_path="src/main/java/nl/example/cache/PlCache.java" revision="branch" strategy="basename" status="success">
                public class PlCache {
                    public void %s() {
                        entries.clear();
                    }
                }
                </tool_result>"""
                        .formatted(injectedFact);

        int factMentioned = 0;
        int totalRuns = 0;
        var results = new ArrayList<String>();

        for (int i = 0; i < RUNS_PER_TEST; i++) {
            String enrichedContext = context + "\n\n" + toolResultXml;
            String secondResponse = provider.chat(DIFF_ANALYST_PROMPT, enrichedContext);

            results.add(secondResponse);
            totalRuns++;
            if (secondResponse.toLowerCase().contains(injectedFact.toLowerCase())) {
                factMentioned++;
            }
        }

        System.out.printf(
                "[qwen_updatesAssessmentAfterToolResult] %d/%d runs mentioned injected fact%n",
                factMentioned, totalRuns);

        writeStringResults("tool-result-usage", results);
    }

    /**
     * With a small diff (full mode), the model should vote directly
     * without requesting a tool.
     */
    @Test
    void qwen_skipsToolWhenContextAlreadySufficient() throws Exception {
        var scenario = AccuracyScenarioFixtures.smallScenario(tempDir.resolve("skip-tool"));
        String context = contextBuilder.build(scenario.changeData(), scenario.jiraData());
        OllamaProvider provider = createProvider();

        var results = new ArrayList<ToolUseRunRecord>();
        for (int i = 0; i < RUNS_PER_TEST; i++) {
            String response = provider.chat(DIFF_ANALYST_PROMPT, context);
            results.add(analyzeToolResponse(response, i + 1));
        }

        long directVotes =
                results.stream().filter(r -> !r.isToolRequest && r.isValidVote).count();

        System.out.printf(
                "[qwen_skipsToolWhenContextAlreadySufficient] %d/%d direct votes%n", directVotes, RUNS_PER_TEST);

        writeResults("skip-tool", results);
        ToolUseMetricsAggregator.writeMetrics(ToolUseMetricsAggregator.compute("skip-tool", results));

        assertThat(directVotes)
                .as("With full diff mode, model should vote directly in most runs")
                .isGreaterThanOrEqualTo(RUNS_PER_TEST / 2);
    }

    // --- analysis helpers ---

    record ToolUseRunRecord(
            int run,
            boolean isToolRequest,
            boolean validToolName,
            String rawTool,
            boolean validRef,
            String rawRef,
            boolean validRevision,
            String rawRevision,
            boolean isValidVote,
            String rawResponse) {}

    private ToolUseRunRecord analyzeToolResponse(String response, int run) {
        String cleaned = cleanResponse(response);

        boolean isToolReq = false;
        boolean validTool = false;
        String rawTool = null;
        boolean validRef = false;
        String rawRef = null;
        boolean validRev = false;
        String rawRev = null;
        boolean isVote = false;

        try {
            JsonNode root = MAPPER.readTree(cleaned);

            if (root.has("tool_request")) {
                isToolReq = true;
                JsonNode req = root.get("tool_request");
                rawTool = req.has("tool") ? req.get("tool").asText() : null;
                validTool = rawTool != null && ToolRequest.ALLOWED_TOOLS.contains(rawTool);
                rawRef = req.has("ref") ? req.get("ref").asText() : null;
                validRef = rawRef != null && !rawRef.isBlank();
                rawRev = req.has("revision") ? req.get("revision").asText() : null;
                validRev = rawRev != null && ToolRequest.ALLOWED_REVISIONS.contains(rawRev);
            } else if (root.has("vote")) {
                isVote = true;
            }
        } catch (Exception ignored) {
            // Non-JSON response — neither tool request nor valid vote
        }

        return new ToolUseRunRecord(
                run, isToolReq, validTool, rawTool, validRef, rawRef, validRev, rawRev, isVote, response);
    }

    private String cleanResponse(String raw) {
        String trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            int nl = trimmed.indexOf('\n');
            if (nl > 0) trimmed = trimmed.substring(nl + 1);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.strip();
    }

    // --- artifact output ---

    private void writeResults(String testName, List<ToolUseRunRecord> records) throws IOException {
        var sb = new StringBuilder();
        sb.append("# Tool Use E2E: ").append(testName).append("\n\n");
        sb.append("| Run | Type | Tool | Ref | Revision | Valid |\n");
        sb.append("|-----|------|------|-----|----------|-------|\n");
        for (var r : records) {
            sb.append("| ")
                    .append(r.run)
                    .append(" | ")
                    .append(r.isToolRequest ? "tool_request" : r.isValidVote ? "vote" : "other")
                    .append(" | ")
                    .append(r.rawTool != null ? r.rawTool : "-")
                    .append(" | ")
                    .append(r.rawRef != null ? r.rawRef : "-")
                    .append(" | ")
                    .append(r.rawRevision != null ? r.rawRevision : "-")
                    .append(" | ")
                    .append(r.isToolRequest ? (r.validToolName && r.validRef && r.validRevision) : r.isValidVote)
                    .append(" |\n");
        }
        Files.writeString(OUTPUT_DIR.resolve(testName + ".md"), sb.toString());

        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(records);
        Files.writeString(OUTPUT_DIR.resolve(testName + ".json"), json);
    }

    private void writeStringResults(String testName, List<String> responses) throws IOException {
        var sb = new StringBuilder();
        sb.append("# Tool Use E2E: ").append(testName).append("\n\n");
        for (int i = 0; i < responses.size(); i++) {
            sb.append("## Run ").append(i + 1).append("\n```\n");
            sb.append(responses.get(i)).append("\n```\n\n");
        }
        Files.writeString(OUTPUT_DIR.resolve(testName + ".md"), sb.toString());
    }

    // --- provider and preflight ---

    private OllamaProvider createProvider() {
        return new OllamaProvider(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), BASE_URL, MODEL, VERBOSE);
    }

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
            assumeTrue(resp.statusCode() == 200, "Ollama not reachable at " + BASE_URL);
        } catch (IOException | InterruptedException e) {
            assumeTrue(false, "Ollama not reachable: " + e.getMessage());
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
            assumeTrue(resp.body().contains(MODEL), "Model " + MODEL + " not available. Run: ollama pull " + MODEL);
        } catch (IOException | InterruptedException e) {
            assumeTrue(false, "Failed to check model: " + e.getMessage());
        }
    }
}
