package nl.example.qualityreport.report;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates rendered report markdown against structural invariants:
 * required headings present, in correct order, each appearing exactly once.
 */
public final class ReportTemplateValidator {

    static final List<String> REQUIRED_HEADINGS = List.of(
            "# Testrapport",
            "## Betrokken tickets",
            "## Wat is er gewijzigd en waarom?",
            "### Wijzigingen per type",
            "## Wat wordt geraakt?",
            "## Acceptatiecriteria",
            "## Risico-analyse",
            "## Wat is getest?",
            "### Handmatig",
            "### Geautomatiseerd",
            "## Uitlevering op productie",
            "## DoD");

    private ReportTemplateValidator() {}

    /**
     * Validates rendered markdown. Returns a list of violations (empty if valid).
     */
    public static List<String> validate(String markdown) {
        List<String> violations = new ArrayList<>();

        int previousIndex = -1;
        for (String heading : REQUIRED_HEADINGS) {
            int firstIndex = indexOfHeading(markdown, heading);
            if (firstIndex < 0) {
                violations.add("Missing required heading: " + heading);
                continue;
            }

            int secondIndex = indexOfHeading(markdown, heading, firstIndex + heading.length());
            if (secondIndex >= 0) {
                violations.add("Duplicate heading: " + heading);
            }

            if (firstIndex <= previousIndex) {
                violations.add("Heading out of order: " + heading);
            }
            previousIndex = firstIndex;
        }

        return violations;
    }

    /**
     * Validates and throws if violations are found.
     *
     * @throws IllegalStateException with all violations listed
     */
    public static void requireValid(String markdown) {
        List<String> violations = validate(markdown);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Report template validation failed:\n- " + String.join("\n- ", violations));
        }
    }

    private static int indexOfHeading(String markdown, String heading) {
        return indexOfHeading(markdown, heading, 0);
    }

    /**
     * Finds a heading at line-start position (beginning of string or after newline).
     */
    private static int indexOfHeading(String markdown, String heading, int fromIndex) {
        int pos = fromIndex;
        while (pos < markdown.length()) {
            int idx = markdown.indexOf(heading, pos);
            if (idx < 0) return -1;
            boolean atLineStart = idx == 0 || markdown.charAt(idx - 1) == '\n';
            if (atLineStart) {
                return idx;
            }
            pos = idx + 1;
        }
        return -1;
    }
}
