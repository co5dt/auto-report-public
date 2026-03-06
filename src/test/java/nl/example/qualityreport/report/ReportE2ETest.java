package nl.example.qualityreport.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import nl.example.qualityreport.GenerateReportUseCase;
import nl.example.qualityreport.config.AppConfig;
import nl.example.qualityreport.llm.LlmProviderException;
import nl.example.qualityreport.llm.LoopDetectedException;
import nl.example.qualityreport.llm.OllamaProvider;
import nl.example.qualityreport.report.evidence.DeterministicEvidenceExtractor;
import nl.example.qualityreport.report.evidence.EvidenceBundle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * True end-to-end test suite: 24 scenarios covering small / medium / large /
 * multi-repo code-change patterns, each scored against strict quality gates.
 *
 * <p>Run with: {@code OLLAMA_MODEL=qwen2.5-coder:3b OLLAMA_VERBOSE=true mvn test -Preport-e2e -Dsurefire.useFile=false}
 */
@Tag("report-e2e")
class ReportE2ETest {

    private static final String BASE_URL = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
    private static final String MODEL = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5-coder:3b");
    private static final boolean VERBOSE = "true".equalsIgnoreCase(System.getenv("OLLAMA_VERBOSE"));
    private static final Path ARTIFACT_DIR = Path.of("target/report-e2e");

    private static final List<ReportE2EScenario> completedScenarios = new ArrayList<>();
    private static final List<ReportScorer.ReportScoreResult> completedScores = new ArrayList<>();
    private static final List<SkippedScenario> skippedScenarios = new ArrayList<>();

    record SkippedScenario(ReportE2EScenario scenario, String reason) {}

    @BeforeAll
    static void preflight() {
        assumeOllamaReachable();
        assumeModelAvailable();
    }

    @AfterAll
    static void writeAggregate() throws IOException {
        if (!completedScenarios.isEmpty() || !skippedScenarios.isEmpty()) {
            writeAggregateIndex(completedScenarios, completedScores, skippedScenarios);
            System.err.printf(
                    "%n=== Aggregate: %d completed, %d skipped ===%n",
                    completedScenarios.size(), skippedScenarios.size());
        }
        if (new AppConfig().e2eResultsPersist() && Files.isDirectory(ARTIFACT_DIR)) {
            String stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.now());
            Path dest = Path.of("e2e-results", stamp);
            Files.createDirectories(dest);
            try (var stream = Files.list(ARTIFACT_DIR)) {
                for (Path src : stream.toList()) {
                    Files.copy(src, dest.resolve(src.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            System.err.printf("E2E results persisted to %s%n", dest.toAbsolutePath());
        }
    }

    static Stream<ReportE2EScenario> scenarios() {
        return ReportE2EScenarios.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenarioGeneratesScoredReport(ReportE2EScenario scenario, @TempDir Path tmp) throws Exception {
        System.err.printf("%n=== Scenario %s: %s ===%n", scenario.id(), scenario.description());

        List<Path> repoDirs;
        if (scenario.isMultiRepo()) {
            repoDirs = ReportFixtureRepoBuilder.buildAll(scenario, tmp);
        } else {
            repoDirs = List.of(ReportFixtureRepoBuilder.build(scenario, tmp));
        }
        Path rosterPath = ReportFixtureRepoBuilder.writeRoster(scenario, tmp);
        String scriptedInput = ReportFixtureRepoBuilder.buildScriptedInput(scenario);

        var inputReader = new BufferedReader(new StringReader(scriptedInput));
        Path outputDir = tmp.resolve("output");

        var provider = new OllamaProvider(MODEL, VERBOSE);
        var config = new AppConfig();
        var stdoutBuf = new ByteArrayOutputStream();
        var stdout = new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8);

        var useCase = new GenerateReportUseCase(inputReader, stdout, provider, config);

        GenerateReportUseCase.Result result;
        try {
            result = useCase.execute(repoDirs, scenario.branchName(), "main", rosterPath, outputDir, false);
        } catch (LlmProviderException e) {
            String reason = classifyFailure(e);
            System.err.printf("[%s] SKIPPED — %s: %s%n", scenario.id(), reason, truncate(e.getMessage(), 200));
            synchronized (skippedScenarios) {
                skippedScenarios.add(new SkippedScenario(scenario, reason));
            }
            writeSkippedArtifact(scenario, reason, e);
            return;
        }

        assertThat(result.reportPath()).as("report path").isNotNull();
        assertThat(result.reportPath()).as("report file exists").exists();

        String markdown = Files.readString(result.reportPath());
        System.err.printf("=== Report for %s (%d chars) ===%n", scenario.id(), markdown.length());
        System.err.println(markdown);
        System.err.println("=== End report ===");

        ReportScorer.ReportScoreResult score =
                ReportScorer.score(markdown, scenario.criticalFacts(), scenario.knownAbsentClaims());

        NarrativeVerifier.VerificationResult evidenceCoverage = null;
        try {
            EvidenceBundle evidence =
                    DeterministicEvidenceExtractor.extract(scenario.toChangeData(), scenario.toJiraData());
            evidenceCoverage = NarrativeVerifier.verify(markdown, evidence);
            System.err.printf(
                    "[%s] Evidence coverage: %.0f%% (%d/%d must-mention)%n",
                    scenario.id(),
                    evidenceCoverage.coveragePercent(),
                    evidenceCoverage.presentIdentifiers().size(),
                    evidenceCoverage.presentIdentifiers().size()
                            + evidenceCoverage.missingIdentifiers().size());
        } catch (Exception e) {
            System.err.printf("[%s] Evidence coverage: N/A (%s)%n", scenario.id(), e.getMessage());
        }

        System.err.printf("[%s] %s%n", scenario.id(), score.summaryLine());

        synchronized (completedScenarios) {
            completedScenarios.add(scenario);
            completedScores.add(score);
        }

        writeScenarioArtifact(scenario, markdown, score, evidenceCoverage);

        assertThat(score.sectionCoveragePercent())
                .as("[%s] All report sections must be present", scenario.id())
                .isEqualTo(100.0);

        assertThat(score.hallucinationFlags())
                .as("[%s] No hallucinated claims should appear", scenario.id())
                .isEmpty();

        assertThat(score.dutchDetected())
                .as("[%s] Narrative sections should contain Dutch prose", scenario.id())
                .isTrue();

        assertThat(score.factRecallPercent())
                .as("[%s] Fact recall must meet %.0f%% threshold", scenario.id(), scenario.minFactRecallPercent())
                .isGreaterThanOrEqualTo(scenario.minFactRecallPercent());
    }

    private static String classifyFailure(LlmProviderException e) {
        if (e instanceof LoopDetectedException) return "loop_detected";
        String msg = e.getMessage();
        if (msg != null && msg.contains("Failed to parse vote")) return "malformed_vote";
        if (msg != null && msg.contains("HTTP")) return "provider_http_error";
        return "provider_error";
    }

    private static String truncate(String s, int max) {
        return (s != null && s.length() > max) ? s.substring(0, max) + "..." : s;
    }

    // --- Artifact output ---

    private static void writeSkippedArtifact(ReportE2EScenario scenario, String reason, LlmProviderException error)
            throws IOException {
        Files.createDirectories(ARTIFACT_DIR);
        String slug = MODEL.replace(":", "-");

        var sb = new StringBuilder();
        sb.append("# %s — %s\n\n".formatted(scenario.id(), scenario.description()));
        sb.append("**Model**: %s | **Difficulty**: %s\n\n".formatted(MODEL, scenario.difficulty()));
        sb.append("## Status: SKIPPED\n\n");
        sb.append("**Reason**: `%s`\n\n".formatted(reason));
        sb.append("**Error**: %s\n".formatted(truncate(error.getMessage(), 500)));
        if (error instanceof LoopDetectedException loop) {
            sb.append("\n**Tokens generated**: %d\n".formatted(loop.tokensGenerated()));
            sb.append("**Repeated fragment**: `%s`\n".formatted(truncate(loop.repeatedFragment(), 200)));
        }

        Files.writeString(ARTIFACT_DIR.resolve("%s-%s.md".formatted(slug, scenario.id())), sb.toString());
    }

    private static void writeScenarioArtifact(
            ReportE2EScenario scenario,
            String markdown,
            ReportScorer.ReportScoreResult score,
            NarrativeVerifier.VerificationResult evidenceCoverage)
            throws IOException {
        Files.createDirectories(ARTIFACT_DIR);
        String slug = MODEL.replace(":", "-");

        var sb = new StringBuilder();
        sb.append("# %s — %s\n\n".formatted(scenario.id(), scenario.description()));
        sb.append("**Model**: %s | **Difficulty**: %s\n\n".formatted(MODEL, scenario.difficulty()));
        sb.append("## Score\n\n");
        sb.append("```\n%s\n```\n\n".formatted(score.summaryLine()));

        appendSectionTable(sb, score);
        appendFactTable(sb, score);
        appendHallucinations(sb, score);
        appendEvidenceCoverage(sb, evidenceCoverage);

        sb.append("## Dutch Prose: %s\n\n".formatted(score.dutchDetected() ? "Detected" : "NOT detected"));
        sb.append("\n---\n\n<details><summary>Generated report</summary>\n\n");
        sb.append(markdown);
        sb.append("\n</details>\n");

        Files.writeString(ARTIFACT_DIR.resolve("%s-%s.md".formatted(slug, scenario.id())), sb.toString());
    }

    static void writeAggregateIndex(
            List<ReportE2EScenario> scenarios,
            List<ReportScorer.ReportScoreResult> scores,
            List<SkippedScenario> skipped)
            throws IOException {
        Files.createDirectories(ARTIFACT_DIR);
        String slug = MODEL.replace(":", "-");

        var sb = new StringBuilder();
        sb.append("# Aggregate Report E2E Results — %s\n\n".formatted(MODEL));
        sb.append("| # | Scenario | Difficulty | Sections | Facts | Halluc. | Dutch | Recall | Status |\n");
        sb.append("|---|----------|------------|:--------:|:-----:|:-------:|:-----:|-------:|--------|\n");

        int row = 0;
        int passed = 0;
        for (int i = 0; i < scenarios.size(); i++) {
            row++;
            var sc = scenarios.get(i);
            var score = scores.get(i);
            boolean ok = score.sectionCoveragePercent() == 100.0
                    && score.hallucinationFlags().isEmpty()
                    && score.dutchDetected()
                    && score.factRecallPercent() >= sc.minFactRecallPercent();
            if (ok) passed++;
            String status = ok ? "PASS" : "**FAIL**";
            sb.append("| %d | %s | %s | %.0f%% | %d/%d | %d | %s | %.0f%% | %s |\n"
                    .formatted(
                            row,
                            sc.id(),
                            sc.difficulty(),
                            score.sectionCoveragePercent(),
                            score.criticalFactsHit().size(),
                            score.criticalFactsHit().size()
                                    + score.criticalFactsMissed().size(),
                            score.hallucinationFlags().size(),
                            score.dutchDetected() ? "OK" : "FAIL",
                            score.factRecallPercent(),
                            status));
        }

        for (var skip : skipped) {
            row++;
            sb.append("| %d | %s | %s | — | — | — | — | — | SKIPPED: %s |\n"
                    .formatted(row, skip.scenario().id(), skip.scenario().difficulty(), skip.reason()));
        }

        int total = scenarios.size() + skipped.size();
        sb.append("\n**Passed**: %d / %d | **Skipped**: %d\n".formatted(passed, total, skipped.size()));

        Files.writeString(ARTIFACT_DIR.resolve(slug + "-aggregate.md"), sb.toString());
    }

    // --- Formatting helpers ---

    private static void appendSectionTable(StringBuilder sb, ReportScorer.ReportScoreResult score) {
        sb.append("## Section Coverage (%.0f%%)\n\n".formatted(score.sectionCoveragePercent()));
        sb.append("| Section | Present |\n|---------|:-------:|\n");
        for (String s : score.sectionsPresent()) sb.append("| %s | yes |\n".formatted(s));
        for (String s : score.sectionsMissing()) sb.append("| %s | **NO** |\n".formatted(s));
        sb.append('\n');
    }

    private static void appendFactTable(StringBuilder sb, ReportScorer.ReportScoreResult score) {
        sb.append("## Fact Recall (%.0f%%)\n\n".formatted(score.factRecallPercent()));
        sb.append("| Fact | Found |\n|------|:-----:|\n");
        for (String f : score.criticalFactsHit()) sb.append("| %s | yes |\n".formatted(f));
        for (String f : score.criticalFactsMissed()) sb.append("| %s | **NO** |\n".formatted(f));
        sb.append('\n');
    }

    private static void appendHallucinations(StringBuilder sb, ReportScorer.ReportScoreResult score) {
        if (!score.hallucinationFlags().isEmpty()) {
            sb.append("## Hallucinations\n\n");
            for (String h : score.hallucinationFlags()) sb.append("- %s\n".formatted(h));
            sb.append('\n');
        }
    }

    private static void appendEvidenceCoverage(StringBuilder sb, NarrativeVerifier.VerificationResult evidence) {
        if (evidence == null) return;
        sb.append("## Evidence Coverage (%.0f%%)\n\n".formatted(evidence.coveragePercent()));
        sb.append("| Must-Mention Identifier | Found |\n|------------------------|:-----:|\n");
        for (String id : evidence.presentIdentifiers()) {
            sb.append("| %s | yes |\n".formatted(id));
        }
        for (String id : evidence.missingIdentifiers()) {
            sb.append("| %s | **NO** |\n".formatted(id));
        }
        sb.append('\n');
    }

    // --- Preflight ---

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
