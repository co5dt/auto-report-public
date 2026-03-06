package nl.example.qualityreport.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import nl.example.qualityreport.context.ContextBuilder;
import nl.example.qualityreport.llm.LlmProvider;
import nl.example.qualityreport.llm.LlmProviderException;
import nl.example.qualityreport.llm.RecordingLlmProvider;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.CommitInfo;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.model.RiskAssessment;
import nl.example.qualityreport.model.RiskLevel;
import org.junit.jupiter.api.Test;

class AgentDeliberationTest {

    @Test
    void unanimousInRound1StopsAfterThreeCalls() throws IOException {
        RecordingLlmProvider llm = new RecordingLlmProvider(loadFixtureLines("unanimous-round1.jsonl"));
        AgentDeliberation deliberation = new AgentDeliberation(llm);

        RiskAssessment result = deliberation.assess(changeData(), jiraData());

        assertThat(result.finalRisk()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.roundReached()).isEqualTo(1);
        assertThat(result.consensusType()).isEqualTo(RiskAssessment.ConsensusType.UNANIMOUS);
        assertThat(result.agentVotes()).hasSize(3);
        assertThat(result.minorityOpinion()).isEmpty();
        assertThat(llm.callCount()).isEqualTo(3);
        assertRoundOrder(llm.systemPrompts(), 1);
        assertThat(llm.userMessages()).allMatch(m -> !m.contains("<prior_votes"));
    }

    @Test
    void disagreementInRound1ThenConsensusInRound2() throws IOException {
        RecordingLlmProvider llm = new RecordingLlmProvider(loadFixtureLines("round2-consensus.jsonl"));
        AgentDeliberation deliberation = new AgentDeliberation(llm);

        RiskAssessment result = deliberation.assess(changeData(), jiraData());

        assertThat(result.finalRisk()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.roundReached()).isEqualTo(2);
        assertThat(result.consensusType()).isEqualTo(RiskAssessment.ConsensusType.UNANIMOUS);
        assertThat(llm.callCount()).isEqualTo(6);
        assertRoundOrder(llm.systemPrompts(), 2);
        assertThat(llm.userMessages().subList(0, 3)).allMatch(m -> !m.contains("<prior_votes"));
        assertThat(llm.userMessages().subList(3, 6)).allMatch(m -> m.contains("<prior_votes round=\"1\">"));
    }

    @Test
    void disagreementThroughRound3UsesPatternAHighestVote() throws IOException {
        RecordingLlmProvider llm = new RecordingLlmProvider(loadFixtureLines("round3-deadlock.jsonl"));
        AgentDeliberation deliberation = new AgentDeliberation(llm);

        RiskAssessment result = deliberation.assess(changeData(), jiraData());

        assertThat(result.finalRisk()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.roundReached()).isEqualTo(3);
        assertThat(result.consensusType()).isEqualTo(RiskAssessment.ConsensusType.HIGHEST);
        assertThat(result.agentVotes()).hasSize(3);
        assertThat(result.minorityOpinion()).contains("diff-analyst");
        assertThat(result.minorityOpinion()).contains("process-assessor");
        assertThat(llm.callCount()).isEqualTo(9);
        assertRoundOrder(llm.systemPrompts(), 3);
        assertThat(llm.userMessages().subList(6, 9)).allMatch(m -> m.contains("<prior_votes round=\"2\">"));
    }

    @Test
    void malformedAgentJsonFailsWithClearContext() throws IOException {
        RecordingLlmProvider llm = new RecordingLlmProvider(loadFixtureLines("malformed-round1.jsonl"));
        AgentDeliberation deliberation = new AgentDeliberation(llm);

        assertThatThrownBy(() -> deliberation.assess(changeData(), jiraData()))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("Failed to parse vote")
                .hasMessageContaining("round 1");
    }

    @Test
    void missingPromptResourceFailsClearly() {
        RecordingLlmProvider llm =
                new RecordingLlmProvider(List.of("{\"vote\":\"LOW\",\"confidence\":0.5,\"reasoning\":\"unused\"}"));
        AgentDeliberation deliberation = new AgentDeliberation(llm, new ContextBuilder(), ignored -> {
            throw new IllegalStateException("Missing prompt resource: /prompts/diff-analyst.txt");
        });

        assertThatThrownBy(() -> deliberation.assess(changeData(), jiraData()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing prompt resource");
    }

    @Test
    void providerFailureBubblesOut() {
        LlmProvider failing = (systemPrompt, userMessage) -> {
            throw new RuntimeException("timeout");
        };
        AgentDeliberation deliberation = new AgentDeliberation(failing);

        assertThatThrownBy(() -> deliberation.assess(changeData(), jiraData()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void singleAgentReturnsUnanimousInRound1() {
        String vote = "{\"vote\":\"LOW\",\"confidence\":0.9,\"reasoning\":\"trivial change\"}";
        RecordingLlmProvider llm = new RecordingLlmProvider(List.of(vote));
        AgentDeliberation deliberation =
                new AgentDeliberation(llm, new ContextBuilder(), name -> "prompt for " + name, 1);

        RiskAssessment result = deliberation.assess(changeData(), jiraData());

        assertThat(result.finalRisk()).isEqualTo(RiskLevel.LOW);
        assertThat(result.roundReached()).isEqualTo(1);
        assertThat(result.consensusType()).isEqualTo(RiskAssessment.ConsensusType.UNANIMOUS);
        assertThat(result.agentVotes()).hasSize(1);
        assertThat(result.agentVotes().getFirst().agent()).isEqualTo("diff-analyst");
        assertThat(llm.callCount()).isEqualTo(1);
    }

    @Test
    void twoAgentConsensusInRound1() {
        String low = "{\"vote\":\"MEDIUM\",\"confidence\":0.8,\"reasoning\":\"moderate risk\"}";
        RecordingLlmProvider llm = new RecordingLlmProvider(List.of(low, low));
        AgentDeliberation deliberation =
                new AgentDeliberation(llm, new ContextBuilder(), name -> "prompt for " + name, 2);

        RiskAssessment result = deliberation.assess(changeData(), jiraData());

        assertThat(result.finalRisk()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.roundReached()).isEqualTo(1);
        assertThat(result.consensusType()).isEqualTo(RiskAssessment.ConsensusType.UNANIMOUS);
        assertThat(result.agentVotes()).hasSize(2);
        assertThat(llm.callCount()).isEqualTo(2);
    }

    @Test
    void agentCountZeroIsRejected() {
        RecordingLlmProvider llm = new RecordingLlmProvider(List.of());
        assertThatThrownBy(() -> new AgentDeliberation(llm, new ContextBuilder(), name -> "prompt", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentCount must be between 1 and 3");
    }

    @Test
    void agentCountAboveThreeIsRejected() {
        RecordingLlmProvider llm = new RecordingLlmProvider(List.of());
        assertThatThrownBy(() -> new AgentDeliberation(llm, new ContextBuilder(), name -> "prompt", 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentCount must be between 1 and 3");
    }

    private static void assertRoundOrder(List<String> systemPrompts, int rounds) {
        assertThat(systemPrompts).hasSize(rounds * 3);
        for (int i = 0; i < rounds; i++) {
            int offset = i * 3;
            assertThat(systemPrompts.get(offset)).contains("Diff Analyst");
            assertThat(systemPrompts.get(offset + 1)).contains("Process Assessor");
            assertThat(systemPrompts.get(offset + 2)).contains("Evidence Checker");
        }
    }

    private static List<String> loadFixtureLines(String file) throws IOException {
        return Files.readAllLines(Path.of("src/test/resources/fixtures/analysis/" + file)).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private static ChangeData changeData() {
        CommitInfo commit = new CommitInfo(
                "abc1234",
                "Alice",
                "alice@example.nl",
                "BE",
                "Team Alpha",
                "feat: add additional validation",
                Instant.parse("2026-01-10T10:00:00Z"),
                2,
                30,
                10);

        String rawDiff =
                """
                diff --git a/src/main/java/Foo.java b/src/main/java/Foo.java
                --- a/src/main/java/Foo.java
                +++ b/src/main/java/Foo.java
                @@ -1 +1,2 @@
                -return old();
                +validate(input);
                +return newer();
                """;

        return ChangeData.from(List.of(commit), rawDiff, List.of("src/main/java/Foo.java"));
    }

    private static JiraData jiraData() {
        return new JiraData(
                List.of("PROJ-12345"),
                "Improve validation flow",
                "Validation handles null and malformed payloads",
                new JiraData.Impact(Map.of("Database(s) wijzigingen", false)),
                new JiraData.TestEvidence("Manual smoke tested", true, "22 passed, 0 failed", "72%"),
                JiraData.Deployment.defaults(),
                true,
                "1.2.3");
    }
}
