package nl.example.qualityreport.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Aggregates tool-use E2E results into a structured summary report.
 * Called by {@link OllamaToolUseE2ETest} at the end of each test method
 * to produce per-test and cross-test metrics.
 */
final class ToolUseMetricsAggregator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path OUTPUT_DIR = Path.of("target/tool-e2e");

    private ToolUseMetricsAggregator() {}

    record Metrics(
            String testName,
            Instant timestamp,
            int totalRuns,
            int toolRequests,
            int directVotes,
            int otherResponses,
            double validToolNameRate,
            double validArgRate,
            double validRevisionRate,
            double unknownToolHallucinationRate) {}

    static Metrics compute(String testName, List<OllamaToolUseE2ETest.ToolUseRunRecord> records) {
        int total = records.size();
        int toolReqs = (int) records.stream().filter(r -> r.isToolRequest()).count();
        int votes = (int) records.stream()
                .filter(r -> !r.isToolRequest() && r.isValidVote())
                .count();
        int other = total - toolReqs - votes;

        long validToolName = records.stream()
                .filter(r -> r.isToolRequest() && r.validToolName())
                .count();
        long validRef =
                records.stream().filter(r -> r.isToolRequest() && r.validRef()).count();
        long validRevision = records.stream()
                .filter(r -> r.isToolRequest() && r.validRevision())
                .count();
        long undeclaredTool = records.stream()
                .filter(r -> r.isToolRequest() && !r.validToolName())
                .count();

        return new Metrics(
                testName,
                Instant.now(),
                total,
                toolReqs,
                votes,
                other,
                toolReqs > 0 ? (double) validToolName / toolReqs : 0,
                toolReqs > 0 ? (double) validRef / toolReqs : 0,
                toolReqs > 0 ? (double) validRevision / toolReqs : 0,
                total > 0 ? (double) undeclaredTool / total : 0);
    }

    static void writeMetrics(Metrics metrics) throws IOException {
        Files.createDirectories(OUTPUT_DIR);

        var map = new LinkedHashMap<String, Object>();
        map.put("test_name", metrics.testName);
        map.put("timestamp", metrics.timestamp.toString());
        map.put("total_runs", metrics.totalRuns);
        map.put("tool_requests", metrics.toolRequests);
        map.put("direct_votes", metrics.directVotes);
        map.put("other_responses", metrics.otherResponses);
        map.put("valid_tool_name_rate", metrics.validToolNameRate);
        map.put("valid_arg_rate", metrics.validArgRate);
        map.put("valid_revision_rate", metrics.validRevisionRate);
        map.put("unknown_tool_hallucination_rate", metrics.unknownToolHallucinationRate);

        Files.writeString(
                OUTPUT_DIR.resolve(metrics.testName + "-metrics.json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(map));
    }

    static void writeSummaryReport(List<Metrics> allMetrics) throws IOException {
        Files.createDirectories(OUTPUT_DIR);

        var sb = new StringBuilder();
        sb.append("# Tool-Use E2E Summary Report\n\n");
        sb.append("Generated: ").append(Instant.now()).append("\n\n");
        sb.append("| Test | Runs | Tool Reqs | Votes | Valid Tool % | Valid Arg % | Hallucination % |\n");
        sb.append("|------|------|-----------|-------|-------------|------------|----------------|\n");

        for (var m : allMetrics) {
            sb.append("| ")
                    .append(m.testName)
                    .append(" | ")
                    .append(m.totalRuns)
                    .append(" | ")
                    .append(m.toolRequests)
                    .append(" | ")
                    .append(m.directVotes)
                    .append(" | ")
                    .append(pct(m.validToolNameRate))
                    .append(" | ")
                    .append(pct(m.validArgRate))
                    .append(" | ")
                    .append(pct(m.unknownToolHallucinationRate))
                    .append(" |\n");
        }

        double avgValidTool = allMetrics.stream()
                .filter(m -> m.toolRequests > 0)
                .mapToDouble(m -> m.validToolNameRate)
                .average()
                .orElse(0);
        double avgValidArg = allMetrics.stream()
                .filter(m -> m.toolRequests > 0)
                .mapToDouble(m -> m.validArgRate)
                .average()
                .orElse(0);

        sb.append("\n## Aggregate\n\n");
        sb.append("- Average valid tool name rate: ").append(pct(avgValidTool)).append('\n');
        sb.append("- Average valid arg rate: ").append(pct(avgValidArg)).append('\n');

        Files.writeString(OUTPUT_DIR.resolve("summary.md"), sb.toString());
    }

    private static String pct(double rate) {
        return String.format("%.0f%%", rate * 100);
    }
}
