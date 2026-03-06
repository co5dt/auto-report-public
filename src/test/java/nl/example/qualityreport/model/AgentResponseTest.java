package nl.example.qualityreport.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.example.qualityreport.analysis.ResponseParser;
import nl.example.qualityreport.analysis.ResponseParser.ParsedResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AgentResponseTest {

    @Nested
    class ToolLoopEnabled {

        @Test
        void parsesToolRequest() {
            String json =
                    """
                    {"tool_request":{"tool":"read_file","ref":"Foo.java","revision":"branch"}}""";

            ParsedResponse response = ResponseParser.parse("diff-analyst", json, true);

            assertThat(response).isInstanceOf(ParsedResponse.ToolCall.class);
            ToolRequest req = ((ParsedResponse.ToolCall) response).request();
            assertThat(req.tool()).isEqualTo("read_file");
            assertThat(req.ref()).isEqualTo("Foo.java");
        }

        @Test
        void parsesVoteWhenNoToolRequest() {
            String json = """
                    {"vote":"HIGH","confidence":0.9,"reasoning":"critical change"}""";

            ParsedResponse response = ResponseParser.parse("diff-analyst", json, true);

            assertThat(response).isInstanceOf(ParsedResponse.Vote.class);
            AgentVote vote = ((ParsedResponse.Vote) response).vote();
            assertThat(vote.vote()).isEqualTo(RiskLevel.HIGH);
        }
    }

    @Nested
    class ToolLoopDisabled {

        @Test
        void ignoresToolRequestAndParsesAsVoteError() {
            String json =
                    """
                    {"tool_request":{"tool":"read_file","ref":"Foo.java","revision":"branch"}}""";

            assertThatThrownBy(() -> ResponseParser.parse("diff-analyst", json, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Missing field 'vote'");
        }

        @Test
        void parsesNormalVote() {
            String json = """
                    {"vote":"LOW","confidence":0.8,"reasoning":"trivial"}""";

            ParsedResponse response = ResponseParser.parse("diff-analyst", json, false);

            assertThat(response).isInstanceOf(ParsedResponse.Vote.class);
            assertThat(((ParsedResponse.Vote) response).vote().vote()).isEqualTo(RiskLevel.LOW);
        }
    }
}
