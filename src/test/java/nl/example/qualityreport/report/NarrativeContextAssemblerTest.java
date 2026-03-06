package nl.example.qualityreport.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import nl.example.qualityreport.model.*;
import nl.example.qualityreport.model.CommitInfo;
import nl.example.qualityreport.report.evidence.EvidenceBundle;
import nl.example.qualityreport.report.evidence.EvidenceFact;
import nl.example.qualityreport.report.evidence.EvidenceFact.FactType;
import org.junit.jupiter.api.Test;

class NarrativeContextAssemblerTest {

    @Test
    void assembleIncludesMustMentionBlock() {
        var evidence = new EvidenceBundle(List.of(
                new EvidenceFact("t-0", FactType.TICKET, "PROJ-1234", "jira", true),
                new EvidenceFact("c-0", FactType.CLASS_NAME, "CacheConfig", "files", true),
                new EvidenceFact("m-0", FactType.METHOD, "init", "diff", false)));

        String result = NarrativeContextAssembler.assemble("change", changes(), jira(), risk(), evidence);

        assertThat(result).contains("<must_mention>");
        assertThat(result).contains("<identifier>PROJ-1234</identifier>");
        assertThat(result).contains("<identifier>CacheConfig</identifier>");
        assertThat(result).doesNotContain("<identifier>init</identifier>");
    }

    @Test
    void assembleIncludesEvidenceFactsBlock() {
        var evidence = new EvidenceBundle(List.of(new EvidenceFact("t-0", FactType.TICKET, "PROJ-1", "jira", true)));

        String result = NarrativeContextAssembler.assemble("change", changes(), jira(), risk(), evidence);

        assertThat(result).contains("<evidence_facts>");
        assertThat(result).contains("id=\"t-0\"");
        assertThat(result).contains("PROJ-1");
    }

    @Test
    void assembleSelectsHunksContainingMustMentionIdentifiers() {
        String diff =
                """
                diff --git a/CacheConfig.java b/CacheConfig.java
                @@ -0,0 +1,5 @@
                +@Configuration
                +@EnableCaching
                +public class CacheConfig {
                +    // gba-persons cache config
                +}
                """;
        var evidence = new EvidenceBundle(
                List.of(new EvidenceFact("l-0", FactType.ANNOTATION_LITERAL, "gba-persons", "diff", true)));

        String result = NarrativeContextAssembler.assemble("change", changesWithDiff(diff), jira(), risk(), evidence);

        assertThat(result).contains("<selected_hunks>");
        assertThat(result).contains("gba-persons");
    }

    @Test
    void emptyEvidenceProducesNoMustMentionBlock() {
        var evidence = new EvidenceBundle(List.of());

        String result = NarrativeContextAssembler.assemble("change", changes(), jira(), risk(), evidence);

        assertThat(result).doesNotContain("<must_mention>");
        assertThat(result).doesNotContain("<evidence_facts>");
    }

    @Test
    void riskTypeIncludesAcceptanceCriteria() {
        var evidence = new EvidenceBundle(List.of());

        String result = NarrativeContextAssembler.assemble(
                "risk", changes(), jiraWithCriteria("All tests must pass"), risk(), evidence);

        assertThat(result).contains("<acceptance_criteria>");
        assertThat(result).contains("All tests must pass");
    }

    @Test
    void deletionDominantChangeIncludesStats() {
        CommitInfo delCommit = new CommitInfo(
                "del1", "Dev", "dev@test.nl", "BE", "Team", "chore: remove module", java.time.Instant.now(), 3, 0, 200);
        ChangeData delChanges = ChangeData.from(List.of(delCommit), "", List.of("Legacy.java"));
        var evidence = new EvidenceBundle(List.of());

        String result = NarrativeContextAssembler.assemble("change", delChanges, jira(), risk(), evidence);

        assertThat(result).contains("<total_insertions>0</total_insertions>");
        assertThat(result).contains("<total_deletions>200</total_deletions>");
    }

    // --- Helpers ---

    private static ChangeData changes() {
        return ChangeData.from(List.of(), "", List.of());
    }

    private static ChangeData changesWithDiff(String diff) {
        return ChangeData.from(List.of(), diff, List.of("CacheConfig.java"));
    }

    private static JiraData jira() {
        return new JiraData(
                List.of("PROJ-1"),
                "desc",
                "",
                new JiraData.Impact(Map.of()),
                JiraData.TestEvidence.none(),
                JiraData.Deployment.defaults(),
                true,
                "1.0");
    }

    private static JiraData jiraWithCriteria(String criteria) {
        return new JiraData(
                List.of("PROJ-1"),
                "desc",
                criteria,
                new JiraData.Impact(Map.of()),
                JiraData.TestEvidence.none(),
                JiraData.Deployment.defaults(),
                true,
                "1.0");
    }

    private static RiskAssessment risk() {
        return RiskAssessment.fromConsensus(List.of(new AgentVote("agent-1", RiskLevel.LOW, 0.9, "No risk.")), 1);
    }
}
