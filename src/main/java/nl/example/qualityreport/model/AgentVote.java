package nl.example.qualityreport.model;

import nl.example.qualityreport.analysis.ResponseParser;

public record AgentVote(String agent, RiskLevel vote, double confidence, String reasoning) {
    /**
     * Delegates to {@link ResponseParser} for JSON parsing and validation.
     */
    public static AgentVote parse(String agentName, String responseJson) {
        String cleaned = ResponseParser.stripCodeFences(responseJson);
        return ResponseParser.parseVote(agentName, cleaned);
    }

    /**
     * @deprecated Use {@link ResponseParser#stripCodeFences(String)} directly.
     */
    @Deprecated(forRemoval = true)
    static String stripCodeFences(String raw) {
        return ResponseParser.stripCodeFences(raw);
    }
}
