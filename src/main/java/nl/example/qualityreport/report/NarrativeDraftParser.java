package nl.example.qualityreport.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Attempts to parse LLM output as a structured {@link NarrativeDraft} JSON.
 * Returns empty on parse failure so callers can fall back to raw text mode.
 */
public final class NarrativeDraftParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*\\n?(\\{.*?})\\s*```", Pattern.DOTALL);

    private NarrativeDraftParser() {}

    public static Optional<NarrativeDraft> parse(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return Optional.empty();
        }

        String json = extractJson(llmOutput);
        try {
            NarrativeDraft draft = MAPPER.readValue(json, NarrativeDraft.class);
            if (draft.claims() == null || draft.claims().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(draft);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private static String extractJson(String text) {
        Matcher m = JSON_BLOCK.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        String trimmed = text.strip();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        return text;
    }
}
