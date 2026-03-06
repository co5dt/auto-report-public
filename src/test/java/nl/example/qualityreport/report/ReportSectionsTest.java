package nl.example.qualityreport.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import nl.example.qualityreport.model.AgentVote;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.CommitInfo;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.model.RiskAssessment;
import nl.example.qualityreport.model.RiskLevel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReportSectionsTest {

    @Nested
    class FormatAgentName {

        @Test
        void mapsKnownAgentNames() {
            assertThat(ReportSections.formatAgentName("diff-analyst")).isEqualTo("Diff Analyst");
            assertThat(ReportSections.formatAgentName("process-assessor")).isEqualTo("Process Assessor");
            assertThat(ReportSections.formatAgentName("evidence-checker")).isEqualTo("Evidence Checker");
        }

        @Test
        void passesUnknownNamesThrough() {
            assertThat(ReportSections.formatAgentName("custom-agent")).isEqualTo("custom-agent");
        }

        @Test
        void handlesNull() {
            assertThat(ReportSections.formatAgentName(null)).isEqualTo("Unknown");
        }
    }

    @Nested
    class BlankOrNone {

        @Test
        void detectsBlanks() {
            assertThat(ReportSections.isBlankOrNone(null)).isTrue();
            assertThat(ReportSections.isBlankOrNone("")).isTrue();
            assertThat(ReportSections.isBlankOrNone("   ")).isTrue();
            assertThat(ReportSections.isBlankOrNone("none")).isTrue();
            assertThat(ReportSections.isBlankOrNone("NONE")).isTrue();
            assertThat(ReportSections.isBlankOrNone(" None ")).isTrue();
        }

        @Test
        void passesRealValues() {
            assertThat(ReportSections.isBlankOrNone("actual data")).isFalse();
        }
    }

    @Nested
    class ImpactChecklistSection {

        @Test
        void allElevenAreasRendered() {
            JiraData jira = fullJiraData();
            String section = ReportSections.impactChecklist(jira);

            for (String area : JiraData.Impact.ALL_AREAS) {
                assertThat(section).contains(area);
            }
        }

        @Test
        void checkedUncheckedCorrect() {
            JiraData jira = fullJiraData();
            String section = ReportSections.impactChecklist(jira);

            assertThat(section).contains("☑ MuniPortal");
            assertThat(section).contains("☐ Verkiezingen proces");
        }
    }

    @Nested
    class DeliberationTableSection {

        @Test
        void tableHasHeaderAndRows() {
            RiskAssessment risk = unanimousRisk();
            String table = ReportSections.deliberationTable(risk);

            assertThat(table).contains("| Agent | Vote | Confidence | Key reasoning |");
            assertThat(table).contains("| Diff Analyst | MEDIUM |");
            assertThat(table).contains("| Process Assessor | MEDIUM |");
            assertThat(table).contains("| Evidence Checker | MEDIUM |");
        }

        @Test
        void consensusLabelRendered() {
            String table = ReportSections.deliberationTable(unanimousRisk());
            assertThat(table).contains("Consensus: unanimous, round 1.");
        }
    }

    @Nested
    class SingleAgentDeliberation {

        @Test
        void singleAgentSaysSingleAgentNotUnanimous() {
            RiskAssessment singleAgent = RiskAssessment.fromConsensus(
                    List.of(new AgentVote("diff-analyst", RiskLevel.LOW, 0.9, "Trivial change.")), 1);
            String table = ReportSections.deliberationTable(singleAgent);
            assertThat(table).contains("Consensus: single agent, round 1.");
            assertThat(table).doesNotContain("unanimous");
        }

        @Test
        void threeAgentsStillSaysUnanimous() {
            String table = ReportSections.deliberationTable(unanimousRisk());
            assertThat(table).contains("Consensus: unanimous, round 1.");
        }
    }

    @Nested
    class UnknownTeamDetection {

        @Test
        void allUnknownTeam_showsOnbekendWithNote() {
            ChangeData data = ChangeData.from(
                    List.of(new CommitInfo(
                            "abc", "Ext", "ext@other.com", "unknown", "unknown", "msg", Instant.now(), 1, 5, 2)),
                    "",
                    List.of());
            assertThat(ReportSections.detectTeam(data)).isEqualTo("Onbekend (geen roster-match)");
        }
    }

    @Nested
    class TestEvidenceGaps {

        @Test
        void noManualTestEmitsGap() {
            JiraData sparse = sparseJiraData();
            String section = ReportSections.testEvidence(sparse);

            assertThat(section).contains("[evidence gap:");
        }
    }

    @Nested
    class DoDRendering {

        @Test
        void dodCompleteShowsCheck() {
            JiraData jira = fullJiraData();
            assertThat(ReportSections.dod(jira)).contains("☑ DoD is gereed");
        }

        @Test
        void dodIncompleteShowsUncheck() {
            JiraData sparse = sparseJiraData();
            assertThat(ReportSections.dod(sparse)).contains("☐ DoD is **niet** gereed");
        }
    }

    @Nested
    class TeamDetection {

        @Test
        void singleTeam_showsTeamName() {
            ChangeData data = ChangeData.from(List.of(commit("Team Alpha"), commit("Team Alpha")), "", List.of());
            assertThat(ReportSections.detectTeam(data)).isEqualTo("Team Alpha");
        }

        @Test
        void multipleTeams_showsAllTeamsSortedByFrequency() {
            ChangeData data = ChangeData.from(
                    List.of(commit("Team Alpha"), commit("Team Alpha"), commit("Team Delta")), "", List.of());
            String result = ReportSections.detectTeam(data);
            assertThat(result).contains("Team Alpha");
            assertThat(result).contains("Team Delta");
            assertThat(result).startsWith("Team Alpha");
        }

        @Test
        void noCommits_showsUnknown() {
            ChangeData data = ChangeData.from(List.of(), "", List.of());
            assertThat(ReportSections.detectTeam(data)).isEqualTo("Onbekend");
        }

        private CommitInfo commit(String team) {
            return new CommitInfo("abc", "Dev", "dev@test.nl", "BE", team, "msg", Instant.now(), 1, 5, 2);
        }
    }

    // --- fixtures ---

    private static JiraData fullJiraData() {
        return new JiraData(
                List.of("PROJ-128530"),
                "Fix stale cache",
                "Cache returns fresh data after PL update",
                new JiraData.Impact(Map.of("MuniPortal", true)),
                new JiraData.TestEvidence("Manual test done", true, "10 passed", "80%"),
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
}
