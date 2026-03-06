package nl.example.qualityreport.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Pattern;
import nl.example.qualityreport.model.AgentVote;
import nl.example.qualityreport.model.RiskLevel;
import nl.example.qualityreport.model.ToolRequest;

/**
 * Parses raw LLM responses into typed results (votes or tool requests).
 * Consolidates all code-fence stripping, JSON extraction, and field
 * validation that was previously scattered across model records.
 */
public final class ResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern THINK_BLOCK = Pattern.compile("<think>.*?</think>\\s*", Pattern.DOTALL);

    private ResponseParser() {}

    /**
     * Discriminated result: either a vote or a tool-call request.
     */
    public sealed interface ParsedResponse {
        record Vote(AgentVote vote) implements ParsedResponse {}

        record ToolCall(ToolRequest request) implements ParsedResponse {}
    }

    /**
     * Parses a raw agent response. When {@code toolLoopEnabled} is true,
     * detects tool requests before falling back to vote parsing.
     */
    public static ParsedResponse parse(String agentName, String rawResponse, boolean toolLoopEnabled) {
        String cleaned = stripCodeFences(rawResponse);

        if (toolLoopEnabled && isToolRequest(cleaned)) {
            return new ParsedResponse.ToolCall(parseToolRequest(agentName, cleaned));
        }

        return new ParsedResponse.Vote(parseVote(agentName, cleaned));
    }

    /**
     * Parses a raw response that is expected to be a vote (no tool-request detection).
     */
    public static AgentVote parseVote(String agentName, String cleanedJson) {
        requireNonBlank(agentName, "Agent name");
        requireNonBlank(cleanedJson, "Agent response for " + agentName);

        JsonNode root = readJson(cleanedJson, agentName, "vote");

        String rawVote = requireText(root, "vote", agentName);
        String reasoning = requireText(root, "reasoning", agentName);
        double confidence = requireConfidence(root, agentName);

        return new AgentVote(agentName.strip(), RiskLevel.fromString(rawVote), confidence, reasoning.strip());
    }

    /**
     * Parses a raw response that is expected to be a tool request.
     */
    public static ToolRequest parseToolRequest(String agentName, String cleanedJson) {
        requireNonBlank(agentName, "Agent name");
        requireNonBlank(cleanedJson, "Tool request response for " + agentName);

        JsonNode root = readJson(cleanedJson, agentName, "tool request");

        JsonNode requestNode = root.get("tool_request");
        if (requestNode == null || requestNode.isNull() || !requestNode.isObject()) {
            throw new IllegalArgumentException("Missing or invalid 'tool_request' object from " + agentName);
        }

        String tool = requireText(requestNode, "tool", agentName).strip();
        String ref = requireText(requestNode, "ref", agentName).strip();
        String revision = requireText(requestNode, "revision", agentName).strip();

        if (!ToolRequest.ALLOWED_TOOLS.contains(tool)) {
            throw new IllegalArgumentException(
                    "Unknown tool '" + tool + "' from " + agentName + ". Allowed: " + ToolRequest.ALLOWED_TOOLS);
        }

        if (!ToolRequest.ALLOWED_REVISIONS.contains(revision)) {
            throw new IllegalArgumentException("Invalid revision '" + revision + "' from " + agentName + ". Allowed: "
                    + ToolRequest.ALLOWED_REVISIONS);
        }

        if (ref.length() > ToolRequest.MAX_REF_LENGTH) {
            throw new IllegalArgumentException("Ref too long (" + ref.length() + " chars) from " + agentName + ". Max: "
                    + ToolRequest.MAX_REF_LENGTH);
        }

        return new ToolRequest(tool, ref, revision);
    }

    // --- shared helpers ---

    public static String stripCodeFences(String raw) {
        if (raw == null) return "";
        String trimmed = THINK_BLOCK.matcher(raw).replaceAll("").strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.strip();
    }

    public static boolean isToolRequest(String cleanedJson) {
        if (cleanedJson == null || cleanedJson.isBlank()) {
            return false;
        }
        try {
            JsonNode root = MAPPER.readTree(cleanedJson);
            return root != null && root.has("tool_request");
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static JsonNode readJson(String json, String agentName, String context) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            String repaired = repairJson(json);
            if (!repaired.equals(json)) {
                try {
                    return MAPPER.readTree(repaired);
                } catch (JsonProcessingException ignored) {
                }
            }
            throw new IllegalArgumentException(
                    "Invalid JSON " + context + " from " + agentName + ": " + truncate(json, 200), e);
        }
    }

    /**
     * Attempts to fix common LLM JSON typos:
     * - Unquoted string values: {@code "vote": LOW"} -> {@code "vote": "LOW"}
     * - Trailing commas before closing braces/brackets
     */
    static String repairJson(String json) {
        if (json == null) return null;
        String result = json;
        result = result.replaceAll(":\\s*([A-Za-z_][A-Za-z0-9_]*)\"", ": \"$1\"");
        result = result.replaceAll(",\\s*([}\\]])", "$1");
        return result;
    }

    private static String requireText(JsonNode parent, String fieldName, String agentName) {
        JsonNode node = parent.get(fieldName);
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Missing field '" + fieldName + "' for " + agentName);
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field '" + fieldName + "' is blank for " + agentName);
        }
        return value;
    }

    private static double requireConfidence(JsonNode root, String agentName) {
        JsonNode node = root.get("confidence");
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Missing field 'confidence' for " + agentName);
        }

        double value;
        if (node.isNumber()) {
            value = node.asDouble();
        } else {
            try {
                value = Double.parseDouble(node.asText().strip());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Field 'confidence' is not numeric for " + agentName + ": " + node.asText(), ex);
            }
        }

        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    "Field 'confidence' must be in [0,1] for " + agentName + ", got: " + value);
        }
        return value;
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) return value;
        return value.substring(0, maxLen) + "...";
    }
}
