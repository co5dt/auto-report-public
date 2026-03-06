package nl.example.qualityreport.report;

import java.util.ArrayList;
import java.util.List;
import nl.example.qualityreport.report.evidence.EvidenceBundle;
import nl.example.qualityreport.report.evidence.EvidenceFact;

/**
 * Deterministic post-generation verifier that checks whether all must-mention
 * identifiers from the evidence bundle appear in the generated narrative text.
 */
public final class NarrativeVerifier {

    private NarrativeVerifier() {}

    public record VerificationResult(List<String> presentIdentifiers, List<String> missingIdentifiers, boolean passed) {
        public double coveragePercent() {
            int total = presentIdentifiers.size() + missingIdentifiers.size();
            return total > 0 ? (presentIdentifiers.size() * 100.0 / total) : 100.0;
        }
    }

    public static VerificationResult verify(String narrativeText, EvidenceBundle evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return new VerificationResult(List.of(), List.of(), true);
        }

        String text = narrativeText != null ? narrativeText : "";
        var present = new ArrayList<String>();
        var missing = new ArrayList<String>();

        for (EvidenceFact fact : evidence.mustMention()) {
            if (IdentifierMatcher.containsIdentifier(text, fact.value())) {
                present.add(fact.value());
            } else {
                missing.add(fact.value());
            }
        }

        return new VerificationResult(List.copyOf(present), List.copyOf(missing), missing.isEmpty());
    }

    /**
     * Build an insert-only repair prompt: instructs the LLM to produce short
     * additive sentences for the missing identifiers, without rewriting the
     * existing narrative.
     */
    public static String buildRepairPrompt(String existingNarrative, List<String> missingIdentifiers) {
        var sb = new StringBuilder();
        sb.append("The following narrative needs additional sentences to cover missing identifiers.\n");
        sb.append("Write 1-2 SHORT sentences in Dutch that mention the missing identifiers below.\n");
        sb.append("Do NOT rewrite or repeat the existing narrative. Only produce the new sentences.\n\n");
        sb.append("Missing identifiers:\n");
        for (String id : missingIdentifiers) {
            sb.append("- ").append(id).append("\n");
        }
        sb.append("\nExisting narrative (for context only, do NOT repeat it):\n");
        sb.append(existingNarrative);
        sb.append("\n\nReturn ONLY the new sentence(s) in Dutch. No JSON, no markdown, no headers.");
        return sb.toString();
    }

    static final int REPAIR_BATCH_SIZE = 3;

    /**
     * Splits missing identifiers into batches for targeted repair calls.
     */
    public static List<List<String>> batchMissing(List<String> missingIdentifiers) {
        var batches = new ArrayList<List<String>>();
        for (int i = 0; i < missingIdentifiers.size(); i += REPAIR_BATCH_SIZE) {
            batches.add(missingIdentifiers.subList(i, Math.min(i + REPAIR_BATCH_SIZE, missingIdentifiers.size())));
        }
        return batches;
    }

    /**
     * Produces deterministic fallback sentences for identifiers that remain
     * missing after all LLM repair attempts.
     */
    public static String deterministicInsert(List<String> missingIdentifiers) {
        if (missingIdentifiers.isEmpty()) return "";
        var sb = new StringBuilder();
        for (String id : missingIdentifiers) {
            sb.append(" Daarnaast is %s onderdeel van deze wijziging.".formatted(id));
        }
        return sb.toString().strip();
    }
}
