package nl.example.qualityreport.report.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Loads domain-specific keywords from a JSON file and provides
 * case-insensitive, whitespace/hyphen-normalized matching.
 *
 * <p>JSON format: {@code {"terms": ["BRP mutatie", "GBA-V", ...]}}
 *
 * <p>Falls through gracefully: returns an empty dictionary if the
 * file is missing or unreadable.
 */
public final class DomainKeywordDictionary {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern NORMALIZE_SEPARATOR = Pattern.compile("[\\s\\-_]+");

    private final List<String> originalTerms;
    private final List<String> normalizedTerms;

    private DomainKeywordDictionary(List<String> terms) {
        this.originalTerms = List.copyOf(terms);
        this.normalizedTerms =
                terms.stream().map(DomainKeywordDictionary::normalize).toList();
    }

    public static DomainKeywordDictionary empty() {
        return new DomainKeywordDictionary(List.of());
    }

    public static DomainKeywordDictionary loadFromPath(Path path) {
        if (path == null || !Files.exists(path)) {
            return empty();
        }
        try (InputStream in = Files.newInputStream(path)) {
            return parseJson(in);
        } catch (IOException e) {
            return empty();
        }
    }

    public static DomainKeywordDictionary loadFromClasspath(String resource) {
        try (InputStream in = DomainKeywordDictionary.class.getResourceAsStream(resource)) {
            if (in == null) return empty();
            return parseJson(in);
        } catch (IOException e) {
            return empty();
        }
    }

    public static DomainKeywordDictionary parseJson(InputStream in) {
        try {
            JsonNode root = MAPPER.readTree(in);
            JsonNode termsNode = root.path("terms");
            if (!termsNode.isArray()) {
                return empty();
            }
            var terms = new ArrayList<String>();
            for (JsonNode node : termsNode) {
                String text = node.asText("").strip();
                if (!text.isEmpty()) {
                    terms.add(text);
                }
            }
            return new DomainKeywordDictionary(terms);
        } catch (IOException e) {
            return empty();
        }
    }

    /**
     * Returns original terms from the dictionary that appear (case/separator-insensitive)
     * anywhere in the given text.
     */
    public List<String> findMatchesIn(String text) {
        if (text == null || text.isBlank() || originalTerms.isEmpty()) {
            return List.of();
        }
        String normalizedText = normalize(text);
        var matches = new ArrayList<String>();
        for (int i = 0; i < normalizedTerms.size(); i++) {
            if (normalizedText.contains(normalizedTerms.get(i))) {
                matches.add(originalTerms.get(i));
            }
        }
        return Collections.unmodifiableList(matches);
    }

    public List<String> terms() {
        return originalTerms;
    }

    public boolean isEmpty() {
        return originalTerms.isEmpty();
    }

    public int size() {
        return originalTerms.size();
    }

    public static String normalize(String input) {
        return NORMALIZE_SEPARATOR.matcher(input.toLowerCase()).replaceAll(" ").strip();
    }
}
