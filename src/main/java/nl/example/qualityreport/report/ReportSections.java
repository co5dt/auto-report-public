package nl.example.qualityreport.report;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import nl.example.qualityreport.model.AgentVote;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.model.RiskAssessment;

/**
 * Deterministic, side-effect-free markdown section builders
 * producing the testrapport template from the CLI spec.
 */
public final class ReportSections {

    private ReportSections() {}

    public static String header(JiraData jira, ChangeData changes, RiskAssessment risk, LocalDate date) {
        String primaryTicket = ReportFileNamer.primaryTicket(jira.tickets());
        String team = detectTeam(changes);
        return """
                # Testrapport %s — %s

                **Fix version:** %s
                **Team:** %s
                **Datum:** %s
                **Risicoscore:** %s

                ---
                """
                .formatted(
                        safe(jira.fixVersion()),
                        primaryTicket,
                        safe(jira.fixVersion()),
                        team,
                        date.toString(),
                        risk.finalRisk().name());
    }

    public static String tickets(JiraData jira) {
        return """
                ## Betrokken tickets
                %s
                """
                .formatted(
                        jira.tickets() != null && !jira.tickets().isEmpty()
                                ? String.join(", ", jira.tickets())
                                : gap("Geen tickets opgegeven"));
    }

    public static String changeNarrative(String llmNarrative, ChangeData changes) {
        var sb = new StringBuilder();
        sb.append("## Wat is er gewijzigd en waarom?\n");
        sb.append(safeNarrative(llmNarrative, "Geen wijzigingsomschrijving beschikbaar"));
        sb.append("\n\n");
        sb.append("### Wijzigingen per type\n");

        Map<String, ChangeData.RoleSummary> byRole = changes.groupByRole();
        byRole.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(e -> {
                    var r = e.getValue();
                    sb.append("- **%s-wijzigingen (%d commits, %d bestanden, +%d/-%d)**\n"
                            .formatted(e.getKey(), r.commitCount(), r.filesChanged(), r.insertions(), r.deletions()));
                });

        long scripts = changes.scriptCount();
        if (scripts > 0) {
            List<String> scriptFiles = changes.changedFiles().stream()
                    .filter(ChangeData::isScript)
                    .sorted()
                    .toList();
            sb.append("- **Scripts (%d):** %s\n".formatted(scripts, String.join(", ", scriptFiles)));
        } else {
            sb.append("- **Scripts (0)**\n");
        }

        long iDocs = changes.iDocCount();
        if (iDocs > 0) {
            sb.append("- **iDoc templates (%d)**\n".formatted(iDocs));
        } else {
            sb.append("- **iDoc templates (0)**\n");
        }

        sb.append('\n');
        return sb.toString();
    }

    public static String impactChecklist(JiraData jira) {
        var sb = new StringBuilder();
        sb.append("## Wat wordt geraakt?\n");

        JiraData.Impact impact = jira.impact();
        for (String area : JiraData.Impact.ALL_AREAS) {
            boolean checked = impact != null && impact.isChecked(area);
            sb.append(checked ? "☑ " : "☐ ");
            sb.append(area).append('\n');
        }

        sb.append('\n');
        return sb.toString();
    }

    public static String acceptanceCriteria(JiraData jira) {
        return """
                ## Acceptatiecriteria
                %s
                """
                .formatted(
                        isBlankOrNone(jira.acceptanceCriteria())
                                ? gap("Geen acceptatiecriteria opgegeven")
                                : jira.acceptanceCriteria().strip());
    }

    public static String riskAnalysis(RiskAssessment risk, String riskNarrative) {
        var sb = new StringBuilder();
        sb.append("## Risico-analyse\n");
        sb.append("**Risicoscore:** %s\n\n".formatted(risk.finalRisk().name()));
        sb.append("**Grootste risico's:**\n");
        sb.append(safeNarrative(riskNarrative, "Geen risicoanalyse beschikbaar"));
        sb.append("\n\n");
        sb.append(deliberationTable(risk));
        return sb.toString();
    }

    public static String deliberationTable(RiskAssessment risk) {
        var sb = new StringBuilder();
        sb.append("**Agent deliberation:**\n");
        sb.append("| Agent | Vote | Confidence | Key reasoning |\n");
        sb.append("|-------|------|------------|---------------|\n");

        for (AgentVote v : risk.agentVotes()) {
            sb.append("| %s | %s | %.2f | %s |\n"
                    .formatted(
                            formatAgentName(v.agent()), v.vote().name(), v.confidence(), truncate(v.reasoning(), 120)));
        }

        String consensusLabel;
        if (risk.agentVotes().size() == 1) {
            consensusLabel = "single agent";
        } else {
            consensusLabel = switch (risk.consensusType()) {
                case UNANIMOUS -> "unanimous";
                case HIGHEST -> "highest vote (Pattern A)";
            };
        }
        sb.append("\nConsensus: %s, round %d.\n\n".formatted(consensusLabel, risk.roundReached()));

        if (!risk.minorityOpinion().isEmpty()) {
            sb.append("**Minority opinion:** %s\n\n".formatted(risk.minorityOpinion()));
        }

        return sb.toString();
    }

    public static String testEvidence(JiraData jira) {
        var sb = new StringBuilder();
        sb.append("## Wat is getest?\n");
        sb.append("### Handmatig\n");

        JiraData.TestEvidence te = jira.testEvidence();
        if (te == null || isBlankOrNone(te.manualDescription())) {
            sb.append(gap("Geen handmatige testomschrijving opgegeven")).append('\n');
        } else {
            sb.append(te.manualDescription().strip()).append('\n');
        }

        sb.append("\n### Geautomatiseerd\n");
        if (te == null) {
            sb.append(gap("Geen geautomatiseerde testresultaten beschikbaar")).append('\n');
        } else {
            if (te.hasAutomatedTests()) {
                sb.append(
                        "- Resultaat: %s\n".formatted(isBlankOrNone(te.passFail()) ? gap("onbekend") : te.passFail()));
            } else {
                sb.append("- Resultaat: ")
                        .append(gap("Geen geautomatiseerde tests gerapporteerd"))
                        .append('\n');
            }
            sb.append("- Coverage: %s\n"
                    .formatted(isBlankOrNone(te.coveragePercent()) ? gap("onbekend") : te.coveragePercent()));
        }

        sb.append('\n');
        return sb.toString();
    }

    public static String deployment(JiraData jira) {
        var sb = new StringBuilder();
        sb.append("## Uitlevering op productie\n");

        JiraData.Deployment dep = jira.deployment();
        if (dep == null) {
            sb.append(gap("Geen uitleveringsinformatie beschikbaar")).append('\n');
        } else {
            sb.append("Standaard uitlevering: %s\n".formatted(yesNo(dep.standardDeployment())));
            sb.append("Feature toggle: %s\n".formatted(yesNo(dep.featureToggle())));
            sb.append("Handmatig script: %s\n".formatted(yesNo(dep.manualScript())));
            sb.append("Hypercare: %s\n".formatted(yesNo(dep.hypercare())));
        }

        sb.append('\n');
        return sb.toString();
    }

    public static String dod(JiraData jira) {
        if (jira.dodComplete()) {
            return "## DoD\n☑ DoD is gereed voor alle issues.\n";
        } else {
            return "## DoD\n☐ DoD is **niet** gereed voor alle issues.\n";
        }
    }

    // --- body-only extractors for template rendering ---

    static String headerFixVersion(JiraData jira) {
        return safe(jira.fixVersion());
    }

    static String headerPrimaryTicket(JiraData jira) {
        return ReportFileNamer.primaryTicket(jira.tickets());
    }

    static String headerTeam(ChangeData changes) {
        return detectTeam(changes);
    }

    static String headerDate(LocalDate date) {
        return date.toString();
    }

    static String headerRiskScore(RiskAssessment risk) {
        return risk.finalRisk().name();
    }

    static String ticketsBody(JiraData jira) {
        return jira.tickets() != null && !jira.tickets().isEmpty()
                ? String.join(", ", jira.tickets())
                : gap("Geen tickets opgegeven");
    }

    static String changeNarrativeBody(String llmNarrative) {
        return safeNarrative(llmNarrative, "Geen wijzigingsomschrijving beschikbaar");
    }

    static String changesPerType(ChangeData changes) {
        var sb = new StringBuilder();
        Map<String, ChangeData.RoleSummary> byRole = changes.groupByRole();
        byRole.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(e -> {
                    var r = e.getValue();
                    sb.append("- **%s-wijzigingen (%d commits, %d bestanden, +%d/-%d)**\n"
                            .formatted(e.getKey(), r.commitCount(), r.filesChanged(), r.insertions(), r.deletions()));
                });

        long scripts = changes.scriptCount();
        if (scripts > 0) {
            List<String> scriptFiles = changes.changedFiles().stream()
                    .filter(ChangeData::isScript)
                    .sorted()
                    .toList();
            sb.append("- **Scripts (%d):** %s\n".formatted(scripts, String.join(", ", scriptFiles)));
        } else {
            sb.append("- **Scripts (0)**\n");
        }

        long iDocs = changes.iDocCount();
        if (iDocs > 0) {
            sb.append("- **iDoc templates (%d)**\n".formatted(iDocs));
        } else {
            sb.append("- **iDoc templates (0)**\n");
        }

        return sb.toString();
    }

    static String impactChecklistBody(JiraData jira) {
        var sb = new StringBuilder();
        JiraData.Impact impact = jira.impact();
        for (String area : JiraData.Impact.ALL_AREAS) {
            boolean checked = impact != null && impact.isChecked(area);
            sb.append(checked ? "☑ " : "☐ ");
            sb.append(area).append('\n');
        }
        return sb.toString();
    }

    static String acceptanceCriteriaBody(JiraData jira) {
        return isBlankOrNone(jira.acceptanceCriteria())
                ? gap("Geen acceptatiecriteria opgegeven")
                : jira.acceptanceCriteria().strip();
    }

    static String riskNarrativeBody(String riskNarrative) {
        return safeNarrative(riskNarrative, "Geen risicoanalyse beschikbaar");
    }

    static String manualTestBody(JiraData jira) {
        JiraData.TestEvidence te = jira.testEvidence();
        if (te == null || isBlankOrNone(te.manualDescription())) {
            return gap("Geen handmatige testomschrijving opgegeven");
        }
        return te.manualDescription().strip();
    }

    static String automatedTestBody(JiraData jira) {
        var sb = new StringBuilder();
        JiraData.TestEvidence te = jira.testEvidence();
        if (te == null) {
            sb.append(gap("Geen geautomatiseerde testresultaten beschikbaar"));
        } else {
            if (te.hasAutomatedTests()) {
                sb.append(
                        "- Resultaat: %s\n".formatted(isBlankOrNone(te.passFail()) ? gap("onbekend") : te.passFail()));
            } else {
                sb.append("- Resultaat: ")
                        .append(gap("Geen geautomatiseerde tests gerapporteerd"))
                        .append('\n');
            }
            sb.append("- Coverage: %s"
                    .formatted(isBlankOrNone(te.coveragePercent()) ? gap("onbekend") : te.coveragePercent()));
        }
        return sb.toString();
    }

    static String deploymentBody(JiraData jira) {
        var sb = new StringBuilder();
        JiraData.Deployment dep = jira.deployment();
        if (dep == null) {
            sb.append(gap("Geen uitleveringsinformatie beschikbaar"));
        } else {
            sb.append("Standaard uitlevering: %s\n".formatted(yesNo(dep.standardDeployment())));
            sb.append("Feature toggle: %s\n".formatted(yesNo(dep.featureToggle())));
            sb.append("Handmatig script: %s\n".formatted(yesNo(dep.manualScript())));
            sb.append("Hypercare: %s".formatted(yesNo(dep.hypercare())));
        }
        return sb.toString();
    }

    static String dodBody(JiraData jira) {
        if (jira.dodComplete()) {
            return "☑ DoD is gereed voor alle issues.";
        } else {
            return "☐ DoD is **niet** gereed voor alle issues.";
        }
    }

    // --- helpers ---

    static String formatAgentName(String raw) {
        if (raw == null) return "Unknown";
        return switch (raw) {
            case "diff-analyst" -> "Diff Analyst";
            case "process-assessor" -> "Process Assessor";
            case "evidence-checker" -> "Evidence Checker";
            default -> raw;
        };
    }

    static String detectTeam(ChangeData changes) {
        if (changes.commits().isEmpty()) return "Onbekend";
        Map<String, Long> teamCounts = changes.commits().stream()
                .collect(Collectors.groupingBy(c -> c.team() == null ? "Onbekend" : c.team(), Collectors.counting()));
        if (teamCounts.size() == 1) {
            String sole = teamCounts.keySet().iterator().next();
            return "unknown".equals(sole) ? "Onbekend (geen roster-match)" : sole;
        }
        return teamCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> "unknown".equals(e.getKey()) ? "Onbekend" : e.getKey())
                .collect(Collectors.joining(", "));
    }

    private static String safe(String value) {
        return isBlankOrNone(value) ? gap("niet opgegeven") : value.strip();
    }

    private static String safeNarrative(String narrative, String fallback) {
        return isBlankOrNone(narrative) ? gap(fallback) : narrative.strip();
    }

    static boolean isBlankOrNone(String value) {
        return nl.example.qualityreport.context.XmlUtils.isBlankOrNone(value);
    }

    private static String gap(String reason) {
        return "_[evidence gap: %s]_".formatted(reason);
    }

    private static String yesNo(boolean value) {
        return value ? "Ja" : "Nee";
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) return value == null ? "" : value;
        return value.substring(0, maxLen) + "...";
    }
}
