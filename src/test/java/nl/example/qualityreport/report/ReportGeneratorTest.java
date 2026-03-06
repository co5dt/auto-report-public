package nl.example.qualityreport.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import nl.example.qualityreport.llm.StubLlmProvider;
import nl.example.qualityreport.model.AgentVote;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.CommitInfo;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.model.RiskAssessment;
import nl.example.qualityreport.model.RiskLevel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportGeneratorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 3);

    @Nested
    class FileCreation {

        @Test
        void generatesFileWithExpectedName(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            Path result = gen.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(result.getFileName().toString()).isEqualTo("quality-report-PROJ-128530-2026-03-03.md");
            assertThat(result).exists();
        }

        @Test
        void fileContainsNonEmptyContent(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            Path result = gen.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(Files.readString(result)).isNotBlank();
        }
    }

    @Nested
    class SectionPresence {

        @Test
        void allRequiredSectionsPresent(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            Path result = gen.generate(fullChangeData(), fullJiraData(), unanimousRisk());
            String report = Files.readString(result);

            assertThat(report).contains("# Testrapport");
            assertThat(report).contains("## Betrokken tickets");
            assertThat(report).contains("## Wat is er gewijzigd en waarom?");
            assertThat(report).contains("### Wijzigingen per type");
            assertThat(report).contains("## Wat wordt geraakt?");
            assertThat(report).contains("## Acceptatiecriteria");
            assertThat(report).contains("## Risico-analyse");
            assertThat(report).contains("**Agent deliberation:**");
            assertThat(report).contains("## Wat is getest?");
            assertThat(report).contains("### Handmatig");
            assertThat(report).contains("### Geautomatiseerd");
            assertThat(report).contains("## Uitlevering op productie");
            assertThat(report).contains("## DoD");
        }

        @Test
        void sectionsAreInCorrectOrder(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), fullJiraData(), unanimousRisk()));

            int header = report.indexOf("# Testrapport");
            int tickets = report.indexOf("## Betrokken tickets");
            int changes = report.indexOf("## Wat is er gewijzigd en waarom?");
            int impact = report.indexOf("## Wat wordt geraakt?");
            int ac = report.indexOf("## Acceptatiecriteria");
            int risk = report.indexOf("## Risico-analyse");
            int test = report.indexOf("## Wat is getest?");
            int deploy = report.indexOf("## Uitlevering op productie");
            int dod = report.indexOf("## DoD");

            assertThat(header).isLessThan(tickets);
            assertThat(tickets).isLessThan(changes);
            assertThat(changes).isLessThan(impact);
            assertThat(impact).isLessThan(ac);
            assertThat(ac).isLessThan(risk);
            assertThat(risk).isLessThan(test);
            assertThat(test).isLessThan(deploy);
            assertThat(deploy).isLessThan(dod);
        }
    }

    @Nested
    class HeaderContent {

        @Test
        void headerContainsFixVersionAndTeam(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), fullJiraData(), unanimousRisk()));

            assertThat(report).contains("**Fix version:** 5.13.0");
            assertThat(report).contains("**Team:** Team Alpha");
            assertThat(report).contains("**Datum:** 2026-03-03");
        }

        @Test
        void headerContainsRiskScore(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), fullJiraData(), unanimousRisk()));

            assertThat(report).contains("**Risicoscore:** MEDIUM");
        }
    }

    @Nested
    class DeliberationTable {

        @Test
        void deliberationTableContainsAllAgents(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), fullJiraData(), unanimousRisk()));

            assertThat(report).contains("Diff Analyst");
            assertThat(report).contains("Process Assessor");
            assertThat(report).contains("Evidence Checker");
        }

        @Test
        void unanimousConsensusLabelPresent(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), fullJiraData(), unanimousRisk()));

            assertThat(report).contains("Consensus: unanimous, round 1.");
        }

        @Test
        void highestVoteConsensusLabelPresent(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), fullJiraData(), highestVoteRisk()));

            assertThat(report).contains("Consensus: highest vote (Pattern A), round 3.");
            assertThat(report).contains("**Minority opinion:**");
        }
    }

    @Nested
    class ImpactCheckboxes {

        @Test
        void checkedAndUncheckedAreasRendered(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), fullJiraData(), unanimousRisk()));

            assertThat(report).contains("☑ MuniPortal");
            assertThat(report).contains("☑ Database(s) wijzigingen");
            assertThat(report).contains("☐ Verkiezingen proces");
        }
    }

    @Nested
    class TestEvidenceSection {

        @Test
        void fullTestEvidenceRendered(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), fullJiraData(), unanimousRisk()));

            assertThat(report).contains("Verified locally");
            assertThat(report).contains("42 passed, 0 failed");
            assertThat(report).contains("78%");
        }
    }

    @Nested
    class DeploymentSection {

        @Test
        void deploymentFlagsRendered(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), fullJiraData(), unanimousRisk()));

            assertThat(report).contains("Standaard uitlevering: Ja");
            assertThat(report).contains("Feature toggle: Nee");
            assertThat(report).contains("Handmatig script: Nee");
            assertThat(report).contains("Hypercare: Nee");
        }
    }

    @Nested
    class DoDSection {

        @Test
        void dodCompleteRendered(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), fullJiraData(), unanimousRisk()));

            assertThat(report).contains("☑ DoD is gereed voor alle issues.");
        }

        @Test
        void dodIncompleteRendered(@TempDir Path tempDir) throws IOException {
            JiraData jira = sparseJiraData();
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), jira, unanimousRisk()));

            assertThat(report).contains("☐ DoD is **niet** gereed voor alle issues.");
        }
    }

    @Nested
    class FileCollisionSafety {

        @Test
        void secondRunGetsSuffixedFilename(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            Path first = gen.generate(fullChangeData(), fullJiraData(), unanimousRisk());
            Path second = gen.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(first).exists();
            assertThat(second).exists();
            assertThat(first).isNotEqualTo(second);
            assertThat(second.getFileName().toString()).contains("-2.md");
        }

        @Test
        void thirdRunGetsSuffix3(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            gen.generate(fullChangeData(), fullJiraData(), unanimousRisk());
            gen.generate(fullChangeData(), fullJiraData(), unanimousRisk());
            Path third = gen.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(third.getFileName().toString()).contains("-3.md");
        }
    }

    @Nested
    class EvidenceGaps {

        @Test
        void sparseInputEmitsGapIndicators(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), sparseJiraData(), unanimousRisk()));

            assertThat(report).contains("[evidence gap:");
        }

        @Test
        void sparseAcceptanceCriteriaGap(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), sparseJiraData(), unanimousRisk()));

            assertThat(report).contains("evidence gap: Geen acceptatiecriteria opgegeven");
        }

        @Test
        void sparseManualTestGap(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), sparseJiraData(), unanimousRisk()));

            assertThat(report).contains("evidence gap: Geen handmatige testomschrijving opgegeven");
        }
    }

    @Nested
    class LlmNarrative {

        @Test
        void narrativeFromStubProviderAppearsInReport(@TempDir Path tempDir) throws IOException {
            var stub = new StubLlmProvider(
                    "De cache-invalidatie is aangepast voor GBA-V queries.",
                    "Het grootste risico betreft de database-migratie.");
            var gen = new ReportGenerator(stub, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), fullJiraData(), unanimousRisk()));

            assertThat(report).contains("De cache-invalidatie is aangepast");
            assertThat(report).contains("Het grootste risico betreft de database-migratie");
        }

        @Test
        void nullProviderProducesFallbackGaps(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), fullJiraData(), unanimousRisk()));

            assertThat(report).contains("[evidence gap:");
        }
    }

    @Nested
    class Determinism {

        @Test
        void identicalInputsProduceIdenticalOutput(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String first = Files.readString(gen.generate(fullChangeData(), fullJiraData(), unanimousRisk()));
            String second = Files.readString(gen.generate(fullChangeData(), fullJiraData(), unanimousRisk()));

            assertThat(first).isEqualTo(second);
        }
    }

    @Nested
    class RoleTypeCounts {

        @Test
        void roleCountsFromChangeDataReflected(@TempDir Path tempDir) throws IOException {
            var gen = new ReportGenerator(null, tempDir, DATE);
            String report = Files.readString(gen.generate(fullChangeData(), fullJiraData(), unanimousRisk()));

            assertThat(report).contains("BE-wijzigingen (1 commits, 2 bestanden, +15/-5)");
            assertThat(report).contains("TE-wijzigingen (1 commits, 1 bestanden, +35/-0)");
        }
    }

    // --- fixtures ---

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

    private static ChangeData fullChangeData() {
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
                "diff --git a/f.java b/f.java\n@@ -1 +1 @@\n-old\n+new\n",
                List.of(
                        "src/main/java/PlCache.java",
                        "src/test/java/PlCacheTest.java",
                        "migrations/V42__add_cache_ttl.sql"));
    }

    private static JiraData fullJiraData() {
        return new JiraData(
                List.of("PROJ-128530", "PROJ-128531"),
                "GBA-V query for category 08 returns stale data after PL update.",
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

    private static RiskAssessment unanimousRisk() {
        return RiskAssessment.fromConsensus(
                List.of(
                        new AgentVote("diff-analyst", RiskLevel.MEDIUM, 0.80, "Moderate code impact."),
                        new AgentVote("process-assessor", RiskLevel.MEDIUM, 0.75, "Moderate process impact."),
                        new AgentVote("evidence-checker", RiskLevel.MEDIUM, 0.85, "Evidence quality acceptable.")),
                1);
    }

    private static RiskAssessment highestVoteRisk() {
        return RiskAssessment.fromHighestVote(
                List.of(
                        new AgentVote("diff-analyst", RiskLevel.LOW, 0.72, "Minimal code complexity."),
                        new AgentVote("process-assessor", RiskLevel.MEDIUM, 0.70, "Some process uncertainty."),
                        new AgentVote("evidence-checker", RiskLevel.HIGH, 0.85, "Insufficient validation.")),
                3);
    }
}
