package nl.example.qualityreport.report;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Structured intermediate representation for a generated narrative section.
 * The LLM produces this JSON, which is then rendered deterministically into markdown.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NarrativeDraft(String type, List<NarrativeClaim> claims) {

    /**
     * A single claim in the narrative, citing the evidence fact IDs that support it.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NarrativeClaim(String text, List<String> citedFactIds) {
        public NarrativeClaim {
            if (citedFactIds == null) citedFactIds = List.of();
        }
    }

    public String renderProse() {
        if (claims == null || claims.isEmpty()) return "";
        var sb = new StringBuilder();
        for (NarrativeClaim claim : claims) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(claim.text().strip());
        }
        return sb.toString();
    }
}
