package nl.example.qualityreport.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.CommitInfo;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.model.RepoFile;
import org.junit.jupiter.api.Test;

class InteractivePrompterTest {

    private static final ChangeData CHANGE_DATA_WITH_DETECTIONS = ChangeData.from(
            List.of(commit("hash1", "dev@example.nl")),
            "diff content",
            List.of("module-a/src/Main.java", "module-b/db/V1__init.sql", "frontend/app.ts"));

    private static final ChangeData CHANGE_DATA_EMPTY = ChangeData.from(List.of(), "", List.of());

    // --- Happy path (fixture-based) ---

    @Test
    void collect_happyPath_returnsFullyPopulatedJiraData() throws IOException {
        JiraData result = runFixture("happy-path.txt", CHANGE_DATA_WITH_DETECTIONS);

        assertThat(result.tickets()).containsExactly("PROJ-128530", "PROJ-128531");
        assertThat(result.description()).contains("GBA-V bevraging");
        assertThat(result.acceptanceCriteria()).contains("Cache TTL");
        assertThat(result.fixVersion()).isEqualTo("5.13.0");
        assertThat(result.dodComplete()).isTrue();

        assertThat(result.impact().isChecked("Database(s) wijzigingen")).isTrue();
        assertThat(result.impact().isChecked("Front-end / Javascript")).isTrue();
        assertThat(result.impact().isChecked("MuniPortal")).isTrue();
        assertThat(result.impact().isChecked("Balieprocessen")).isFalse();

        assertThat(result.testEvidence().hasAutomatedTests()).isTrue();
        assertThat(result.testEvidence().passFail()).isEqualTo("142 passed, 0 failed");
        assertThat(result.testEvidence().coveragePercent()).isEqualTo("78%");
        assertThat(result.testEvidence().manualDescription()).contains("Verified on local");

        assertThat(result.deployment().standardDeployment()).isTrue();
        assertThat(result.deployment().featureToggle()).isFalse();
    }

    // --- Minimal input / defaults ---

    @Test
    void collect_minimalInput_usesDefaults() throws IOException {
        JiraData result = runFixture("minimal-input.txt", CHANGE_DATA_EMPTY);

        assertThat(result.tickets()).containsExactly("PROJ-99999");
        assertThat(result.description()).isEmpty();
        assertThat(result.acceptanceCriteria()).isEqualTo("none");
        assertThat(result.testEvidence().manualDescription()).isEqualTo("none");
        assertThat(result.testEvidence().hasAutomatedTests()).isFalse();
        assertThat(result.testEvidence().coveragePercent()).isEqualTo("unknown");
        assertThat(result.deployment().standardDeployment()).isTrue();
        assertThat(result.dodComplete()).isTrue();
    }

    // --- Multi-line sections ---

    @Test
    void collect_multilineSections_preservesAllLines() throws IOException {
        JiraData result = runFixture("multiline-sections.txt", CHANGE_DATA_EMPTY);

        assertThat(result.description())
                .isEqualTo("Line one of description.\nLine two of description.\nLine three of description.");
        assertThat(result.acceptanceCriteria()).isEqualTo("Line one of ACs.\nLine two of ACs.");
        assertThat(result.testEvidence().manualDescription())
                .isEqualTo("Line one of manual test.\nLine two of manual test.");
    }

    // --- Ticket parsing ---

    @Test
    void parseTickets_commaSeparated_trimsWhitespace() {
        List<String> tickets = InteractivePrompter.parseTickets("  PROJ-1 , CIP-2,  DPS-3  ");
        assertThat(tickets).containsExactly("PROJ-1", "CIP-2", "DPS-3");
    }

    @Test
    void parseTickets_singleTicket() {
        assertThat(InteractivePrompter.parseTickets("PROJ-12345")).containsExactly("PROJ-12345");
    }

    @Test
    void parseTickets_emptyInput_returnsEmptyList() {
        assertThat(InteractivePrompter.parseTickets("")).isEmpty();
        assertThat(InteractivePrompter.parseTickets("   ")).isEmpty();
        assertThat(InteractivePrompter.parseTickets(null)).isEmpty();
    }

    @Test
    void parseTickets_trailingComma_ignoresEmpty() {
        assertThat(InteractivePrompter.parseTickets("PROJ-1,")).containsExactly("PROJ-1");
    }

    // --- Yes/No parsing ---

    @Test
    void parseYesNo_yesVariants() {
        assertThat(InteractivePrompter.parseYesNo("y", false).value()).isTrue();
        assertThat(InteractivePrompter.parseYesNo("Y", false).value()).isTrue();
        assertThat(InteractivePrompter.parseYesNo("yes", false).value()).isTrue();
        assertThat(InteractivePrompter.parseYesNo("YES", false).value()).isTrue();
        assertThat(InteractivePrompter.parseYesNo("y", false).recognized()).isTrue();
    }

    @Test
    void parseYesNo_dutchVariants() {
        assertThat(InteractivePrompter.parseYesNo("ja", false).value()).isTrue();
        assertThat(InteractivePrompter.parseYesNo("JA", false).value()).isTrue();
        assertThat(InteractivePrompter.parseYesNo("nee", true).value()).isFalse();
        assertThat(InteractivePrompter.parseYesNo("NEE", true).value()).isFalse();
        assertThat(InteractivePrompter.parseYesNo("ja", false).recognized()).isTrue();
        assertThat(InteractivePrompter.parseYesNo("nee", true).recognized()).isTrue();
    }

    @Test
    void parseYesNo_noVariants() {
        assertThat(InteractivePrompter.parseYesNo("n", true).value()).isFalse();
        assertThat(InteractivePrompter.parseYesNo("N", true).value()).isFalse();
        assertThat(InteractivePrompter.parseYesNo("no", true).value()).isFalse();
        assertThat(InteractivePrompter.parseYesNo("NO", true).value()).isFalse();
    }

    @Test
    void parseYesNo_emptyInput_usesDefault() {
        assertThat(InteractivePrompter.parseYesNo("", true).value()).isTrue();
        assertThat(InteractivePrompter.parseYesNo("", false).value()).isFalse();
        assertThat(InteractivePrompter.parseYesNo("  ", true).value()).isTrue();
        assertThat(InteractivePrompter.parseYesNo(null, false).value()).isFalse();
        assertThat(InteractivePrompter.parseYesNo("", true).recognized()).isTrue();
    }

    @Test
    void parseYesNo_garbage_notRecognized() {
        var result = InteractivePrompter.parseYesNo("maybe", true);
        assertThat(result.value()).isTrue();
        assertThat(result.recognized()).isFalse();
    }

    // --- None normalization ---

    @Test
    void normalizeNone_variousInputs() {
        assertThat(InteractivePrompter.normalizeNone("none")).isEqualTo("none");
        assertThat(InteractivePrompter.normalizeNone("NONE")).isEqualTo("none");
        assertThat(InteractivePrompter.normalizeNone("None")).isEqualTo("none");
        assertThat(InteractivePrompter.normalizeNone("")).isEqualTo("none");
        assertThat(InteractivePrompter.normalizeNone("   ")).isEqualTo("none");
        assertThat(InteractivePrompter.normalizeNone(null)).isEqualTo("none");
        assertThat(InteractivePrompter.normalizeNone("actual text")).isEqualTo("actual text");
    }

    // --- Impact auto-detection ---

    @Test
    void detectImpactSignals_sqlFiles_detectsDatabase() throws IOException {
        ChangeData data = ChangeData.from(List.of(), "", List.of("db/V1__migration.sql"));
        var prompter = createPrompter("", data);

        assertThat(prompter.detectImpactSignals()).contains("Database(s) wijzigingen");
    }

    @Test
    void detectImpactSignals_tsFiles_detectsFrontend() throws IOException {
        ChangeData data = ChangeData.from(List.of(), "", List.of("src/app.ts"));
        var prompter = createPrompter("", data);

        assertThat(prompter.detectImpactSignals()).contains("Front-end / Javascript");
    }

    @Test
    void detectImpactSignals_multiModule_detectsMultiModule() throws IOException {
        ChangeData data = ChangeData.from(List.of(), "", List.of("module-a/Foo.java", "module-b/Bar.java"));
        var prompter = createPrompter("", data);

        assertThat(prompter.detectImpactSignals()).contains("Impact op meer dan 1 module");
    }

    @Test
    void detectImpactSignals_noSignals_returnsEmpty() throws IOException {
        ChangeData data = ChangeData.from(List.of(), "", List.of());
        var prompter = createPrompter("", data);

        assertThat(prompter.detectImpactSignals()).isEmpty();
    }

    @Test
    void detectImpactSignals_allSignals_returnsAll() throws IOException {
        ChangeData data = ChangeData.from(
                List.of(), "", List.of("module-a/src/Main.java", "module-b/db/V1__init.sql", "frontend/app.tsx"));
        var prompter = createPrompter("", data);

        assertThat(prompter.detectImpactSignals())
                .containsExactlyInAnyOrder(
                        "Database(s) wijzigingen", "Front-end / Javascript", "Impact op meer dan 1 module");
    }

    @Test
    void detectImpactSignals_multiRepo_detectsMultiModule() throws IOException {
        ChangeData data = ChangeData.fromRepoFiles(
                List.of(), "", List.of(new RepoFile("repoA", "src/Foo.java"), new RepoFile("repoB", "src/Bar.java")));
        var prompter = createPrompter("", data);

        assertThat(prompter.detectImpactSignals()).contains("Impact op meer dan 1 module");
    }

    @Test
    void detectImpactSignals_singleRepo_noMultiModule() throws IOException {
        ChangeData data = ChangeData.fromRepoFiles(List.of(), "", List.of(new RepoFile("repoA", "src/Foo.java")));
        var prompter = createPrompter("", data);

        assertThat(prompter.detectImpactSignals()).doesNotContain("Impact op meer dan 1 module");
    }

    // --- Prompt output verification ---

    @Test
    void collect_outputContainsHeaderAndSectionNumbers() throws IOException {
        String output = captureOutput("happy-path.txt", CHANGE_DATA_WITH_DETECTIONS);

        assertThat(output).contains("JIRA INFORMATION");
        assertThat(output).contains("1. Ticket IDs");
        assertThat(output).contains("2. What changed and why?");
        assertThat(output).contains("3. Acceptance criteria");
        assertThat(output).contains("4. Impact areas");
        assertThat(output).contains("5. Test evidence");
        assertThat(output).contains("6. Deployment");
        assertThat(output).contains("7. Definition of Done");
        assertThat(output).contains("8. Fix version");
    }

    @Test
    void collect_autoDetectedImpact_shownInOutput() throws IOException {
        String output = captureOutput("happy-path.txt", CHANGE_DATA_WITH_DETECTIONS);

        assertThat(output).contains("Auto-detected:");
        assertThat(output).contains("☑ Database(s) wijzigingen");
        assertThat(output).contains("☑ Front-end / Javascript");
    }

    @Test
    void collect_yesNoDefaults_displayedCorrectly() throws IOException {
        String output = captureOutput("happy-path.txt", CHANGE_DATA_WITH_DETECTIONS);

        assertThat(output).contains("[Y/n]");
        assertThat(output).contains("[y/N]");
    }

    // --- Multiline reader edge case ---

    @Test
    void readMultiline_emptyImmediately_returnsEmpty() throws IOException {
        var prompter = createPrompter("\n", CHANGE_DATA_EMPTY);
        assertThat(prompter.readMultiline()).isEmpty();
    }

    @Test
    void readMultiline_singleLine_returnsTrimmedContent() throws IOException {
        var prompter = createPrompter("hello\n\n", CHANGE_DATA_EMPTY);
        assertThat(prompter.readMultiline()).isEqualTo("hello");
    }

    // --- Impact areas completeness ---

    @Test
    void collect_allImpactAreas_presentInResult() throws IOException {
        JiraData result = runFixture("happy-path.txt", CHANGE_DATA_WITH_DETECTIONS);

        for (String area : JiraData.Impact.ALL_AREAS) {
            assertThat(result.impact().areas()).containsKey(area);
        }
    }

    // --- Y/N prompt with default yes ---

    @Test
    void promptYesNo_defaultYes_emptyReturnsTrue() throws IOException {
        var prompter = createPrompter("\n", CHANGE_DATA_EMPTY);
        assertThat(prompter.promptYesNo("Test?", true)).isTrue();
    }

    @Test
    void promptYesNo_defaultNo_emptyReturnsFalse() throws IOException {
        var prompter = createPrompter("\n", CHANGE_DATA_EMPTY);
        assertThat(prompter.promptYesNo("Test?", false)).isFalse();
    }

    // --- Helpers ---

    private JiraData runFixture(String fixtureName, ChangeData changeData) throws IOException {
        try (var is = getClass().getResourceAsStream("/fixtures/prompt-sessions/" + fixtureName)) {
            var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            var out = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
            return new InteractivePrompter(reader, out, changeData).collect();
        }
    }

    private String captureOutput(String fixtureName, ChangeData changeData) throws IOException {
        try (var is = getClass().getResourceAsStream("/fixtures/prompt-sessions/" + fixtureName)) {
            var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            var baos = new ByteArrayOutputStream();
            var out = new PrintStream(baos, true, StandardCharsets.UTF_8);
            new InteractivePrompter(reader, out, changeData).collect();
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private InteractivePrompter createPrompter(String input, ChangeData changeData) {
        var reader = new BufferedReader(new StringReader(input));
        var out = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        return new InteractivePrompter(reader, out, changeData);
    }

    private static CommitInfo commit(String hash, String email) {
        return new CommitInfo(hash, "Dev", email, "BE", "TestTeam", "commit msg", Instant.now(), 3, 10, 2);
    }
}
