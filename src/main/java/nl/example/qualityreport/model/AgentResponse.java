package nl.example.qualityreport.model;

import nl.example.qualityreport.analysis.ResponseParser;

/**
 * Discriminated result of parsing an agent's raw LLM response.
 * Either a final {@link AgentVote} or a {@link ToolRequest}.
 *
 * @deprecated Use {@link ResponseParser.ParsedResponse} directly.
 *             Retained for backward compatibility during migration.
 */
@Deprecated(forRemoval = true)
public sealed interface AgentResponse {

    record Vote(AgentVote vote) implements AgentResponse {}

    record ToolCall(ToolRequest request) implements AgentResponse {}

    static AgentResponse parse(String agentName, String rawResponse, boolean toolLoopEnabled) {
        ResponseParser.ParsedResponse parsed = ResponseParser.parse(agentName, rawResponse, toolLoopEnabled);

        if (parsed instanceof ResponseParser.ParsedResponse.Vote v) {
            return new Vote(v.vote());
        }
        if (parsed instanceof ResponseParser.ParsedResponse.ToolCall tc) {
            return new ToolCall(tc.request());
        }
        throw new IllegalStateException("Unexpected parsed response type: " + parsed.getClass());
    }
}
