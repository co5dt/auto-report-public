package nl.example.qualityreport.context;

import static nl.example.qualityreport.context.XmlUtils.*;

import java.util.*;
import java.util.stream.Collectors;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.CommitInfo;
import nl.example.qualityreport.model.JiraData;

/**
 * Assembles the shared XML context consumed by all deliberation agents.
 * Single public method: {@link #build(ChangeData, JiraData)}.
 */
public class ContextBuilder {

    public String build(ChangeData changes, JiraData jira) {
        var sb = new StringBuilder();
        sb.append("<context>\n");
        sb.append(buildChangeOverview(changes, jira));
        sb.append(buildJira(jira));
        sb.append(buildDiffContent(changes));
        sb.append(buildImpact(jira));
        sb.append(buildTestEvidence(jira));
        sb.append(buildDeployment(jira));
        sb.append("</context>");
        return sb.toString();
    }

    // --- section builders ---

    private String buildChangeOverview(ChangeData changes, JiraData jira) {
        var sb = new StringBuilder();
        sb.append("  <change_overview>\n");

        sb.append("    <tickets>")
                .append(escapeXml(String.join(", ", jira.tickets())))
                .append("</tickets>\n");
        if (isBlankOrNone(jira.fixVersion())) {
            sb.append("    <fix_version>")
                    .append(noData("Fix version not provided"))
                    .append("</fix_version>\n");
        } else {
            sb.append("    <fix_version>").append(escapeXml(jira.fixVersion())).append("</fix_version>\n");
        }

        sb.append(buildAuthors(changes));
        sb.append(buildStats(changes));
        sb.append(buildChangesByRole(changes));
        sb.append(buildChangesByType(changes));
        sb.append(buildCommitMessages(changes));

        sb.append("  </change_overview>\n");
        return sb.toString();
    }

    private String buildAuthors(ChangeData changes) {
        var sb = new StringBuilder();
        sb.append("    <authors>\n");

        var byAuthor = changes.commits().stream()
                .collect(Collectors.groupingBy(
                        c -> c.author() + "|" + c.role() + "|" + c.team(), LinkedHashMap::new, Collectors.toList()));

        var sorted = byAuthor.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().getFirst().author()))
                .toList();

        for (var entry : sorted) {
            CommitInfo sample = entry.getValue().getFirst();
            sb.append("      <author name=\"")
                    .append(escapeXml(sample.author()))
                    .append("\" role=\"")
                    .append(escapeXml(sample.role()))
                    .append("\" team=\"")
                    .append(escapeXml(sample.team()))
                    .append("\" commits=\"")
                    .append(entry.getValue().size())
                    .append("\"/>\n");
        }

        sb.append("    </authors>\n");
        return sb.toString();
    }

    private String buildStats(ChangeData changes) {
        return """
                    <stats>
                      <total_commits>%d</total_commits>
                      <total_files_changed>%d</total_files_changed>
                      <total_insertions>%d</total_insertions>
                      <total_deletions>%d</total_deletions>
                    </stats>
               """
                .formatted(
                        changes.commits().size(),
                        changes.changedFiles().size(),
                        changes.totalInsertions(),
                        changes.totalDeletions());
    }

    private String buildChangesByRole(ChangeData changes) {
        var sb = new StringBuilder();
        sb.append("    <changes_by_role>\n");

        changes.groupByRole().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    var r = entry.getValue();
                    sb.append("      <role name=\"")
                            .append(escapeXml(entry.getKey()))
                            .append("\" commits=\"")
                            .append(r.commitCount())
                            .append("\" files=\"")
                            .append(r.filesChanged())
                            .append("\" insertions=\"")
                            .append(r.insertions())
                            .append("\" deletions=\"")
                            .append(r.deletions())
                            .append("\"/>\n");
                });

        sb.append("    </changes_by_role>\n");
        return sb.toString();
    }

    private String buildChangesByType(ChangeData changes) {
        var sb = new StringBuilder();
        sb.append("    <changes_by_type>\n");

        Map<String, Long> types = changes.countByFileType();
        List.of("iDoc", "Scripts", "Other").forEach(type -> {
            long count = types.getOrDefault(type, 0L);
            List<String> files = findFilesByType(changes.changedFiles(), type);
            sb.append("      <type name=\"")
                    .append(escapeXml(type))
                    .append("\" count=\"")
                    .append(count)
                    .append("\"");
            if (!files.isEmpty()) {
                sb.append(" files=\"")
                        .append(escapeXml(String.join(", ", files)))
                        .append("\"");
            }
            sb.append("/>\n");
        });

        sb.append("    </changes_by_type>\n");
        return sb.toString();
    }

    private String buildCommitMessages(ChangeData changes) {
        var sb = new StringBuilder();
        sb.append("    <commit_messages>\n");

        changes.commits().stream()
                .sorted(Comparator.comparing(CommitInfo::date))
                .forEach(c -> {
                    sb.append("      <commit hash=\"")
                            .append(escapeXml(c.hash()))
                            .append("\" author=\"")
                            .append(escapeXml(c.author()))
                            .append("\" role=\"")
                            .append(escapeXml(c.role()))
                            .append("\">\n");
                    sb.append("        ").append(escapeXml(c.message().strip())).append('\n');
                    sb.append("      </commit>\n");
                });

        sb.append("    </commit_messages>\n");
        return sb.toString();
    }

    private String buildJira(JiraData jira) {
        var sb = new StringBuilder();
        sb.append("  <jira>\n");

        if (isBlankOrNone(jira.description())) {
            sb.append("    <description>")
                    .append(noData("User indicated no description available"))
                    .append("</description>\n");
        } else {
            sb.append("    <description>\n      ")
                    .append(escapeXml(jira.description().strip()))
                    .append("\n    </description>\n");
        }

        if (isBlankOrNone(jira.acceptanceCriteria())) {
            sb.append("    <acceptance_criteria>")
                    .append(noData("User indicated no acceptance criteria defined in Jira"))
                    .append("</acceptance_criteria>\n");
        } else {
            sb.append("    <acceptance_criteria>\n      ")
                    .append(escapeXml(jira.acceptanceCriteria().strip()))
                    .append("\n    </acceptance_criteria>\n");
        }

        sb.append("  </jira>\n");
        return sb.toString();
    }

    private String buildDiffContent(ChangeData changes) {
        int lineCount = changes.diffLineCount();

        if (lineCount == 0) {
            return "  <diff_content mode=\"full\" lines=\"0\">\n    <raw_diff>" + noData("No diff content available")
                    + "</raw_diff>\n  </diff_content>\n";
        }

        if (DiffSummariser.isFullMode(lineCount)) {
            return "  <diff_content mode=\"full\" lines=\"%d\">\n    <raw_diff>\n%s\n    </raw_diff>\n  </diff_content>\n"
                    .formatted(lineCount, escapeXml(changes.rawDiff()));
        }

        var fileDiffs = DiffSummariser.parseFileDiffs(changes.rawDiff());
        var highSignalHunks = DiffSummariser.selectHighSignalHunks(fileDiffs);

        return """
                  <diff_content mode="summary" total_lines="%d" reason="Diff exceeds %d-line threshold; sending structured summary">
                    <files_changed count="%d">
                %s    </files_changed>
                    <high_signal_hunks>
                %s    </high_signal_hunks>
                  </diff_content>
                """
                .formatted(
                        lineCount,
                        DiffSummariser.FULL_DIFF_THRESHOLD,
                        fileDiffs.size(),
                        DiffSummariser.buildFilesChangedXml(fileDiffs),
                        DiffSummariser.buildHighSignalHunksXml(highSignalHunks));
    }

    private String buildImpact(JiraData jira) {
        var sb = new StringBuilder();
        sb.append("  <impact>\n");

        JiraData.Impact impact = jira.impact();
        JiraData.Impact.ALL_AREAS.forEach(area -> {
            boolean checked = impact != null && impact.isChecked(area);
            sb.append("    <checkbox name=\"")
                    .append(escapeXml(area))
                    .append("\" checked=\"")
                    .append(checked)
                    .append("\" source=\"user_confirmed\"/>\n");
        });

        sb.append("    <cross_team_signal detected=\"false\"/>\n");

        sb.append("  </impact>\n");
        return sb.toString();
    }

    private String buildTestEvidence(JiraData jira) {
        var sb = new StringBuilder();
        sb.append("  <test_evidence>\n");

        JiraData.TestEvidence te = jira.testEvidence();
        if (te == null) {
            sb.append("    <manual_testing>")
                    .append(noData("No test evidence provided"))
                    .append("</manual_testing>\n");
            sb.append("    <automated_testing>\n");
            sb.append("      <results>")
                    .append(noData("No automated test results provided"))
                    .append("</results>\n");
            sb.append("      <coverage>")
                    .append(noData("No coverage data provided"))
                    .append("</coverage>\n");
            sb.append("    </automated_testing>\n");
        } else {
            if (isBlankOrNone(te.manualDescription())) {
                sb.append("    <manual_testing>")
                        .append(noData("User indicated no manual testing was performed"))
                        .append("</manual_testing>\n");
            } else {
                sb.append("    <manual_testing>\n      ")
                        .append(escapeXml(te.manualDescription().strip()))
                        .append("\n    </manual_testing>\n");
            }

            sb.append("    <automated_testing>\n");
            if (te.hasAutomatedTests()) {
                sb.append("      <results>")
                        .append(escapeXml(isBlankOrNone(te.passFail()) ? "unknown" : te.passFail()))
                        .append("</results>\n");
            } else {
                sb.append("      <results>")
                        .append(noData("No automated tests reported"))
                        .append("</results>\n");
            }
            sb.append("      <coverage>")
                    .append(escapeXml(isBlankOrNone(te.coveragePercent()) ? "unknown" : te.coveragePercent()))
                    .append("</coverage>\n");
            sb.append("    </automated_testing>\n");
        }

        sb.append("  </test_evidence>\n");
        return sb.toString();
    }

    private String buildDeployment(JiraData jira) {
        var sb = new StringBuilder();
        sb.append("  <deployment>\n");

        JiraData.Deployment dep = jira.deployment();
        if (dep == null) {
            sb.append("    ")
                    .append(noData("No deployment information provided"))
                    .append('\n');
        } else {
            sb.append("    <standard_rollout>").append(dep.standardDeployment()).append("</standard_rollout>\n");
            sb.append("    <feature_toggle>").append(dep.featureToggle()).append("</feature_toggle>\n");
            sb.append("    <manual_script_required>").append(dep.manualScript()).append("</manual_script_required>\n");
            sb.append("    <hypercare_needed>").append(dep.hypercare()).append("</hypercare_needed>\n");
        }

        sb.append("  </deployment>\n");
        return sb.toString();
    }

    // --- helpers ---

    private List<String> findFilesByType(List<String> files, String type) {
        return files.stream()
                .filter(f -> switch (type) {
                    case "iDoc" -> ChangeData.isIDoc(f);
                    case "Scripts" -> ChangeData.isScript(f);
                    default -> false;
                })
                .sorted()
                .toList();
    }
}
