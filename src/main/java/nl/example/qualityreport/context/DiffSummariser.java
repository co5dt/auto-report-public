package nl.example.qualityreport.context;

import static nl.example.qualityreport.context.XmlUtils.escapeXml;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses unified diffs and provides file-level summaries and high-signal hunk selection.
 * Consumed by {@link ContextBuilder} and the evidence extraction pipeline.
 */
public final class DiffSummariser {

    static final int FULL_DIFF_THRESHOLD = 500;
    static final int HUNK_BUDGET_LINES = 200;

    private static final Pattern DIFF_HEADER = Pattern.compile("^diff --git a/(.*) b/(.*)$");
    private static final Pattern METHOD_SIGNATURE =
            Pattern.compile("(?:public|protected|private)?\\s+(?:static\\s+)?\\S+\\s+(\\w+)\\s*\\(");
    private static final Set<String> CONFIG_EXTENSIONS =
            Set.of(".yml", ".yaml", ".properties", ".xml", ".json", ".toml", ".env", ".conf", ".cfg");
    private static final Set<String> SECURITY_KEYWORDS = Set.of(
            "auth",
            "security",
            "encrypt",
            "decrypt",
            "password",
            "credential",
            "token",
            "oauth",
            "permission",
            "access",
            "acl");

    private DiffSummariser() {}

    public record FileDiff(
            String path, String status, int insertions, int deletions, List<String> hunks, boolean binary) {
        public FileDiff(String path, String status, int insertions, int deletions, List<String> hunks) {
            this(path, status, insertions, deletions, hunks, false);
        }

        int churn() {
            return insertions + deletions;
        }
    }

    public enum HunkPriority {
        MIGRATION,
        PUBLIC_API,
        CONFIG,
        SECURITY,
        HIGH_CHURN
    }

    public record SelectedHunk(String file, HunkPriority priority, String reason, String content, int lineCount) {}

    static boolean isFullMode(int diffLineCount) {
        return diffLineCount <= FULL_DIFF_THRESHOLD;
    }

    public static List<FileDiff> parseFileDiffs(String rawDiff) {
        if (rawDiff == null || rawDiff.isBlank()) {
            return List.of();
        }

        var result = new ArrayList<FileDiff>();
        String[] lines = rawDiff.split("\n");
        String currentPath = null;
        int ins = 0, del = 0;
        var currentHunks = new ArrayList<String>();
        var currentHunk = new StringBuilder();
        boolean inHunk = false;
        boolean isBinary = false;

        for (String line : lines) {
            Matcher headerMatch = DIFF_HEADER.matcher(line);
            if (headerMatch.matches()) {
                if (currentPath != null) {
                    if (inHunk && !currentHunk.isEmpty()) {
                        currentHunks.add(currentHunk.toString());
                    }
                    result.add(buildFileDiff(currentPath, ins, del, currentHunks, isBinary));
                }
                currentPath = headerMatch.group(2);
                ins = 0;
                del = 0;
                currentHunks = new ArrayList<>();
                currentHunk = new StringBuilder();
                inHunk = false;
                isBinary = false;
                continue;
            }

            if (line.startsWith("Binary files ") || line.startsWith("GIT binary patch")) {
                isBinary = true;
                continue;
            }

            if (line.startsWith("@@")) {
                if (inHunk && !currentHunk.isEmpty()) {
                    currentHunks.add(currentHunk.toString());
                }
                currentHunk = new StringBuilder();
                currentHunk.append(line).append('\n');
                inHunk = true;
                continue;
            }

            if (inHunk) {
                currentHunk.append(line).append('\n');
            }

            if (line.startsWith("+") && !line.startsWith("+++")) {
                ins++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                del++;
            }
        }

        if (currentPath != null) {
            if (inHunk && !currentHunk.isEmpty()) {
                currentHunks.add(currentHunk.toString());
            }
            result.add(buildFileDiff(currentPath, ins, del, currentHunks, isBinary));
        }

        result.sort(Comparator.comparing(FileDiff::path));
        return List.copyOf(result);
    }

    static String buildFilesChangedXml(List<FileDiff> files) {
        var sb = new StringBuilder();
        for (FileDiff f : files) {
            sb.append("    <file path=\"")
                    .append(escapeXml(f.path()))
                    .append("\" insertions=\"")
                    .append(f.insertions())
                    .append("\" deletions=\"")
                    .append(f.deletions())
                    .append("\" status=\"")
                    .append(f.status())
                    .append("\"");
            if (f.binary()) {
                sb.append(" binary=\"true\"");
            }
            sb.append(">\n");
            sb.append("      ").append(escapeXml(describeFile(f))).append('\n');
            sb.append("    </file>\n");
        }
        return sb.toString();
    }

    public static List<SelectedHunk> selectHighSignalHunks(List<FileDiff> files) {
        var selected = new ArrayList<SelectedHunk>();
        int usedLines = 0;

        // Priority 1: database migrations
        for (FileDiff f : files) {
            if (isSqlMigration(f.path())) {
                for (String hunk : f.hunks()) {
                    int hunkLines = countLines(hunk);
                    if (usedLines + hunkLines > HUNK_BUDGET_LINES && !selected.isEmpty()) break;
                    selected.add(
                            new SelectedHunk(f.path(), HunkPriority.MIGRATION, "database migration", hunk, hunkLines));
                    usedLines += hunkLines;
                }
            }
        }

        // Priority 2: public API changes
        for (FileDiff f : files) {
            if (usedLines >= HUNK_BUDGET_LINES) break;
            if (isSqlMigration(f.path())) continue;
            for (String hunk : f.hunks()) {
                if (usedLines >= HUNK_BUDGET_LINES) break;
                if (hasPublicMethodChanges(hunk)) {
                    int hunkLines = countLines(hunk);
                    if (usedLines + hunkLines > HUNK_BUDGET_LINES && !selected.isEmpty()) break;
                    selected.add(
                            new SelectedHunk(f.path(), HunkPriority.PUBLIC_API, "public API change", hunk, hunkLines));
                    usedLines += hunkLines;
                }
            }
        }

        // Priority 3: config changes
        for (FileDiff f : files) {
            if (usedLines >= HUNK_BUDGET_LINES) break;
            if (isConfigFile(f.path())) {
                for (String hunk : f.hunks()) {
                    if (usedLines >= HUNK_BUDGET_LINES) break;
                    int hunkLines = countLines(hunk);
                    if (usedLines + hunkLines > HUNK_BUDGET_LINES && !selected.isEmpty()) break;
                    selected.add(
                            new SelectedHunk(f.path(), HunkPriority.CONFIG, "configuration change", hunk, hunkLines));
                    usedLines += hunkLines;
                }
            }
        }

        // Priority 4: security-relevant
        for (FileDiff f : files) {
            if (usedLines >= HUNK_BUDGET_LINES) break;
            if (isSecurityRelated(f.path()) && !isConfigFile(f.path())) {
                for (String hunk : f.hunks()) {
                    if (usedLines >= HUNK_BUDGET_LINES) break;
                    int hunkLines = countLines(hunk);
                    if (usedLines + hunkLines > HUNK_BUDGET_LINES && !selected.isEmpty()) break;
                    selected.add(new SelectedHunk(
                            f.path(), HunkPriority.SECURITY, "security-relevant change", hunk, hunkLines));
                    usedLines += hunkLines;
                }
            }
        }

        // Priority 5: highest churn (fill remaining budget)
        var usedFiles = selected.stream().map(SelectedHunk::file).collect(Collectors.toSet());
        var byChurn = files.stream()
                .filter(f -> !usedFiles.contains(f.path()))
                .sorted(Comparator.comparingInt(FileDiff::churn).reversed())
                .toList();

        for (FileDiff f : byChurn) {
            if (usedLines >= HUNK_BUDGET_LINES) break;
            for (String hunk : f.hunks()) {
                int hunkLines = countLines(hunk);
                if (usedLines + hunkLines > HUNK_BUDGET_LINES) break;
                selected.add(new SelectedHunk(f.path(), HunkPriority.HIGH_CHURN, "high churn", hunk, hunkLines));
                usedLines += hunkLines;
            }
        }

        return List.copyOf(selected);
    }

    static String buildHighSignalHunksXml(List<SelectedHunk> hunks) {
        var sb = new StringBuilder();
        for (SelectedHunk h : hunks) {
            sb.append("    <hunk file=\"")
                    .append(escapeXml(h.file()))
                    .append("\" reason=\"")
                    .append(escapeXml(h.reason()))
                    .append("\">\n");
            sb.append(escapeXml(h.content().stripTrailing())).append('\n');
            sb.append("    </hunk>\n");
        }
        return sb.toString();
    }

    // --- file classification helpers ---

    public static boolean isSqlMigration(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".sql") || lower.contains("migration") || lower.contains("migrate");
    }

    static boolean isConfigFile(String path) {
        String lower = path.toLowerCase();
        return CONFIG_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    static boolean isSecurityRelated(String path) {
        String lower = path.toLowerCase();
        return SECURITY_KEYWORDS.stream().anyMatch(lower::contains);
    }

    static boolean hasPublicMethodChanges(String hunkContent) {
        for (String line : hunkContent.split("\n")) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                if (METHOD_SIGNATURE.matcher(line).find() && line.contains("public")) {
                    return true;
                }
            }
        }
        return false;
    }

    static String describeFile(FileDiff file) {
        if (file.binary()) {
            return "binary file";
        }
        if (isSqlMigration(file.path())) {
            return firstSqlStatement(file.hunks());
        }

        List<String> addedMethods = extractMethodNames(file.hunks(), "+");
        List<String> removedMethods = extractMethodNames(file.hunks(), "-");

        if (!addedMethods.isEmpty() && !removedMethods.isEmpty()) {
            return "Methods added: " + String.join(", ", addedMethods) + "; Methods removed: "
                    + String.join(", ", removedMethods);
        }
        if (!addedMethods.isEmpty()) {
            return "Methods added: " + String.join(", ", addedMethods);
        }
        if (!removedMethods.isEmpty()) {
            return "Methods removed: " + String.join(", ", removedMethods);
        }
        return "+%d / -%d lines".formatted(file.insertions(), file.deletions());
    }

    // --- internal helpers ---

    private static FileDiff buildFileDiff(String path, int ins, int del, List<String> hunks, boolean binary) {
        String status;
        if (binary) {
            status = "binary";
        } else if (del == 0 && ins > 0) {
            status = "added";
        } else if (ins == 0 && del > 0) {
            status = "deleted";
        } else {
            status = "modified";
        }
        return new FileDiff(path, status, ins, del, List.copyOf(hunks), binary);
    }

    public static List<String> extractMethodNames(List<String> hunks, String prefix) {
        var names = new LinkedHashSet<String>();
        for (String hunk : hunks) {
            for (String line : hunk.split("\n")) {
                if (line.startsWith(prefix) && !line.startsWith(prefix.repeat(3))) {
                    Matcher m = METHOD_SIGNATURE.matcher(line);
                    if (m.find()) {
                        names.add(m.group(1));
                    }
                }
            }
        }
        return List.copyOf(names);
    }

    private static String firstSqlStatement(List<String> hunks) {
        for (String hunk : hunks) {
            for (String line : hunk.split("\n")) {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    String sql = line.substring(1).strip();
                    if (!sql.isEmpty() && !sql.startsWith("--")) {
                        return sql.length() > 100 ? sql.substring(0, 100) + "..." : sql;
                    }
                }
            }
        }
        return "SQL migration";
    }

    private static int countLines(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\n", -1).length;
    }
}
