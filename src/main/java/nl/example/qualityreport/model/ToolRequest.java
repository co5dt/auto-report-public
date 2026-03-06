package nl.example.qualityreport.model;

import java.util.Set;
import nl.example.qualityreport.analysis.ResponseParser;

/**
 * Represents a bounded tool request from an agent during deliberation.
 * Validation constants are defined here; parsing logic lives in {@link ResponseParser}.
 */
public record ToolRequest(String tool, String ref, String revision) {
    public static final String TOOL_READ_FILE = "read_file";
    public static final Set<String> ALLOWED_TOOLS = Set.of(TOOL_READ_FILE);
    public static final Set<String> ALLOWED_REVISIONS = Set.of("branch", "target");
    public static final int MAX_REF_LENGTH = 500;

    /**
     * Delegates to {@link ResponseParser#isToolRequest(String)}.
     */
    public static boolean isToolRequest(String cleanedJson) {
        return ResponseParser.isToolRequest(cleanedJson);
    }

    /**
     * Delegates to {@link ResponseParser} for JSON parsing and validation.
     */
    public static ToolRequest parse(String agentName, String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            throw new IllegalArgumentException("Empty tool request response from " + agentName);
        }
        String cleaned = ResponseParser.stripCodeFences(responseJson);
        return ResponseParser.parseToolRequest(agentName, cleaned);
    }
}
