package nl.example.qualityreport.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * Scores a model's JSON response against a scenario's ground-truth facts.
 * Produces an exploratory score — no hard pass/fail thresholds.
 */
final class AccuracyScorer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> REQUIRED_FIELDS = List.of(
            "change_summary",
            "key_risks",
            "impacted_areas",
            "test_evidence_assessment",
            "deployment_risks",
            "confidence");

    private AccuracyScorer() {}

    record ScoreResult(
            boolean validJson,
            List<String> presentFields,
            List<String> missingFields,
            int criticalFactsFound,
            int criticalFactsTotal,
            List<String> criticalFactsHit,
            List<String> criticalFactsMissed,
            List<String> hallucinationFlags,
            List<String> expectedAreasHit,
            List<String> expectedAreasMissed,
            double factRecallPercent,
            double fieldCoveragePercent) {
        String summaryLine() {
            return "json=%s fields=%d/%d facts=%d/%d hallucinations=%d recall=%.0f%%"
                    .formatted(
                            validJson ? "OK" : "FAIL",
                            presentFields.size(),
                            presentFields.size() + missingFields.size(),
                            criticalFactsFound,
                            criticalFactsTotal,
                            hallucinationFlags.size(),
                            factRecallPercent);
        }
    }

    static ScoreResult score(String rawResponse, AccuracyScenarioFixtures.Scenario scenario) {
        String responseText = rawResponse.toLowerCase();

        JsonNode root = tryParseJson(rawResponse);
        boolean validJson = root != null;

        List<String> presentFields = new ArrayList<>();
        List<String> missingFields = new ArrayList<>();
        if (validJson) {
            for (String field : REQUIRED_FIELDS) {
                if (root.has(field) && !root.get(field).isNull() && isNonEmpty(root.get(field))) {
                    presentFields.add(field);
                } else {
                    missingFields.add(field);
                }
            }
        } else {
            missingFields.addAll(REQUIRED_FIELDS);
        }

        List<String> criticalFactsHit = new ArrayList<>();
        List<String> criticalFactsMissed = new ArrayList<>();
        for (String fact : scenario.criticalFacts()) {
            if (responseText.contains(fact.toLowerCase())) {
                criticalFactsHit.add(fact);
            } else {
                criticalFactsMissed.add(fact);
            }
        }

        List<String> expectedAreasHit = new ArrayList<>();
        List<String> expectedAreasMissed = new ArrayList<>();
        for (String area : scenario.expectedAreas()) {
            if (responseText.contains(area.toLowerCase())) {
                expectedAreasHit.add(area);
            } else {
                expectedAreasMissed.add(area);
            }
        }

        List<String> hallucinationFlags = new ArrayList<>();
        for (String absent : scenario.knownAbsentClaims()) {
            if (responseText.contains(absent.toLowerCase())) {
                hallucinationFlags.add(absent);
            }
        }

        int total = scenario.criticalFacts().size();
        double recall = total > 0 ? (criticalFactsHit.size() * 100.0 / total) : 100.0;

        int totalFields = REQUIRED_FIELDS.size();
        double fieldCoverage = totalFields > 0 ? (presentFields.size() * 100.0 / totalFields) : 100.0;

        return new ScoreResult(
                validJson,
                presentFields,
                missingFields,
                criticalFactsHit.size(),
                total,
                criticalFactsHit,
                criticalFactsMissed,
                hallucinationFlags,
                expectedAreasHit,
                expectedAreasMissed,
                recall,
                fieldCoverage);
    }

    private static JsonNode tryParseJson(String raw) {
        String trimmed = raw.strip();

        // Try direct parse first
        JsonNode node = attemptParse(trimmed);
        if (node != null && node.isObject()) return node;

        // Try extracting JSON from markdown code fence
        int start = trimmed.indexOf("```json");
        if (start >= 0) {
            int contentStart = trimmed.indexOf('\n', start);
            int end = trimmed.indexOf("```", contentStart + 1);
            if (contentStart > 0 && end > contentStart) {
                node = attemptParse(trimmed.substring(contentStart + 1, end).strip());
                if (node != null && node.isObject()) return node;
            }
        }

        // Try extracting first { ... } block
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            node = attemptParse(trimmed.substring(braceStart, braceEnd + 1));
            if (node != null && node.isObject()) return node;
        }

        return null;
    }

    private static boolean isNonEmpty(JsonNode node) {
        if (node.isArray()) return node.size() > 0;
        if (node.isTextual()) return !node.asText().isBlank();
        return true;
    }

    private static JsonNode attemptParse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
