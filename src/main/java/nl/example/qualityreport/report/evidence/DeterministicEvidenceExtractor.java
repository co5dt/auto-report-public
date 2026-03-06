package nl.example.qualityreport.report.evidence;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nl.example.qualityreport.context.DiffSummariser;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.report.evidence.EvidenceFact.FactType;

/**
 * Deterministically extracts {@link EvidenceFact}s from structured change data.
 * No LLM calls — uses parsing, regex, and heuristics only.
 */
public final class DeterministicEvidenceExtractor {

    private static final Pattern JAVA_CLASS_NAME = Pattern.compile("([A-Z][a-zA-Z0-9]+)(?:\\.java)$");

    private static final Pattern SQL_MIGRATION_NAME =
            Pattern.compile("(V\\d+__[\\w]+)\\.sql$", Pattern.CASE_INSENSITIVE);

    private static final Pattern ANNOTATION_STRING = Pattern.compile("@\\w+\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");

    private static final Pattern REQUEST_MAPPING =
            Pattern.compile("@(?:Request|Get|Post|Put|Delete|Patch)Mapping\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");

    private static final Pattern CONFIG_PROPERTY_KEY = Pattern.compile("^\\+\\s*([a-z][a-z0-9._-]+)\\s*=");

    private static final Pattern CONSTANT_FIELD_DECL = Pattern.compile(
            "(?:public\\s+)?(?:static\\s+final|final\\s+static)\\s+[\\w<>\\[\\],\\s?]+\\s+([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+)\\s*[=;]");

    private static final Set<String> NOISY_CONSTANTS =
            Set.of("LOGGER", "LOG", "TAG", "INSTANCE", "SERIAL_VERSION_UID", "serialVersionUID");

    private DeterministicEvidenceExtractor() {}

    public static EvidenceBundle extract(ChangeData changes, JiraData jira) {
        var facts = new ArrayList<EvidenceFact>();
        int seq = 0;

        seq = extractTickets(jira, facts, seq);
        seq = extractClassNames(changes, facts, seq);
        seq = extractMigrations(changes, facts, seq);
        seq = extractMethods(changes, facts, seq);
        seq = extractAnnotationLiterals(changes, facts, seq);
        seq = extractApiPaths(changes, facts, seq);
        seq = extractConstantFields(changes, facts, seq);
        extractConfigKeys(changes, facts, seq);

        return new EvidenceBundle(List.copyOf(facts));
    }

    static int extractTickets(JiraData jira, List<EvidenceFact> facts, int seq) {
        for (String ticket : jira.tickets()) {
            facts.add(new EvidenceFact("ticket-" + seq++, FactType.TICKET, ticket, "jira.tickets", true));
        }
        return seq;
    }

    static int extractClassNames(ChangeData changes, List<EvidenceFact> facts, int seq) {
        var seen = new LinkedHashSet<String>();
        for (String path : changes.changedFiles()) {
            Matcher m = JAVA_CLASS_NAME.matcher(path);
            if (m.find()) {
                seen.add(m.group(1));
            }
        }
        for (String className : seen) {
            facts.add(new EvidenceFact("class-" + seq++, FactType.CLASS_NAME, className, "changedFiles", true));
        }
        return seq;
    }

    static int extractMigrations(ChangeData changes, List<EvidenceFact> facts, int seq) {
        var seen = new LinkedHashSet<String>();
        for (String path : changes.changedFiles()) {
            Matcher m = SQL_MIGRATION_NAME.matcher(path);
            if (m.find()) {
                seen.add(m.group(1));
            }
        }
        for (String migration : seen) {
            facts.add(new EvidenceFact("migration-" + seq++, FactType.MIGRATION, migration, "changedFiles", true));
        }
        return seq;
    }

    static int extractMethods(ChangeData changes, List<EvidenceFact> facts, int seq) {
        List<DiffSummariser.FileDiff> fileDiffs = DiffSummariser.parseFileDiffs(changes.rawDiff());
        var seen = new LinkedHashSet<String>();
        for (DiffSummariser.FileDiff fd : fileDiffs) {
            List<String> added = DiffSummariser.extractMethodNames(fd.hunks(), "+");
            seen.addAll(added);
        }
        for (String method : seen) {
            facts.add(new EvidenceFact("method-" + seq++, FactType.METHOD, method, "diff.added_methods", false));
        }
        return seq;
    }

    static int extractAnnotationLiterals(ChangeData changes, List<EvidenceFact> facts, int seq) {
        List<DiffSummariser.FileDiff> fileDiffs = DiffSummariser.parseFileDiffs(changes.rawDiff());
        var seen = new LinkedHashSet<String>();
        for (DiffSummariser.FileDiff fd : fileDiffs) {
            for (String hunk : fd.hunks()) {
                for (String line : hunk.split("\n")) {
                    if (!line.startsWith("+") || line.startsWith("+++")) continue;
                    Matcher m = ANNOTATION_STRING.matcher(line);
                    while (m.find()) {
                        String literal = m.group(1);
                        if (!isBoringLiteral(literal)) {
                            seen.add(literal);
                        }
                    }
                }
            }
        }
        for (String literal : seen) {
            facts.add(new EvidenceFact(
                    "literal-" + seq++, FactType.ANNOTATION_LITERAL, literal, "diff.annotation_values", true));
        }
        return seq;
    }

    static int extractApiPaths(ChangeData changes, List<EvidenceFact> facts, int seq) {
        List<DiffSummariser.FileDiff> fileDiffs = DiffSummariser.parseFileDiffs(changes.rawDiff());
        var seen = new LinkedHashSet<String>();
        for (DiffSummariser.FileDiff fd : fileDiffs) {
            for (String hunk : fd.hunks()) {
                for (String line : hunk.split("\n")) {
                    if (!line.startsWith("+") || line.startsWith("+++")) continue;
                    Matcher m = REQUEST_MAPPING.matcher(line);
                    while (m.find()) {
                        seen.add(m.group(1));
                    }
                }
            }
        }
        for (String path : seen) {
            facts.add(new EvidenceFact("api-" + seq++, FactType.API_PATH, path, "diff.request_mappings", false));
        }
        return seq;
    }

    static int extractConstantFields(ChangeData changes, List<EvidenceFact> facts, int seq) {
        List<DiffSummariser.FileDiff> fileDiffs = DiffSummariser.parseFileDiffs(changes.rawDiff());

        var declared = new LinkedHashSet<String>();
        var referenced = new LinkedHashSet<String>();

        for (DiffSummariser.FileDiff fd : fileDiffs) {
            if (!fd.path().endsWith(".java")) continue;
            boolean isTestFile = fd.path().contains("/test/");

            for (String hunk : fd.hunks()) {
                for (String line : hunk.split("\n")) {
                    if (!line.startsWith("+") || line.startsWith("+++")) continue;

                    Matcher m = CONSTANT_FIELD_DECL.matcher(line);
                    while (m.find()) {
                        String name = m.group(1);
                        if (!NOISY_CONSTANTS.contains(name) && !isTestFile) {
                            declared.add(name);
                        }
                    }

                    for (String candidate : declared) {
                        if (!CONSTANT_FIELD_DECL.matcher(line).find() && line.contains(candidate) && !isTestFile) {
                            referenced.add(candidate);
                        }
                    }
                }
            }
        }

        for (String name : declared) {
            boolean used = referenced.contains(name);
            facts.add(new EvidenceFact("const-" + seq++, FactType.CONSTANT_FIELD, name, "diff.constant_fields", used));
        }
        return seq;
    }

    static int extractConfigKeys(ChangeData changes, List<EvidenceFact> facts, int seq) {
        List<DiffSummariser.FileDiff> fileDiffs = DiffSummariser.parseFileDiffs(changes.rawDiff());
        for (DiffSummariser.FileDiff fd : fileDiffs) {
            if (!isPropertiesFile(fd.path())) continue;
            for (String hunk : fd.hunks()) {
                for (String line : hunk.split("\n")) {
                    Matcher m = CONFIG_PROPERTY_KEY.matcher(line);
                    if (m.find()) {
                        facts.add(new EvidenceFact(
                                "config-" + seq++, FactType.CONFIG_KEY, m.group(1), "diff.config:" + fd.path(), false));
                    }
                }
            }
        }
        return seq;
    }

    private static boolean isBoringLiteral(String literal) {
        if (literal.length() <= 2) return true;
        if (literal.startsWith("$") || literal.startsWith("#")) return true;
        return literal.matches("^\\d+$");
    }

    private static boolean isPropertiesFile(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".properties") || lower.endsWith(".yml") || lower.endsWith(".yaml");
    }
}
