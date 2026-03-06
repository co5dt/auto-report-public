package nl.example.qualityreport.model;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prints a human-readable summary of change data to a stream.
 * Extracted from {@link ChangeData} to separate presentation from domain.
 */
public final class ChangeSummaryPrinter {

    private ChangeSummaryPrinter() {}

    public static void print(ChangeData changes, PrintStream out) {
        out.printf("  Commits:     %d%n", changes.commits().size());

        Map<String, List<CommitInfo>> byAuthor =
                changes.commits().stream().collect(Collectors.groupingBy(c -> c.author() + " [" + c.role() + "]"));
        out.printf("  Authors:     %d  (%s)%n", byAuthor.size(), String.join(", ", byAuthor.keySet()));

        out.printf(
                "  Files:       %d changed (+%d / -%d)%n",
                changes.changedFiles().size(), changes.totalInsertions(), changes.totalDeletions());

        changes.groupByRole()
                .forEach((role, summary) -> out.printf(
                        "  %s changes:  %d commits, %d files, +%d / -%d%n",
                        role,
                        summary.commitCount(),
                        summary.filesChanged(),
                        summary.insertions(),
                        summary.deletions()));

        out.printf("  iDoc:        %d templates%n", changes.iDocCount());
        out.printf("  Scripts:     %d%n", changes.scriptCount());

        List<String> unmatchedEmails = changes.unmatchedEmails();
        if (!unmatchedEmails.isEmpty()) {
            out.printf(
                    "%n  WARNING: %d commit(s) from unrecognised email(s) not in roster: %s%n",
                    unmatchedEmails.size(), String.join(", ", unmatchedEmails));
        }
    }
}
