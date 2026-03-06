package nl.example.qualityreport.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.CommitInfo;
import nl.example.qualityreport.model.JiraData;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ContextBuilderTest {

    private final ContextBuilder builder = new ContextBuilder();

    private static String loadFixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/fixtures/context/" + name));
    }

    private static CommitInfo commit(
            String hash,
            String author,
            String email,
            String role,
            String team,
            String message,
            int files,
            int ins,
            int del) {
        return new CommitInfo(
                hash, author, email, role, team, message, Instant.parse("2025-01-15T10:00:00Z"), files, ins, del);
    }

    private static ChangeData smallChangeData() throws IOException {
        String diff = loadFixture("small-diff.patch");
        return ChangeData.from(
                List.of(
                        commit(
                                "a1b2c3d",
                                "Alice Dev",
                                "alice@example.nl",
                                "BE",
                                "Team Alpha",
                                "fix: invalidate cache on PL update",
                                2,
                                15,
                                5),
                        commit(
                                "e4f5g6h",
                                "Bob Test",
                                "bob@example.nl",
                                "TE",
                                "Team Alpha",
                                "test: add cache invalidation tests",
                                1,
                                35,
                                0)),
                diff,
                List.of("src/main/java/nl/example/cache/PlCache.java", "src/test/java/nl/example/cache/PlCacheTest.java"));
    }

    private static ChangeData largeChangeData() throws IOException {
        String diff = loadFixture("large-diff.patch");
        return ChangeData.from(
                List.of(
                        commit(
                                "a1b2c3d",
                                "Alice Dev",
                                "alice@example.nl",
                                "BE",
                                "Team Alpha",
                                "feat: add cache TTL and metrics",
                                5,
                                120,
                                30),
                        commit(
                                "e4f5g6h",
                                "Bob Test",
                                "bob@example.nl",
                                "TE",
                                "Team Alpha",
                                "test: add cache and query tests",
                                3,
                                90,
                                5),
                        commit(
                                "i7j8k9l",
                                "Charlie Sec",
                                "charlie@example.nl",
                                "BE",
                                "Team Beta",
                                "feat: add token revocation",
                                2,
                                40,
                                10)),
                diff,
                List.of(
                        "migrations/V42__add_cache_ttl_column.sql",
                        "src/main/java/nl/example/cache/PlCache.java",
                        "src/main/java/nl/example/query/GbaVQueryHandler.java",
                        "src/main/java/nl/example/auth/TokenValidator.java",
                        "src/main/java/nl/example/config/CacheConfig.java",
                        "src/main/java/nl/example/metrics/MetricsCollector.java",
                        "src/main/java/nl/example/event/CacheEventPublisher.java",
                        "src/main/java/nl/example/service/DataSliceService.java",
                        "src/main/java/nl/example/service/FeatureFlags.java",
                        "src/main/java/nl/example/model/QueryResult.java",
                        "src/test/java/nl/example/cache/PlCacheTest.java",
                        "src/test/java/nl/example/query/GbaVQueryHandlerTest.java",
                        "src/test/java/nl/example/service/DataSliceServiceTest.java",
                        "src/test/java/nl/example/auth/TokenValidatorTest.java"));
    }

    private static JiraData fullJiraData() {
        return new JiraData(
                List.of("PROJ-128530", "PROJ-128531"),
                "The GBA-V query for category 08 returns stale data after PL update.",
                "- After PL update, subsequent query returns fresh data\n- Cache TTL respects configured window",
                new JiraData.Impact(Map.of(
                        "MuniPortal", true,
                        "Database(s) wijzigingen", true,
                        "Front-end / Javascript", false)),
                new JiraData.TestEvidence(
                        "Verified locally that fresh data is returned", true, "42 passed, 0 failed", "78%"),
                new JiraData.Deployment(true, false, false, false),
                true,
                "5.13.0");
    }

    private static JiraData sparseJiraData() {
        return new JiraData(
                List.of("PROJ-99999"),
                "none",
                "none",
                new JiraData.Impact(Map.of()),
                JiraData.TestEvidence.none(),
                JiraData.Deployment.defaults(),
                false,
                "");
    }

    @Nested
    class SectionPresence {

        @Test
        void allRequiredSectionsPresent() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());

            assertThat(xml).contains("<context>");
            assertThat(xml).contains("</context>");
            assertThat(xml).contains("<change_overview>");
            assertThat(xml).contains("</change_overview>");
            assertThat(xml).contains("<jira>");
            assertThat(xml).contains("</jira>");
            assertThat(xml).contains("<diff_content");
            assertThat(xml).contains("</diff_content>");
            assertThat(xml).contains("<impact>");
            assertThat(xml).contains("</impact>");
            assertThat(xml).contains("<test_evidence>");
            assertThat(xml).contains("</test_evidence>");
            assertThat(xml).contains("<deployment>");
            assertThat(xml).contains("</deployment>");
        }

        @Test
        void changeOverviewContainsSubsections() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());

            assertThat(xml).contains("<tickets>");
            assertThat(xml).contains("<fix_version>");
            assertThat(xml).contains("<authors>");
            assertThat(xml).contains("<stats>");
            assertThat(xml).contains("<changes_by_role>");
            assertThat(xml).contains("<changes_by_type>");
            assertThat(xml).contains("<commit_messages>");
        }
    }

    @Nested
    class FullDiffMode {

        @Test
        void smallDiffUsesFullMode() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            assertThat(xml).contains("mode=\"full\"");
            assertThat(xml).contains("<raw_diff>");
        }

        @Test
        void fullModeContainsDiffContent() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            assertThat(xml).contains("invalidate");
        }
    }

    @Nested
    class SummaryDiffMode {

        @Test
        void largeDiffUsesSummaryMode() throws IOException {
            String xml = builder.build(largeChangeData(), fullJiraData());
            assertThat(xml).contains("mode=\"summary\"");
            assertThat(xml).contains("<files_changed");
            assertThat(xml).contains("<high_signal_hunks>");
        }

        @Test
        void summaryContainsFilesChangedEntries() throws IOException {
            String xml = builder.build(largeChangeData(), fullJiraData());
            assertThat(xml).contains("path=\"migrations/V42__add_cache_ttl_column.sql\"");
        }

        @Test
        void summaryContainsHighSignalHunks() throws IOException {
            String xml = builder.build(largeChangeData(), fullJiraData());
            assertThat(xml).contains("<hunk ");
            assertThat(xml).contains("reason=\"");
        }
    }

    @Nested
    class JiraSection {

        @Test
        void fullJiraDataPreservesDescription() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            assertThat(xml).contains("GBA-V query for category 08");
        }

        @Test
        void fullJiraDataPreservesAcceptanceCriteria() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            assertThat(xml).contains("After PL update");
            assertThat(xml).contains("Cache TTL respects");
        }

        @Test
        void ticketsAreListed() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            assertThat(xml).contains("PROJ-128530");
            assertThat(xml).contains("PROJ-128531");
        }
    }

    @Nested
    class NoDataBehavior {

        @Test
        void sparseJiraEmitsNoDataForDescription() throws IOException {
            String xml = builder.build(smallChangeData(), sparseJiraData());
            assertThat(xml).contains("<description><no_data reason=");
        }

        @Test
        void sparseJiraEmitsNoDataForAcceptanceCriteria() throws IOException {
            String xml = builder.build(smallChangeData(), sparseJiraData());
            assertThat(xml).contains("<acceptance_criteria><no_data reason=");
        }

        @Test
        void sparseJiraEmitsNoDataForManualTesting() throws IOException {
            String xml = builder.build(smallChangeData(), sparseJiraData());
            assertThat(xml).contains("<manual_testing><no_data reason=");
        }

        @Test
        void sparseJiraEmitsNoDataForFixVersion() throws IOException {
            String xml = builder.build(smallChangeData(), sparseJiraData());
            assertThat(xml).contains("<fix_version><no_data reason=");
        }
    }

    @Nested
    class XmlEscaping {

        @Test
        void specialCharsInDescriptionAreEscaped() throws IOException {
            JiraData jira = new JiraData(
                    List.of("TEST-1"),
                    "Check <script> & 'quotes' in \"description\"",
                    "AC with <b>bold</b> & ampersand",
                    new JiraData.Impact(Map.of()),
                    JiraData.TestEvidence.none(),
                    JiraData.Deployment.defaults(),
                    true,
                    "1.0");

            String xml = builder.build(smallChangeData(), jira);
            assertThat(xml).contains("&lt;script&gt;");
            assertThat(xml).contains("&amp;");
            assertThat(xml).contains("&apos;quotes&apos;");
            assertThat(xml).contains("&lt;b&gt;bold&lt;/b&gt;");
        }

        @Test
        void specialCharsInCommitMessagesAreEscaped() {
            ChangeData changes = ChangeData.from(
                    List.of(commit(
                            "abc123", "Dev", "dev@test.nl", "BE", "Team", "fix: handle <null> & edge cases", 1, 5, 2)),
                    "diff --git a/f.txt b/f.txt\n--- a/f.txt\n+++ b/f.txt\n@@ -1 +1 @@\n-old\n+new\n",
                    List.of("f.txt"));

            String xml = builder.build(changes, fullJiraData());
            assertThat(xml).contains("&lt;null&gt;");
            assertThat(xml).contains("&amp; edge cases");
        }
    }

    @Nested
    class ImpactSection {

        @Test
        void allImpactAreasPresent() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());

            for (String area : JiraData.Impact.ALL_AREAS) {
                assertThat(xml).contains("name=\"" + XmlUtils.escapeXml(area) + "\"");
            }
        }

        @Test
        void checkedAreasReflectInput() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            assertThat(xml).contains("name=\"MuniPortal\" checked=\"true\"");
            assertThat(xml).contains("name=\"Front-end / Javascript\" checked=\"false\"");
        }

        @Test
        void crossTeamSignalPresent() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            assertThat(xml).contains("<cross_team_signal detected=");
        }
    }

    @Nested
    class TestEvidenceSection {

        @Test
        void fullTestEvidencePreservesContent() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            assertThat(xml).contains("Verified locally");
            assertThat(xml).contains("42 passed, 0 failed");
            assertThat(xml).contains("78%");
        }
    }

    @Nested
    class DeploymentSection {

        @Test
        void deploymentFlagsPresent() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            assertThat(xml).contains("<standard_rollout>true</standard_rollout>");
            assertThat(xml).contains("<feature_toggle>false</feature_toggle>");
            assertThat(xml).contains("<manual_script_required>false</manual_script_required>");
            assertThat(xml).contains("<hypercare_needed>false</hypercare_needed>");
        }
    }

    @Nested
    class Determinism {

        @Test
        void identicalInputsProduceIdenticalOutput() throws IOException {
            ChangeData changes = smallChangeData();
            JiraData jira = fullJiraData();

            String first = builder.build(changes, jira);
            String second = builder.build(changes, jira);
            assertThat(first).isEqualTo(second);
        }

        @Test
        void repeatedCallsWithLargeDiffAreDeterministic() throws IOException {
            ChangeData changes = largeChangeData();
            JiraData jira = fullJiraData();

            String first = builder.build(changes, jira);
            String second = builder.build(changes, jira);
            assertThat(first).isEqualTo(second);
        }
    }

    @Nested
    class ChangeOverviewDetails {

        @Test
        void authorsAreSortedByName() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            int aliceIdx = xml.indexOf("Alice Dev");
            int bobIdx = xml.indexOf("Bob Test");
            assertThat(aliceIdx).isLessThan(bobIdx);
        }

        @Test
        void statsAreComputedCorrectly() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            assertThat(xml).contains("<total_commits>2</total_commits>");
            assertThat(xml).contains("<total_files_changed>2</total_files_changed>");
            assertThat(xml).contains("<total_insertions>50</total_insertions>");
            assertThat(xml).contains("<total_deletions>5</total_deletions>");
        }

        @Test
        void fixVersionFromJira() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            assertThat(xml).contains("<fix_version>5.13.0</fix_version>");
        }

        @Test
        void commitMessagesSortedByDate() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            assertThat(xml).contains("fix: invalidate cache on PL update");
            assertThat(xml).contains("test: add cache invalidation tests");
        }

        @Test
        void changesByRolePresent() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            assertThat(xml).contains("name=\"BE\"");
            assertThat(xml).contains("name=\"TE\"");
        }
    }

    @Nested
    class EmptyDiff {

        @Test
        void emptyDiffProducesNoDataInDiffSection() {
            ChangeData changes = ChangeData.from(List.of(), "", List.of());
            String xml = builder.build(changes, fullJiraData());
            assertThat(xml).contains("mode=\"full\"");
            assertThat(xml).contains("<no_data reason=");
        }
    }

    @Nested
    class DodNotInContext {

        @Test
        void dodCompleteIsNotInContextXml() throws IOException {
            String xml = builder.build(smallChangeData(), fullJiraData());
            assertThat(xml).doesNotContain("dod");
            assertThat(xml).doesNotContain("definition_of_done");
        }

        @Test
        void dodIncompleteAlsoNotInContextXml() throws IOException {
            String xml = builder.build(smallChangeData(), sparseJiraData());
            assertThat(xml).doesNotContain("dod");
        }
    }

    @Nested
    class AllDeletionChange {

        @Test
        void allDeletionChangeProducesValidContext() {
            CommitInfo deleteCommit =
                    commit("del1", "Dev", "dev@example.nl", "BE", "Team Alpha", "chore: remove legacy module", 3, 0, 120);
            String diff =
                    "diff --git a/Legacy.java b/Legacy.java\n--- a/Legacy.java\n+++ /dev/null\n@@ -1,50 +0,0 @@\n-public class Legacy {}\n";
            ChangeData changes = ChangeData.from(List.of(deleteCommit), diff, List.of("Legacy.java"));

            String xml = builder.build(changes, fullJiraData());
            assertThat(xml).contains("<total_insertions>0</total_insertions>");
            assertThat(xml).contains("<total_deletions>120</total_deletions>");
            assertThat(xml).contains("<context>");
        }
    }
}
