package nl.example.qualityreport.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes accuracy evaluation artifacts: a JSON log of all runs
 * and a human-readable markdown summary with feasibility recommendation.
 */
final class AccuracyReportWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private AccuracyReportWriter() {}

    record RunRecord(
            String scenario, int runIndex, long durationMs, String rawResponse, AccuracyScorer.ScoreResult score) {}

    static void writeArtifacts(Path outputDir, String model, List<RunRecord> runs) throws IOException {
        Files.createDirectories(outputDir);
        writeJsonRuns(outputDir.resolve(model.replace(":", "-") + "-runs.json"), runs);
        writeMarkdownSummary(outputDir.resolve(model.replace(":", "-") + "-summary.md"), model, runs);
    }

    private static void writeJsonRuns(Path path, List<RunRecord> runs) throws IOException {
        List<Map<String, Object>> jsonRuns = new ArrayList<>();
        for (RunRecord run : runs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("scenario", run.scenario);
            entry.put("run", run.runIndex);
            entry.put("duration_ms", run.durationMs);
            entry.put("valid_json", run.score.validJson());
            entry.put("fact_recall_pct", run.score.factRecallPercent());
            entry.put("field_coverage_pct", run.score.fieldCoveragePercent());
            entry.put("critical_facts_found", run.score.criticalFactsFound());
            entry.put("critical_facts_total", run.score.criticalFactsTotal());
            entry.put("critical_facts_hit", run.score.criticalFactsHit());
            entry.put("critical_facts_missed", run.score.criticalFactsMissed());
            entry.put("hallucination_flags", run.score.hallucinationFlags());
            entry.put("expected_areas_hit", run.score.expectedAreasHit());
            entry.put("expected_areas_missed", run.score.expectedAreasMissed());
            entry.put("present_fields", run.score.presentFields());
            entry.put("missing_fields", run.score.missingFields());
            entry.put("raw_response", run.rawResponse);
            jsonRuns.add(entry);
        }
        Files.writeString(path, MAPPER.writeValueAsString(jsonRuns));
    }

    private static void writeMarkdownSummary(Path path, String model, List<RunRecord> runs) throws IOException {
        var sb = new StringBuilder();
        sb.append("# Ollama Accuracy Feasibility Report\n\n");
        sb.append("**Model:** `").append(model).append("`\n");
        sb.append("**Date:** ").append(Instant.now().toString()).append("\n");
        sb.append("**Total runs:** ").append(runs.size()).append("\n\n");

        Map<String, List<RunRecord>> byScenario = new LinkedHashMap<>();
        for (RunRecord run : runs) {
            byScenario.computeIfAbsent(run.scenario, k -> new ArrayList<>()).add(run);
        }

        sb.append("## Aggregate Scores\n\n");
        sb.append("| Scenario | Runs | JSON Valid | Avg Fact Recall | Avg Field Coverage | Hallucinations |\n");
        sb.append("|----------|------|------------|-----------------|---------------------|----------------|\n");

        for (var entry : byScenario.entrySet()) {
            List<RunRecord> scenarioRuns = entry.getValue();
            long validCount =
                    scenarioRuns.stream().filter(r -> r.score.validJson()).count();
            double avgRecall = scenarioRuns.stream()
                    .mapToDouble(r -> r.score.factRecallPercent())
                    .average()
                    .orElse(0);
            double avgFieldCov = scenarioRuns.stream()
                    .mapToDouble(r -> r.score.fieldCoveragePercent())
                    .average()
                    .orElse(0);
            long totalHallucinations = scenarioRuns.stream()
                    .mapToLong(r -> r.score.hallucinationFlags().size())
                    .sum();

            sb.append("| ")
                    .append(entry.getKey())
                    .append(" | ")
                    .append(scenarioRuns.size())
                    .append(" | ")
                    .append(validCount)
                    .append("/")
                    .append(scenarioRuns.size())
                    .append(" | ")
                    .append("%.1f%%".formatted(avgRecall))
                    .append(" | ")
                    .append("%.1f%%".formatted(avgFieldCov))
                    .append(" | ")
                    .append(totalHallucinations)
                    .append(" |\n");
        }

        for (var entry : byScenario.entrySet()) {
            sb.append("\n## Scenario: ").append(entry.getKey()).append("\n\n");

            for (RunRecord run : entry.getValue()) {
                sb.append("### Run ")
                        .append(run.runIndex)
                        .append(" (")
                        .append(run.durationMs)
                        .append("ms)\n\n");
                sb.append("- **JSON valid:** ")
                        .append(run.score.validJson() ? "Yes" : "No")
                        .append('\n');
                sb.append("- **Fact recall:** ")
                        .append("%.0f%%".formatted(run.score.factRecallPercent()))
                        .append(" (")
                        .append(run.score.criticalFactsFound())
                        .append("/")
                        .append(run.score.criticalFactsTotal())
                        .append(")\n");
                sb.append("- **Field coverage:** ")
                        .append("%.0f%%".formatted(run.score.fieldCoveragePercent()))
                        .append('\n');

                if (!run.score.criticalFactsMissed().isEmpty()) {
                    sb.append("- **Missed facts:** ")
                            .append(String.join(", ", run.score.criticalFactsMissed()))
                            .append('\n');
                }
                if (!run.score.hallucinationFlags().isEmpty()) {
                    sb.append("- **Hallucinations detected:** ")
                            .append(String.join(", ", run.score.hallucinationFlags()))
                            .append('\n');
                }
                if (!run.score.expectedAreasMissed().isEmpty()) {
                    sb.append("- **Missed impact areas:** ")
                            .append(String.join(", ", run.score.expectedAreasMissed()))
                            .append('\n');
                }

                sb.append("\n<details><summary>Raw response</summary>\n\n```\n");
                sb.append(run.rawResponse).append("\n```\n\n</details>\n\n");
            }
        }

        sb.append("\n## Feasibility Assessment\n\n");
        double overallRecall = runs.stream()
                .mapToDouble(r -> r.score.factRecallPercent())
                .average()
                .orElse(0);
        long overallValidJson = runs.stream().filter(r -> r.score.validJson()).count();
        long overallHallucinations = runs.stream()
                .mapToLong(r -> r.score.hallucinationFlags().size())
                .sum();
        double jsonRate = runs.isEmpty() ? 0 : overallValidJson * 100.0 / runs.size();

        sb.append("| Metric | Value |\n");
        sb.append("|--------|-------|\n");
        sb.append("| JSON compliance rate | ")
                .append("%.0f%%".formatted(jsonRate))
                .append(" |\n");
        sb.append("| Average fact recall | ")
                .append("%.0f%%".formatted(overallRecall))
                .append(" |\n");
        sb.append("| Total hallucination flags | ")
                .append(overallHallucinations)
                .append(" |\n\n");

        if (jsonRate >= 80 && overallRecall >= 70 && overallHallucinations == 0) {
            sb.append(
                    "**Recommendation:** Feasible now -- model produces valid structured output with good fact recall and no hallucinations.\n");
        } else if (jsonRate >= 50 && overallRecall >= 50) {
            sb.append(
                    "**Recommendation:** Feasible with guardrails -- model sometimes produces valid output but needs retry logic, output validation, and hallucination filtering.\n");
        } else {
            sb.append(
                    "**Recommendation:** Consider alternative model -- JSON compliance and/or fact recall are too low for reliable use.\n");
        }

        Files.writeString(path, sb.toString());
    }
}
