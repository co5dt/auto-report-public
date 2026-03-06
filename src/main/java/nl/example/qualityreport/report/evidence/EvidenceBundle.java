package nl.example.qualityreport.report.evidence;

import java.util.List;
import java.util.Set;

/**
 * Collection of deterministically extracted facts from a change set,
 * partitioned into must-mention (required in narrative) and optional.
 * Tier classification provides verification policy:
 * - Tier1 (strict): tickets, classes, migrations, annotation literals
 * - Tier2 (soft): domain terms, test classes, other augmented keywords
 */
public record EvidenceBundle(List<EvidenceFact> facts) {

    private static final Set<EvidenceFact.FactType> TIER1_TYPES = Set.of(
            EvidenceFact.FactType.TICKET,
            EvidenceFact.FactType.CLASS_NAME,
            EvidenceFact.FactType.MIGRATION,
            EvidenceFact.FactType.ANNOTATION_LITERAL,
            EvidenceFact.FactType.CONSTANT_FIELD);

    public List<EvidenceFact> mustMention() {
        return facts.stream().filter(EvidenceFact::mustMention).toList();
    }

    public List<String> mustMentionValues() {
        return mustMention().stream().map(EvidenceFact::value).toList();
    }

    public List<EvidenceFact> tier1() {
        return mustMention().stream()
                .filter(f -> TIER1_TYPES.contains(f.type()))
                .toList();
    }

    public List<String> tier1Values() {
        return tier1().stream().map(EvidenceFact::value).toList();
    }

    public List<EvidenceFact> tier2() {
        return mustMention().stream()
                .filter(f -> !TIER1_TYPES.contains(f.type()))
                .toList();
    }

    public List<String> tier2Values() {
        return tier2().stream().map(EvidenceFact::value).toList();
    }

    public List<EvidenceFact> optional() {
        return facts.stream().filter(f -> !f.mustMention()).toList();
    }

    public boolean isEmpty() {
        return facts.isEmpty();
    }

    public int size() {
        return facts.size();
    }
}
