package nl.example.qualityreport.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import nl.example.qualityreport.context.ContextBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Accuracy-focused E2E evaluation of the configured Ollama model against realistic
 * quality report scenarios. Produces a feasibility report under
 * {@code target/accuracy-e2e/}.
 *
 * Run via: {@code mvn test -Paccuracy-e2e}
 */
@Tag("accuracy-e2e")
class OllamaAccuracyE2ETest {

    private static final String BASE_URL = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
    private static final String MODEL = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5-coder:3b");
    private static final boolean VERBOSE = "true".equalsIgnoreCase(System.getenv("OLLAMA_VERBOSE"));
    private static final int RUNS_PER_SCENARIO = 2;
    private static final Path OUTPUT_DIR = Path.of("target/accuracy-e2e");

    private static final String SYSTEM_PROMPT =
            """
            You are a senior quality engineer reviewing a software change for a Dutch municipality system.
            You will receive XML-tagged context containing change overview, Jira data, diff content,
            impact areas, test evidence, and deployment information.

            Analyze the change and respond with ONLY a JSON object (no markdown, no explanation outside the JSON).
            The JSON must have exactly these fields:

            {
              "change_summary": "2-3 sentence summary of what changed and why",
              "key_risks": ["list of identified technical and process risks"],
              "impacted_areas": ["list of system areas affected by this change"],
              "test_evidence_assessment": "assessment of test coverage adequacy",
              "deployment_risks": "assessment of deployment complexity and risks",
              "confidence": "HIGH, MEDIUM, or LOW — your confidence in this assessment"
            }

            Be precise. Only mention things supported by the provided context.
            Do not invent information not present in the context.
            Respond with the JSON object only.""";

    private final ContextBuilder contextBuilder = new ContextBuilder();

    @TempDir
    Path tempDir;

    @BeforeAll
    static void preflight() {
        assumeOllamaReachable();
        assumeModelAvailable();
    }

    @Test
    void evaluateSmallScenario() throws Exception {
        var scenario = AccuracyScenarioFixtures.smallScenario(tempDir.resolve("small"));
        List<AccuracyReportWriter.RunRecord> runs = evaluateScenario(scenario);

        assertThat(runs).hasSize(RUNS_PER_SCENARIO);
        assertThat(runs).allSatisfy(run -> assertThat(run.rawResponse()).isNotBlank());
    }

    @Test
    void evaluateLargeScenario() throws Exception {
        var scenario = AccuracyScenarioFixtures.largeScenario(tempDir.resolve("large"));
        List<AccuracyReportWriter.RunRecord> runs = evaluateScenario(scenario);

        assertThat(runs).hasSize(RUNS_PER_SCENARIO);
        assertThat(runs).allSatisfy(run -> assertThat(run.rawResponse()).isNotBlank());
    }

    @Test
    void generateFeasibilityReport() throws Exception {
        List<AccuracyReportWriter.RunRecord> allRuns = new ArrayList<>();

        var small = AccuracyScenarioFixtures.smallScenario(tempDir.resolve("small"));
        allRuns.addAll(evaluateScenario(small));

        var large = AccuracyScenarioFixtures.largeScenario(tempDir.resolve("large"));
        allRuns.addAll(evaluateScenario(large));

        AccuracyReportWriter.writeArtifacts(OUTPUT_DIR, MODEL, allRuns);

        String modelSlug = MODEL.replace(":", "-");
        assertThat(OUTPUT_DIR.resolve(modelSlug + "-summary.md")).exists();
        assertThat(OUTPUT_DIR.resolve(modelSlug + "-runs.json")).exists();

        System.out.println("\n=== ACCURACY E2E SUMMARY ===");
        for (AccuracyReportWriter.RunRecord run : allRuns) {
            System.out.printf(
                    "[%s run %d] %s%n",
                    run.scenario(), run.runIndex(), run.score().summaryLine());
        }
        System.out.println("Artifacts written to: " + OUTPUT_DIR.toAbsolutePath());
    }

    private List<AccuracyReportWriter.RunRecord> evaluateScenario(AccuracyScenarioFixtures.Scenario scenario)
            throws Exception {

        String xmlContext = contextBuilder.build(scenario.changeData(), scenario.jiraData());

        OllamaProvider provider = new OllamaProvider(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), BASE_URL, MODEL, VERBOSE);

        List<AccuracyReportWriter.RunRecord> runs = new ArrayList<>();
        for (int i = 1; i <= RUNS_PER_SCENARIO; i++) {
            long start = System.currentTimeMillis();
            String response = provider.chat(SYSTEM_PROMPT, xmlContext);
            long durationMs = System.currentTimeMillis() - start;

            AccuracyScorer.ScoreResult score = AccuracyScorer.score(response, scenario);
            runs.add(new AccuracyReportWriter.RunRecord(scenario.name(), i, durationMs, response, score));

            System.out.printf("[%s run %d] %dms — %s%n", scenario.name(), i, durationMs, score.summaryLine());
        }
        return runs;
    }

    // --- preflight ---

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
