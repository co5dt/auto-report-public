package nl.example.qualityreport.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.example.qualityreport.analysis.ResponseParser;
import org.junit.jupiter.api.Test;

class AgentVoteTest {

    @Test
    void parsesValidVotePayload() {
        AgentVote vote = AgentVote.parse(
                "diff-analyst", "{\"vote\":\"HIGH\",\"confidence\":0.91,\"reasoning\":\"Critical path touched\"}");

        assertThat(vote.agent()).isEqualTo("diff-analyst");
        assertThat(vote.vote()).isEqualTo(RiskLevel.HIGH);
        assertThat(vote.confidence()).isEqualTo(0.91);
        assertThat(vote.reasoning()).isEqualTo("Critical path touched");
    }

    @Test
    void rejectsMissingField() {
        assertThatThrownBy(() ->
                        AgentVote.parse("process-assessor", "{\"confidence\":0.5,\"reasoning\":\"Missing vote\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing field 'vote'");
    }

    @Test
    void rejectsUnknownVoteValue() {
        assertThatThrownBy(() -> AgentVote.parse(
                        "evidence-checker", "{\"vote\":\"SEVERE\",\"confidence\":0.5,\"reasoning\":\"Unknown enum\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported vote");
    }

    @Test
    void parsesVoteWrappedInMarkdownCodeFences() {
        String fenced =
                """
                ```json
                {"vote":"MEDIUM","confidence":0.75,"reasoning":"Moderate risk"}
                ```""";

        AgentVote vote = AgentVote.parse("diff-analyst", fenced);

        assertThat(vote.vote()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(vote.confidence()).isEqualTo(0.75);
        assertThat(vote.reasoning()).isEqualTo("Moderate risk");
    }

    @Test
    void parsesVoteWithPlainCodeFences() {
        String fenced =
                """
                ```
                {"vote":"LOW","confidence":0.6,"reasoning":"Minor change"}
                ```""";

        AgentVote vote = AgentVote.parse("evidence-checker", fenced);

        assertThat(vote.vote()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void stripCodeFencesPassesThroughPlainJson() {
        String plain = "{\"vote\":\"HIGH\"}";
        assertThat(ResponseParser.stripCodeFences(plain)).isEqualTo(plain);
    }

    @Test
    void rejectsOutOfRangeConfidence() {
        assertThatThrownBy(() -> AgentVote.parse(
                        "diff-analyst", "{\"vote\":\"LOW\",\"confidence\":1.2,\"reasoning\":\"Too high confidence\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }
}
