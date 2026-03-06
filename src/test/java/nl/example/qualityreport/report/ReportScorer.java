package nl.example.qualityreport.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Evaluates a generated markdown report against ground truth.
 * Checks section presence, critical fact recall, hallucination detection,
 * and basic Dutch language presence in narrative sections.
 */
final class ReportScorer {

    private static final List<String> EXPECTED_SECTIONS = List.of(
            "Testrapport",
            "Betrokken tickets",
            "Wat is er gewijzigd en waarom?",
            "Wijzigingen per type",
            "Wat wordt geraakt?",
            "Acceptatiecriteria",
            "Risico-analyse",
            "Wat is getest?",
            "Uitlevering op productie",
            "DoD");

    private static final Set<String> DUTCH_MARKERS = Set.of(
            " de ", " het ", " van ", " een ", " voor ", " wijziging", " risico", " beperkt", " impact", " bestand");

    private static final Pattern NARRATIVE_SECTION =
            Pattern.compile("(?s)## Wat is er gewijzigd en waarom\\?(.+?)(?=##|$)");
    private static final Pattern RISK_SECTION = Pattern.compile("(?s)## Risico-analyse(.+?)(?=##|$)");

    private ReportScorer() {}

    record ReportScoreResult(
            List<String> sectionsPresent,
            List<String> sectionsMissing,
            List<String> criticalFactsHit,
            List<String> criticalFactsMissed,
            List<String> hallucinationFlags,
            boolean dutchDetected,
            double factRecallPercent,
            double sectionCoveragePercent) {
        String summaryLine() {
            return "sections=%d/%d facts=%d/%d hallucinations=%d dutch=%s recall=%.0f%%"
                    .formatted(
                            sectionsPresent.size(),
                            sectionsPresent.size() + sectionsMissing.size(),
                            criticalFactsHit.size(),
                            criticalFactsHit.size() + criticalFactsMissed.size(),
                            hallucinationFlags.size(),
                            dutchDetected ? "OK" : "FAIL",
                            factRecallPercent);
        }
    }

    static ReportScoreResult score(String markdown, List<String> criticalFacts, List<String> knownAbsentClaims) {
        List<String> sectionsPresent = new ArrayList<>();
        List<String> sectionsMissing = new ArrayList<>();
        for (String section : EXPECTED_SECTIONS) {
            if (markdown.contains(section)) {
                sectionsPresent.add(section);
            } else {
                sectionsMissing.add(section);
            }
        }

        List<String> criticalFactsHit = new ArrayList<>();
        List<String> criticalFactsMissed = new ArrayList<>();
        for (String fact : criticalFacts) {
            if (IdentifierMatcher.containsIdentifier(markdown, fact)) {
                criticalFactsHit.add(fact);
            } else {
                criticalFactsMissed.add(fact);
            }
        }

        String fullLower = markdown.toLowerCase();
        List<String> hallucinationFlags = new ArrayList<>();
        for (String absent : knownAbsentClaims) {
            if (fullLower.contains(absent.toLowerCase())) {
                hallucinationFlags.add(absent);
            }
        }

        String narrativeText = extractNarrativeSections(markdown);
        String narrativeLower = narrativeText.toLowerCase();
        boolean dutchDetected = DUTCH_MARKERS.stream().anyMatch(narrativeLower::contains);

        int total = criticalFacts.size();
        double recall = total > 0 ? (criticalFactsHit.size() * 100.0 / total) : 100.0;
        int totalSections = EXPECTED_SECTIONS.size();
        double sectionCoverage = totalSections > 0 ? (sectionsPresent.size() * 100.0 / totalSections) : 100.0;

        return new ReportScoreResult(
                sectionsPresent, sectionsMissing,
                criticalFactsHit, criticalFactsMissed,
                hallucinationFlags, dutchDetected,
                recall, sectionCoverage);
    }

    private static String extractNarrativeSections(String markdown) {
        var sb = new StringBuilder();
        var changeMatcher = NARRATIVE_SECTION.matcher(markdown);
        if (changeMatcher.find()) {
            sb.append(changeMatcher.group(1));
        }
        var riskMatcher = RISK_SECTION.matcher(markdown);
        if (riskMatcher.find()) {
            sb.append(riskMatcher.group(1));
        }
        return sb.toString();
    }
}
