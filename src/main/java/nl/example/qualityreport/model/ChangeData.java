package nl.example.qualityreport.model;

import java.io.PrintStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record ChangeData(List<CommitInfo> commits, String rawDiff, List<RepoFile> repoFiles) {

    private static final Set<String> IDOC_EXTENSIONS = Set.of(".ftl", ".docx");
    private static final Set<String> SCRIPT_EXTENSIONS = Set.of(".sql");

    public static ChangeData from(List<CommitInfo> commits, String rawDiff, List<String> changedFiles) {
        List<RepoFile> files = changedFiles.stream().map(RepoFile::new).toList();
        return new ChangeData(List.copyOf(commits), rawDiff, List.copyOf(files));
    }

    public static ChangeData fromRepoFiles(List<CommitInfo> commits, String rawDiff, List<RepoFile> repoFiles) {
        return new ChangeData(List.copyOf(commits), rawDiff, List.copyOf(repoFiles));
    }

    /** Backward-compatible accessor returning plain paths. */
    public List<String> changedFiles() {
        return repoFiles.stream().map(RepoFile::path).toList();
    }

    public Map<String, RoleSummary> groupByRole() {
        return commits.stream().collect(Collectors.groupingBy(CommitInfo::role)).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> RoleSummary.fromCommits(e.getValue())));
    }

    public int totalInsertions() {
        return commits.stream().mapToInt(CommitInfo::insertions).sum();
    }

    public int totalDeletions() {
        return commits.stream().mapToInt(CommitInfo::deletions).sum();
    }

    public int diffLineCount() {
        if (rawDiff == null || rawDiff.isEmpty()) {
            return 0;
        }
        return rawDiff.split("\n", -1).length;
    }

    public int iDocCount() {
        return (int) repoFiles.stream()
                .map(RepoFile::path)
                .filter(ChangeData::isIDoc)
                .count();
    }

    public int scriptCount() {
        return (int) repoFiles.stream()
                .map(RepoFile::path)
                .filter(ChangeData::isScript)
                .count();
    }

    public Map<String, Long> countByFileType() {
        long iDocs = iDocCount();
        long scripts = scriptCount();
        long other = repoFiles.size() - iDocs - scripts;
        return Map.of(
                "iDoc", iDocs,
                "Scripts", scripts,
                "Other", other);
    }

    /**
     * @deprecated Use {@link ChangeSummaryPrinter#print(ChangeData, PrintStream)} instead.
     */
    @Deprecated(forRemoval = true)
    public void printSummary(PrintStream out) {
        ChangeSummaryPrinter.print(this, out);
    }

    public List<String> unmatchedEmails() {
        Set<String> emails = new LinkedHashSet<>();
        for (CommitInfo c : commits) {
            if ("unknown".equals(c.role())) {
                emails.add(c.email());
            }
        }
        return List.copyOf(emails);
    }

    public static boolean isIDoc(String path) {
        String lower = path.toLowerCase();
        return IDOC_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    public static boolean isScript(String path) {
        String lower = path.toLowerCase();
        if (SCRIPT_EXTENSIONS.stream().anyMatch(lower::endsWith)) {
            return true;
        }
        return lower.contains("migration") || lower.contains("migrate");
    }

    public record RoleSummary(int commitCount, int filesChanged, int insertions, int deletions) {
        static RoleSummary fromCommits(List<CommitInfo> commits) {
            int files = commits.stream().mapToInt(CommitInfo::filesChanged).sum();
            int ins = commits.stream().mapToInt(CommitInfo::insertions).sum();
            int del = commits.stream().mapToInt(CommitInfo::deletions).sum();
            return new RoleSummary(commits.size(), files, ins, del);
        }
    }
}
