package nl.example.qualityreport.prompt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.model.JiraData.Deployment;
import nl.example.qualityreport.model.JiraData.Impact;
import nl.example.qualityreport.model.JiraData.TestEvidence;

/**
 * Collects Jira data interactively from terminal input.
 * I/O is injected for deterministic testing with scripted input.
 */
public class InteractivePrompter {

    private final BufferedReader in;
    private final PrintStream out;
    private final ChangeData changeData;

    public InteractivePrompter(BufferedReader in, PrintStream out, ChangeData changeData) {
        this.in = in;
        this.out = out;
        this.changeData = changeData;
    }

    public JiraData collect() throws IOException {
        printHeader();

        List<String> tickets = promptTickets();
        String description = promptDescription();
        String acceptanceCriteria = promptAcceptanceCriteria();
        Impact impact = promptImpact();
        TestEvidence testEvidence = promptTestEvidence();
        Deployment deployment = promptDeployment();
        boolean dodComplete = promptDoD();
        String fixVersion = promptFixVersion();

        return new JiraData(
                tickets, description, acceptanceCriteria, impact, testEvidence, deployment, dodComplete, fixVersion);
    }

    private void printHeader() {
        out.println("───────────────────────────────────────────────────────");
        out.println("  JIRA INFORMATION");
        out.println("───────────────────────────────────────────────────────");
        out.println();
    }

    // --- Section 1: Tickets ---

    private List<String> promptTickets() throws IOException {
        out.println("1. Ticket IDs");
        out.println("   Which GAAS/CIP/DPS tickets are part of this change?");
        out.println("   (comma-separated, e.g.: PROJ-12345, CIP-678)");
        out.println();
        String line = promptLine();
        return parseTickets(line);
    }

    static List<String> parseTickets(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        return Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // --- Section 2: Description ---

    private String promptDescription() throws IOException {
        out.println("2. What changed and why?");
        out.println("   Paste the Jira ticket description or a short summary.");
        out.println("   (end with an empty line)");
        out.println();
        return readMultiline();
    }

    // --- Section 3: Acceptance Criteria ---

    private String promptAcceptanceCriteria() throws IOException {
        out.println("3. Acceptance criteria");
        out.println("   Paste the acceptance criteria from Jira (if any).");
        out.println("   (end with an empty line, or type \"none\")");
        out.println();
        String text = readMultiline();
        return normalizeNone(text);
    }

    // --- Section 4: Impact ---

    private Impact promptImpact() throws IOException {
        out.println("4. Impact areas — confirm or adjust auto-detected checkboxes:");
        out.println();

        Set<String> autoDetected = detectImpactSignals();
        Map<String, Boolean> areas = new LinkedHashMap<>();

        if (!autoDetected.isEmpty()) {
            out.println("   Auto-detected:");
            for (String area : autoDetected) {
                out.printf("     ☑ %s%n", area);
                areas.put(area, true);
            }
            out.println();
        }

        out.println("   Additional (answer y/n for each):");
        for (String area : Impact.ALL_AREAS) {
            if (autoDetected.contains(area)) {
                continue;
            }
            boolean checked = promptYesNo("     " + area + "?", false);
            areas.put(area, checked);
        }
        out.println();

        return new Impact(areas);
    }

    Set<String> detectImpactSignals() {
        Set<String> detected = new LinkedHashSet<>();
        if (changeData.scriptCount() > 0) {
            detected.add("Database(s) wijzigingen");
        }
        if (hasFrontendChanges()) {
            detected.add("Front-end / Javascript");
        }
        if (isMultiModule()) {
            detected.add("Impact op meer dan 1 module");
        }
        return detected;
    }

    private boolean hasFrontendChanges() {
        return changeData.changedFiles().stream().anyMatch(f -> {
            String lower = f.toLowerCase();
            return lower.endsWith(".js")
                    || lower.endsWith(".ts")
                    || lower.endsWith(".jsx")
                    || lower.endsWith(".tsx")
                    || lower.endsWith(".vue")
                    || lower.endsWith(".css")
                    || lower.endsWith(".scss");
        });
    }

    private boolean isMultiModule() {
        long distinctRepos = changeData.repoFiles().stream()
                .map(rf -> rf.repoName())
                .filter(name -> !name.isEmpty())
                .distinct()
                .count();
        if (distinctRepos > 1) {
            return true;
        }
        long distinctModules = changeData.changedFiles().stream()
                .map(f -> f.contains("/") ? f.substring(0, f.indexOf('/')) : "root")
                .distinct()
                .count();
        return distinctModules > 1;
    }

    // --- Section 5: Test Evidence ---

    private TestEvidence promptTestEvidence() throws IOException {
        out.println("5. Test evidence");
        out.println("   What did you test manually? Paste a short description.");
        out.println("   (end with an empty line, or type \"none\")");
        out.println();
        String manual = normalizeNone(readMultiline());

        boolean hasAutomated = promptYesNo("   Do you have automated test results? (CI pipeline, JUnit)", false);

        String passFail = "";
        String coverage = "unknown";

        if (hasAutomated) {
            out.println("   Test pass/fail summary (e.g., \"142 passed, 0 failed\"):");
            out.println();
            passFail = promptLine();

            out.println("   Code coverage % (or \"unknown\"):");
            out.println();
            coverage = promptLine();
            if (coverage.isBlank()) {
                coverage = "unknown";
            }
        }
        out.println();

        return new TestEvidence(manual, hasAutomated, passFail, coverage);
    }

    // --- Section 6: Deployment ---

    private Deployment promptDeployment() throws IOException {
        out.println("6. Deployment");
        boolean standard = promptYesNo("   Standard deployment to all municipalities?", true);
        boolean toggle = promptYesNo("   Feature toggle required?", false);
        boolean script = promptYesNo("   Migration script needs manual execution?", false);
        boolean hypercare = promptYesNo("   Hypercare / active monitoring needed?", false);
        out.println();

        return new Deployment(standard, toggle, script, hypercare);
    }

    // --- Section 7: DoD ---

    private boolean promptDoD() throws IOException {
        out.println("7. Definition of Done");
        boolean complete = promptYesNo("   Is the DoD complete for all linked tickets?", true);
        out.println();
        return complete;
    }

    // --- Section 8: Fix Version ---

    private String promptFixVersion() throws IOException {
        out.println("8. Fix version");
        out.println("   Which release version? (e.g., 5.13.0)");
        out.println();
        return promptLine();
    }

    // --- I/O helpers ---

    private String promptLine() throws IOException {
        out.print("   > ");
        out.flush();
        String line = in.readLine();
        return line == null ? "" : line;
    }

    String readMultiline() throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = readRawLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    private String readRawLine() throws IOException {
        out.print("   > ");
        out.flush();
        return in.readLine();
    }

    boolean promptYesNo(String prompt, boolean defaultYes) throws IOException {
        String hint = defaultYes ? "[Y/n]" : "[y/N]";
        out.printf("%s %s ", prompt, hint);
        out.flush();
        String line = in.readLine();
        ParseResult result = parseYesNo(line, defaultYes);
        if (!result.recognized()) {
            out.printf(
                    "     Unrecognised input '%s'. Please enter y/yes/ja or n/no/nee: ",
                    line != null ? line.trim() : "");
            out.flush();
            String retry = in.readLine();
            ParseResult retryResult = parseYesNo(retry, defaultYes);
            return retryResult.value();
        }
        return result.value();
    }

    static ParseResult parseYesNo(String input, boolean defaultValue) {
        if (input == null || input.isBlank()) {
            return new ParseResult(defaultValue, true);
        }
        String trimmed = input.trim().toLowerCase();
        return switch (trimmed) {
            case "y", "yes", "ja" -> new ParseResult(true, true);
            case "n", "no", "nee" -> new ParseResult(false, true);
            default -> new ParseResult(defaultValue, false);
        };
    }

    record ParseResult(boolean value, boolean recognized) {}

    static String normalizeNone(String input) {
        if (input == null || input.isBlank()) {
            return "none";
        }
        if (input.trim().equalsIgnoreCase("none")) {
            return "none";
        }
        return input;
    }
}
