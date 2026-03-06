package nl.example.qualityreport.report.evidence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nl.example.qualityreport.llm.LlmProvider;

/**
 * Uses an LLM to extract domain-specific keywords from Jira text
 * (title, description, acceptance criteria) in Dutch and/or English.
 *
 * <p>Expects the LLM to return a JSON array of strings.
 * Falls back to empty list on any parse failure.
 */
public final class JiraKeywordExtractor {

    private static final Logger LOG = Logger.getLogger(JiraKeywordExtractor.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROMPT_RESOURCE = "/prompts/jira-keyword-extraction.txt";
    private static final Pattern JSON_ARRAY =
            Pattern.compile("\\[\\s*\".*?\"\\s*(?:,\\s*\".*?\"\\s*)*]", Pattern.DOTALL);

    private final LlmProvider llm;
    private final String systemPrompt;

    public JiraKeywordExtractor(LlmProvider llm) {
        this.llm = llm;
        this.systemPrompt = loadPrompt();
    }

    /**
     * Extracts domain keywords from the combined Jira text.
     * Returns an empty list on LLM or parsing failure (fail-open).
     */
    public List<String> extract(String jiraTitle, String jiraDescription, String acceptanceCriteria) {
        var userMessage = buildUserMessage(jiraTitle, jiraDescription, acceptanceCriteria);
        if (userMessage.isBlank()) {
            return List.of();
        }

        try {
            String response = llm.chat(systemPrompt, userMessage);
            return parseResponse(response);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM keyword extraction failed, falling back to empty", e);
            return List.of();
        }
    }

    static List<String> parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        String jsonCandidate = response.strip();

        if (!jsonCandidate.startsWith("[")) {
            Matcher m = JSON_ARRAY.matcher(jsonCandidate);
            if (m.find()) {
                jsonCandidate = m.group();
            } else {
                LOG.warning("No JSON array found in LLM response: " + truncate(response));
                return List.of();
            }
        }

        try {
            List<String> parsed = MAPPER.readValue(jsonCandidate, new TypeReference<>() {});
            return parsed.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::strip)
                    .toList();
        } catch (IOException e) {
            LOG.warning("Failed to parse keyword JSON: " + truncate(response));
            return List.of();
        }
    }

    private static String buildUserMessage(String title, String description, String acceptanceCriteria) {
        var sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title.strip());
        }
        if (description != null && !description.isBlank()) {
            sb.append("\n\n").append(description.strip());
        }
        if (acceptanceCriteria != null && !acceptanceCriteria.isBlank()) {
            sb.append("\n\n").append(acceptanceCriteria.strip());
        }
        return sb.toString().strip();
    }

    private static String loadPrompt() {
        try (InputStream in = JiraKeywordExtractor.class.getResourceAsStream(PROMPT_RESOURCE)) {
            if (in == null) {
                LOG.warning("Missing prompt resource: " + PROMPT_RESOURCE);
                return "Extract domain keywords as a JSON array of strings.";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error loading prompt", e);
            return "Extract domain keywords as a JSON array of strings.";
        }
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
