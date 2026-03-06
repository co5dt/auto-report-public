package nl.example.qualityreport.report.evidence;

import java.util.*;
import java.util.logging.Logger;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.report.evidence.EvidenceFact.FactType;

/**
 * Merges domain-keyword evidence from two enrichment sources
 * (dictionary matches and LLM-extracted Jira keywords) into
 * an existing {@link EvidenceBundle}.
 *
 * <p>Dictionary matches are unconditionally promoted as must-mention.
 * LLM-extracted keywords are only promoted when they can be
 * deterministically anchored in the code context (diff, commits,
 * changed file paths).
 *
 * <p>Deduplicates against existing facts by normalized value.
 */
public final class EvidenceKeywordAugmenter {

    private static final Logger LOG = Logger.getLogger(EvidenceKeywordAugmenter.class.getName());

    private EvidenceKeywordAugmenter() {}

    /**
     * Augments the given bundle with domain keyword facts.
     *
     * @param baseline          existing evidence from {@link DeterministicEvidenceExtractor}
     * @param dictionary        loaded domain keyword dictionary (may be empty)
     * @param llmKeywords       raw keywords returned by {@link JiraKeywordExtractor} (may be empty)
     * @param changes           change data for anchoring context
     * @param jira              jira data for anchoring context
     * @return a new bundle with additional domain-term facts appended
     */
    public static EvidenceBundle augment(
            EvidenceBundle baseline,
            DomainKeywordDictionary dictionary,
            List<String> llmKeywords,
            ChangeData changes,
            JiraData jira) {

        var existingValues = new HashSet<String>();
        for (EvidenceFact f : baseline.facts()) {
            existingValues.add(normalize(f.value()));
        }

        var augmentedFacts = new ArrayList<>(baseline.facts());
        int seq = baseline.size();

        String anchorContext = buildAnchorContext(changes, jira);

        seq = addDictionaryMatches(dictionary, anchorContext, existingValues, augmentedFacts, seq);
        addAnchoredLlmKeywords(llmKeywords, anchorContext, existingValues, augmentedFacts, seq);

        return new EvidenceBundle(List.copyOf(augmentedFacts));
    }

    /**
     * Dictionary terms that appear in the anchor context are promoted
     * as must-mention domain terms.
     */
    static int addDictionaryMatches(
            DomainKeywordDictionary dictionary,
            String anchorContext,
            Set<String> existingValues,
            List<EvidenceFact> facts,
            int seq) {

        if (dictionary == null || dictionary.isEmpty()) return seq;

        List<String> matches = dictionary.findMatchesIn(anchorContext);
        for (String match : matches) {
            if (existingValues.add(normalize(match))) {
                facts.add(new EvidenceFact("domain-" + seq++, FactType.DOMAIN_TERM, match, "dictionary", true));
                LOG.fine(() -> "Dictionary must-mention: " + match);
            }
        }
        return seq;
    }

    /**
     * LLM-extracted keywords are only added if they can be anchored
     * (found case-insensitively) in the change/Jira context.
     */
    static int addAnchoredLlmKeywords(
            List<String> llmKeywords,
            String anchorContext,
            Set<String> existingValues,
            List<EvidenceFact> facts,
            int seq) {

        if (llmKeywords == null || llmKeywords.isEmpty()) return seq;

        String lowerContext = anchorContext.toLowerCase();
        for (String keyword : llmKeywords) {
            if (keyword == null || keyword.isBlank()) continue;
            String normalized = normalize(keyword);
            if (existingValues.contains(normalized)) continue;

            if (lowerContext.contains(normalized)) {
                existingValues.add(normalized);
                facts.add(new EvidenceFact(
                        "domain-" + seq++, FactType.DOMAIN_TERM, keyword, "llm-jira-extract+anchor", true));
                LOG.fine(() -> "LLM keyword anchored as must-mention: " + keyword);
            } else {
                LOG.fine(() -> "LLM keyword NOT anchored, skipping: " + keyword);
            }
        }
        return seq;
    }

    /**
     * Builds a single text blob from all change/Jira context for anchoring.
     */
    static String buildAnchorContext(ChangeData changes, JiraData jira) {
        var sb = new StringBuilder();
        if (jira != null) {
            if (jira.description() != null) sb.append(jira.description()).append('\n');
            if (jira.acceptanceCriteria() != null)
                sb.append(jira.acceptanceCriteria()).append('\n');
            for (String t : jira.tickets()) sb.append(t).append('\n');
        }
        if (changes != null) {
            for (String f : changes.changedFiles()) sb.append(f).append('\n');
            for (var c : changes.commits()) {
                if (c.message() != null) sb.append(c.message()).append('\n');
            }
            if (changes.rawDiff() != null) sb.append(changes.rawDiff());
        }
        return sb.toString();
    }

    private static String normalize(String value) {
        return DomainKeywordDictionary.normalize(value);
    }
}
