package nl.example.qualityreport.report;

import java.util.regex.Pattern;

/**
 * Shared identifier matching logic for narrative verification and scoring.
 * Handles case-insensitive matching and camelCase/kebab-case normalization
 * to reduce false negatives without introducing false positives.
 */
public final class IdentifierMatcher {

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z])(?=[A-Z])");

    private IdentifierMatcher() {}

    /**
     * Returns true if the identifier appears in the text, using both
     * direct substring match and normalized token match.
     * Checks both directions: identifier normalized against text,
     * and text tokens normalized to match the identifier's form.
     */
    public static boolean containsIdentifier(String text, String identifier) {
        if (text == null || identifier == null) return false;
        String lowerText = text.toLowerCase();
        String lowerIdentifier = identifier.toLowerCase();

        if (lowerText.contains(lowerIdentifier)) {
            return true;
        }

        String idTokens = normalizeToTokens(identifier);
        if (!idTokens.equals(lowerIdentifier) && lowerText.contains(idTokens)) {
            return true;
        }

        String idKebab = camelToKebab(identifier);
        if (!idKebab.equals(lowerIdentifier) && lowerText.contains(idKebab)) {
            return true;
        }

        String joined = lowerIdentifier.replace("-", "");
        if (!joined.equals(lowerIdentifier) && lowerText.contains(joined)) {
            return true;
        }

        String underscoreToSpace = lowerIdentifier.replace("_", " ");
        if (!underscoreToSpace.equals(lowerIdentifier) && lowerText.contains(underscoreToSpace)) {
            return true;
        }

        String underscoreToHyphen = lowerIdentifier.replace("_", "-");
        if (!underscoreToHyphen.equals(lowerIdentifier) && lowerText.contains(underscoreToHyphen)) {
            return true;
        }

        String underscoreJoined = lowerIdentifier.replace("_", "");
        if (!underscoreJoined.equals(lowerIdentifier) && lowerText.contains(underscoreJoined)) {
            return true;
        }

        return false;
    }

    /**
     * Normalizes camelCase identifiers to space-separated lowercase tokens.
     * e.g. "CitizenSearchService" -> "citizen search service"
     */
    static String normalizeToTokens(String identifier) {
        return CAMEL_BOUNDARY.matcher(identifier).replaceAll(" ").toLowerCase();
    }

    /**
     * Converts camelCase to kebab-case.
     * e.g. "CitizenSearchService" -> "citizen-search-service"
     */
    static String camelToKebab(String identifier) {
        return CAMEL_BOUNDARY.matcher(identifier).replaceAll("-").toLowerCase();
    }
}
