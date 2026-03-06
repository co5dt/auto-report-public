package nl.example.qualityreport.report;

import static nl.example.qualityreport.context.XmlUtils.escapeXml;
import static nl.example.qualityreport.context.XmlUtils.isBlankOrNone;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import nl.example.qualityreport.llm.LlmProvider;
import nl.example.qualityreport.model.AgentVote;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.CommitInfo;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.model.RiskAssessment;
import nl.example.qualityreport.report.evidence.DeterministicEvidenceExtractor;
import nl.example.qualityreport.report.evidence.DomainKeywordDictionary;
import nl.example.qualityreport.report.evidence.EvidenceBundle;
import nl.example.qualityreport.report.evidence.EvidenceKeywordAugmenter;
import nl.example.qualityreport.report.evidence.JiraKeywordExtractor;

/**
 * Generates LLM-based narrative sections for quality reports.
 * Supports single-pass (direct), two-pass (extract-then-narrate), and
 * evidence-first (deterministic extraction + targeted context) modes.
 */
public class NarrativeService {

    private static final Logger LOG = Logger.getLogger(NarrativeService.class.getName());

    static final int MAX_DIFF_EXCERPT_CHARS = 4000;

    private final LlmProvider llm;
    private final boolean twoPass;
    private final boolean evidenceFirst;
    private final boolean verifyEnabled;
    private final int repairMaxRetries;
    private final boolean debugArtifacts;
    private final DomainKeywordDictionary dictionary;
    private final JiraKeywordExtractor jiraKeywordExtractor;

    public NarrativeService(LlmProvider llm) {
        this(llm, false, false, false, 0, null, false);
    }

    public NarrativeService(LlmProvider llm, boolean twoPass) {
        this(llm, twoPass, false, false, 0, null, false);
    }

    public NarrativeService(LlmProvider llm, boolean twoPass, boolean evidenceFirst) {
        this(llm, twoPass, evidenceFirst, false, 0, null, false);
    }

    public NarrativeService(
            LlmProvider llm, boolean twoPass, boolean evidenceFirst, boolean verifyEnabled, int repairMaxRetries) {
        this(llm, twoPass, evidenceFirst, verifyEnabled, repairMaxRetries, null, false);
    }

    public NarrativeService(
            LlmProvider llm,
            boolean twoPass,
            boolean evidenceFirst,
            boolean verifyEnabled,
            int repairMaxRetries,
            DomainKeywordDictionary dictionary) {
        this(llm, twoPass, evidenceFirst, verifyEnabled, repairMaxRetries, dictionary, false);
    }

    public NarrativeService(
            LlmProvider llm,
            boolean twoPass,
            boolean evidenceFirst,
            boolean verifyEnabled,
            int repairMaxRetries,
            DomainKeywordDictionary dictionary,
            boolean debugArtifacts) {
        this.llm = llm;
        this.twoPass = twoPass;
        this.evidenceFirst = evidenceFirst;
        this.verifyEnabled = verifyEnabled;
        this.repairMaxRetries = repairMaxRetries;
        this.debugArtifacts = debugArtifacts;
        this.dictionary = dictionary != null ? dictionary : DomainKeywordDictionary.empty();
        this.jiraKeywordExtractor = llm != null ? new JiraKeywordExtractor(llm) : null;
    }

    public record Narratives(String changeNarrative, String riskNarrative) {}

    public Narratives generate(ChangeData changes, JiraData jira, RiskAssessment risk) {
        if (llm == null) {
            return new Narratives("", "");
        }

        EvidenceBundle evidence = evidenceFirst
                ? augmentEvidence(DeterministicEvidenceExtractor.extract(changes, jira), changes, jira)
                : null;

        String changeNarrative = generateNarrative("change", changes, jira, risk, evidence);
        String riskNarrative = generateNarrative("risk", changes, jira, risk, evidence);
        return new Narratives(changeNarrative, riskNarrative);
    }

    private EvidenceBundle augmentEvidence(EvidenceBundle baseline, ChangeData changes, JiraData jira) {
        java.util.List<String> llmKeywords = java.util.List.of();
        if (jiraKeywordExtractor != null) {
            try {
                llmKeywords = jiraKeywordExtractor.extract(
                        jira.tickets().isEmpty() ? "" : jira.tickets().getFirst(),
                        jira.description(),
                        jira.acceptanceCriteria());
            } catch (Exception e) {
                // fail-open: continue with deterministic baseline
            }
        }
        return EvidenceKeywordAugmenter.augment(baseline, dictionary, llmKeywords, changes, jira);
    }

    EvidenceBundle lastEvidence;

    NarrativeDraft lastDraft;

    NarrativeVerifier.VerificationResult lastVerification;

    private String generateNarrative(
            String type, ChangeData changes, JiraData jira, RiskAssessment risk, EvidenceBundle evidence) {
        String context;
        if (evidence != null) {
            context = NarrativeContextAssembler.assemble(type, changes, jira, risk, evidence);
            lastEvidence = evidence;

            if (debugArtifacts) {
                LOG.info(() -> "[%s] must-mention count: %d, identifiers: %s"
                        .formatted(type, evidence.mustMentionValues().size(), evidence.mustMentionValues()));
            }

            String narrative = null;
            var parsePathBuilder = new StringBuilder();

            String structured = generateStructuredDraft(context);
            if (structured != null) {
                var parsed = NarrativeDraftParser.parse(structured);
                if (parsed.isPresent()) {
                    lastDraft = parsed.get();
                    narrative = parsed.get().renderProse();
                    parsePathBuilder.append("structured-json");
                } else {
                    parsePathBuilder.append("structured-parse-failed");
                }
            } else {
                parsePathBuilder.append("structured-llm-failed");
            }

            if (narrative == null) {
                narrative = singlePassNarrative(context);
                parsePathBuilder.append("->single-pass-fallback");
            }

            if (debugArtifacts) {
                String resolvedPath = parsePathBuilder.toString();
                LOG.info(() -> "[%s] parse path: %s".formatted(type, resolvedPath));
            }

            if (verifyEnabled) {
                narrative = verifyAndRepair(type, narrative, evidence);
            }

            return narrative;
        } else {
            context = buildNarrativeContext(type, changes, jira, risk);
        }

        if (twoPass) {
            String facts = extractFacts(context);
            if (facts != null && !facts.isBlank()) {
                return writeNarrativeFromFacts(type, facts);
            }
        }

        return singlePassNarrative(context);
    }

    String verifyAndRepair(String narrativeType, String narrative, EvidenceBundle evidence) {
        if ("risk".equals(narrativeType)) {
            var result = NarrativeVerifier.verify(narrative, evidence);
            lastVerification = result;
            if (debugArtifacts) {
                LOG.info(() ->
                        "[verify-risk] coverage=%.0f%% (no strict enforcement)".formatted(result.coveragePercent()));
            }
            return narrative;
        }

        String current = narrative;
        for (int attempt = 0; attempt <= repairMaxRetries; attempt++) {
            var result = NarrativeVerifier.verify(current, evidence);
            lastVerification = result;
            if (debugArtifacts) {
                int attemptNum = attempt;
                LOG.info(() -> "[verify-change] attempt %d: coverage=%.0f%% missing=%s"
                        .formatted(attemptNum, result.coveragePercent(), result.missingIdentifiers()));
            }
            if (result.passed()) {
                return current;
            }
            if (attempt < repairMaxRetries) {
                current = repairWithBatches(current, result.missingIdentifiers());
            }
        }

        var finalResult = NarrativeVerifier.verify(current, evidence);
        lastVerification = finalResult;
        if (!finalResult.passed()) {
            String fallback = NarrativeVerifier.deterministicInsert(finalResult.missingIdentifiers());
            if (!fallback.isEmpty()) {
                current = current + " " + fallback;
                if (debugArtifacts) {
                    LOG.info(() ->
                            "[verify-change] deterministic insert for: %s".formatted(finalResult.missingIdentifiers()));
                }
            }
        }
        return current;
    }

    private String repairWithBatches(String existingNarrative, List<String> missingIdentifiers) {
        String current = existingNarrative;
        for (List<String> batch : NarrativeVerifier.batchMissing(missingIdentifiers)) {
            String addition = repair(current, batch);
            if (addition != null && !addition.isBlank()) {
                current = current + " " + addition.strip();
            }
        }
        return current;
    }

    String repair(String existingNarrative, List<String> missingIdentifiers) {
        String prompt = loadPrompt("/prompts/report-narrative.txt");
        String userMessage = NarrativeVerifier.buildRepairPrompt(existingNarrative, missingIdentifiers);
        try {
            return llm.chat(prompt, userMessage);
        } catch (Exception e) {
            return null;
        }
    }

    String generateStructuredDraft(String context) {
        String prompt = loadPrompt("/prompts/report-narrative-structured.txt");
        try {
            return llm.chat(prompt, context);
        } catch (Exception e) {
            return null;
        }
    }

    String extractFacts(String context) {
        String prompt = loadPrompt("/prompts/fact-extraction.txt");
        try {
            return llm.chat(prompt, context);
        } catch (Exception e) {
            return null;
        }
    }

    String writeNarrativeFromFacts(String type, String facts) {
        String prompt = loadPrompt("/prompts/report-narrative.txt");
        String userMessage =
                "<narrative_from_facts type=\"%s\">\n<extracted_facts>\n%s\n</extracted_facts>\n</narrative_from_facts>"
                        .formatted(type, facts);
        try {
            return llm.chat(prompt, userMessage);
        } catch (Exception e) {
            return "";
        }
    }

    private String singlePassNarrative(String context) {
        String prompt = loadPrompt("/prompts/report-narrative.txt");
        try {
            return llm.chat(prompt, context);
        } catch (Exception e) {
            return "";
        }
    }

    String buildNarrativeContext(String type, ChangeData changes, JiraData jira, RiskAssessment risk) {
        var sb = new StringBuilder();
        sb.append("<narrative_request type=\"%s\">\n".formatted(type));

        sb.append(buildMetadata(changes, jira, risk));
        sb.append(buildChangedFiles(changes));
        sb.append(buildCommitMessages(changes));
        sb.append(buildAgentVotes(risk));

        if ("change".equals(type)) {
            sb.append(buildDiffExcerpt(changes));
        }
        if ("risk".equals(type)) {
            sb.append(buildAcceptanceCriteria(jira));
        }

        sb.append("</narrative_request>");
        return sb.toString();
    }

    private String buildMetadata(ChangeData changes, JiraData jira, RiskAssessment risk) {
        var sb = new StringBuilder();
        sb.append("  <tickets>%s</tickets>\n".formatted(escapeXml(String.join(", ", jira.tickets()))));
        sb.append("  <description>%s</description>\n"
                .formatted(escapeXml(jira.description() != null ? jira.description() : "")));
        sb.append("  <risk_level>%s</risk_level>\n".formatted(risk.finalRisk().name()));
        sb.append("  <consensus>%s round %d</consensus>\n"
                .formatted(risk.consensusType().name().toLowerCase(), risk.roundReached()));
        sb.append("  <total_commits>%d</total_commits>\n"
                .formatted(changes.commits().size()));
        sb.append("  <total_files>%d</total_files>\n"
                .formatted(changes.changedFiles().size()));
        sb.append("  <total_insertions>%d</total_insertions>\n".formatted(changes.totalInsertions()));
        sb.append("  <total_deletions>%d</total_deletions>\n".formatted(changes.totalDeletions()));
        return sb.toString();
    }

    private String buildChangedFiles(ChangeData changes) {
        if (changes.changedFiles().isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        sb.append("  <changed_files>\n");
        for (String file : changes.changedFiles().stream().sorted().toList()) {
            sb.append("    <file>%s</file>\n".formatted(escapeXml(file)));
        }
        sb.append("  </changed_files>\n");
        return sb.toString();
    }

    private String buildCommitMessages(ChangeData changes) {
        if (changes.commits().isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        sb.append("  <commit_messages>\n");
        changes.commits().stream()
                .sorted(Comparator.comparing(CommitInfo::date))
                .forEach(c -> sb.append("    <commit author=\"%s\" role=\"%s\">%s</commit>\n"
                        .formatted(
                                escapeXml(c.author()),
                                escapeXml(c.role()),
                                escapeXml(c.message().strip()))));
        sb.append("  </commit_messages>\n");
        return sb.toString();
    }

    private String buildAgentVotes(RiskAssessment risk) {
        List<AgentVote> votes = risk.agentVotes();
        if (votes.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        sb.append("  <agent_votes consensus=\"%s\">\n"
                .formatted(risk.consensusType().name().toLowerCase()));
        for (AgentVote v : votes) {
            sb.append("    <vote agent=\"%s\" level=\"%s\" confidence=\"%.2f\">%s</vote>\n"
                    .formatted(escapeXml(v.agent()), v.vote().name(), v.confidence(), escapeXml(v.reasoning())));
        }
        sb.append("  </agent_votes>\n");
        return sb.toString();
    }

    private String buildDiffExcerpt(ChangeData changes) {
        String diff = changes.rawDiff();
        if (diff == null || diff.isBlank()) {
            return "";
        }
        String excerpt = diff.length() > MAX_DIFF_EXCERPT_CHARS
                ? diff.substring(0, MAX_DIFF_EXCERPT_CHARS) + "\n[truncated]"
                : diff;
        var sb = new StringBuilder();
        sb.append("  <diff_excerpt>\n");
        sb.append(escapeXml(excerpt));
        sb.append("\n  </diff_excerpt>\n");
        return sb.toString();
    }

    private String buildAcceptanceCriteria(JiraData jira) {
        if (isBlankOrNone(jira.acceptanceCriteria())) {
            return "";
        }
        return "  <acceptance_criteria>%s</acceptance_criteria>\n"
                .formatted(escapeXml(jira.acceptanceCriteria().strip()));
    }

    private String loadPrompt(String resource) {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                return "You are a report writer. Generate a concise narrative for a quality report section.";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "You are a report writer. Generate a concise narrative for a quality report section.";
        }
    }
}
