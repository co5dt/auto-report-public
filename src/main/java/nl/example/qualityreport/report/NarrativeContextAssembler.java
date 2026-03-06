package nl.example.qualityreport.report;

import static nl.example.qualityreport.context.XmlUtils.escapeXml;
import static nl.example.qualityreport.context.XmlUtils.isBlankOrNone;

import java.util.*;
import nl.example.qualityreport.context.DiffSummariser;
import nl.example.qualityreport.model.AgentVote;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.CommitInfo;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.model.RiskAssessment;
import nl.example.qualityreport.report.evidence.EvidenceBundle;
import nl.example.qualityreport.report.evidence.EvidenceFact;

/**
 * Builds narrative context that includes deterministic evidence facts and
 * targeted diff hunks around must-mention identifiers, replacing the fixed
 * 4000-char truncation approach in the legacy path.
 */
public final class NarrativeContextAssembler {

    static final int HUNK_BUDGET_CHARS = 6000;

    private NarrativeContextAssembler() {}

    public static String assemble(
            String type, ChangeData changes, JiraData jira, RiskAssessment risk, EvidenceBundle evidence) {
        var sb = new StringBuilder();
        sb.append("<narrative_request type=\"%s\">\n".formatted(type));

        sb.append(buildMetadata(changes, jira, risk));
        sb.append(buildMustMention(evidence));
        sb.append(buildEvidenceFacts(evidence));
        sb.append(buildChangedFiles(changes));
        sb.append(buildCommitMessages(changes));
        sb.append(buildAgentVotes(risk));

        if ("change".equals(type)) {
            sb.append(buildSelectedHunks(changes, evidence));
        }
        if ("risk".equals(type)) {
            sb.append(buildAcceptanceCriteria(jira));
        }

        sb.append("</narrative_request>");
        return sb.toString();
    }

    static String buildMustMention(EvidenceBundle evidence) {
        List<String> values = evidence.mustMentionValues();
        if (values.isEmpty()) return "";

        var sb = new StringBuilder();
        sb.append("  <must_mention>\n");
        for (String v : values) {
            sb.append("    <identifier>%s</identifier>\n".formatted(escapeXml(v)));
        }
        sb.append("  </must_mention>\n");
        return sb.toString();
    }

    static String buildEvidenceFacts(EvidenceBundle evidence) {
        if (evidence.isEmpty()) return "";

        var sb = new StringBuilder();
        sb.append("  <evidence_facts>\n");
        for (EvidenceFact f : evidence.facts()) {
            sb.append("    <fact id=\"%s\" type=\"%s\" source=\"%s\" required=\"%s\">%s</fact>\n"
                    .formatted(
                            f.id(),
                            f.type().name(),
                            escapeXml(f.source()),
                            f.mustMention() ? "true" : "false",
                            escapeXml(f.value())));
        }
        sb.append("  </evidence_facts>\n");
        return sb.toString();
    }

    static String buildSelectedHunks(ChangeData changes, EvidenceBundle evidence) {
        List<DiffSummariser.FileDiff> fileDiffs = DiffSummariser.parseFileDiffs(changes.rawDiff());
        if (fileDiffs.isEmpty()) return "";

        Set<String> mustMentionLower = new HashSet<>();
        for (String v : evidence.mustMentionValues()) {
            mustMentionLower.add(v.toLowerCase());
        }

        var sb = new StringBuilder();
        sb.append("  <selected_hunks>\n");
        int usedChars = 0;

        for (DiffSummariser.FileDiff fd : fileDiffs) {
            for (String hunk : fd.hunks()) {
                boolean relevant = containsAnyIdentifier(hunk, mustMentionLower);
                if (relevant && usedChars + hunk.length() <= HUNK_BUDGET_CHARS) {
                    sb.append("    <hunk file=\"%s\" reason=\"contains must-mention identifier\">\n"
                            .formatted(escapeXml(fd.path())));
                    sb.append(escapeXml(hunk.stripTrailing()));
                    sb.append("\n    </hunk>\n");
                    usedChars += hunk.length();
                }
            }
        }

        if (usedChars < HUNK_BUDGET_CHARS) {
            List<DiffSummariser.SelectedHunk> highSignal = DiffSummariser.selectHighSignalHunks(fileDiffs);
            for (DiffSummariser.SelectedHunk h : highSignal) {
                if (usedChars + h.content().length() > HUNK_BUDGET_CHARS) break;
                sb.append(
                        "    <hunk file=\"%s\" reason=\"%s\">\n".formatted(escapeXml(h.file()), escapeXml(h.reason())));
                sb.append(escapeXml(h.content().stripTrailing()));
                sb.append("\n    </hunk>\n");
                usedChars += h.content().length();
            }
        }

        sb.append("  </selected_hunks>\n");
        return sb.toString();
    }

    private static boolean containsAnyIdentifier(String hunk, Set<String> identifiers) {
        String lower = hunk.toLowerCase();
        for (String id : identifiers) {
            if (lower.contains(id)) return true;
        }
        return false;
    }

    private static String buildMetadata(ChangeData changes, JiraData jira, RiskAssessment risk) {
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

    private static String buildChangedFiles(ChangeData changes) {
        if (changes.changedFiles().isEmpty()) return "";
        var sb = new StringBuilder();
        sb.append("  <changed_files>\n");
        for (String file : changes.changedFiles().stream().sorted().toList()) {
            sb.append("    <file>%s</file>\n".formatted(escapeXml(file)));
        }
        sb.append("  </changed_files>\n");
        return sb.toString();
    }

    private static String buildCommitMessages(ChangeData changes) {
        if (changes.commits().isEmpty()) return "";
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

    private static String buildAgentVotes(RiskAssessment risk) {
        List<AgentVote> votes = risk.agentVotes();
        if (votes.isEmpty()) return "";
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

    private static String buildAcceptanceCriteria(JiraData jira) {
        if (isBlankOrNone(jira.acceptanceCriteria())) return "";
        return "  <acceptance_criteria>%s</acceptance_criteria>\n"
                .formatted(escapeXml(jira.acceptanceCriteria().strip()));
    }
}
