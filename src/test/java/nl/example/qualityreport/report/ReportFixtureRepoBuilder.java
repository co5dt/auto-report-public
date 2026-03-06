package nl.example.qualityreport.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.api.Git;

/**
 * Materializes a {@link ReportE2EScenario.RepoRecipe} into a real JGit
 * repository on disk. Handles branch creation, multi-commit histories,
 * and roster file generation.
 */
final class ReportFixtureRepoBuilder {

    private ReportFixtureRepoBuilder() {}

    static Path build(ReportE2EScenario scenario, Path baseDir) throws Exception {
        return buildSingleRepo(scenario.repo(), scenario.branchName(), baseDir.resolve("repo"));
    }

    static List<Path> buildAll(ReportE2EScenario scenario, Path baseDir) throws Exception {
        var repoDirs = new ArrayList<Path>();
        int idx = 0;
        for (var recipe : scenario.allRepos()) {
            Path repoDir = baseDir.resolve("repo-" + idx);
            buildSingleRepo(recipe, scenario.branchName(), repoDir);
            repoDirs.add(repoDir);
            idx++;
        }
        return repoDirs;
    }

    private static Path buildSingleRepo(ReportE2EScenario.RepoRecipe recipe, String branchName, Path repoDir)
            throws Exception {
        Files.createDirectories(repoDir);

        try (Git git = Git.init()
                .setInitialBranch("main")
                .setDirectory(repoDir.toFile())
                .call()) {
            Path readme = repoDir.resolve("README.md");
            Files.writeString(readme, "# " + recipe.moduleName() + "\n");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("initial commit")
                    .setAuthor("System", "system@example.nl")
                    .call();

            git.branchCreate().setName(branchName).call();
            git.checkout().setName(branchName).call();

            for (var commit : recipe.commits()) {
                for (var entry : commit.files().entrySet()) {
                    Path file = repoDir.resolve(entry.getKey());
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, entry.getValue());
                }
                git.add().addFilepattern(".").call();
                git.commit()
                        .setSign(false)
                        .setMessage(commit.message())
                        .setAuthor(commit.authorName(), commit.authorEmail())
                        .call();
            }
        }

        return repoDir;
    }

    static Path writeRoster(ReportE2EScenario scenario, Path baseDir) throws IOException {
        Map<String, java.util.List<Map<String, Object>>> teamMembers = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (var recipe : scenario.allRepos()) {
            for (var entry : recipe.roster()) {
                String key = entry.email() + "|" + entry.team();
                if (seen.add(key)) {
                    Map<String, Object> memberMap = new LinkedHashMap<>();
                    memberMap.put("name", entry.name());
                    memberMap.put("email", entry.email());
                    if (entry.emails() != null && !entry.emails().isEmpty()) {
                        memberMap.put("emails", entry.emails());
                    }
                    memberMap.put("role", entry.role());
                    teamMembers
                            .computeIfAbsent(entry.team(), k -> new java.util.ArrayList<>())
                            .add(memberMap);
                }
            }
        }

        Map<String, Object> teamsMap = new LinkedHashMap<>();
        for (var entry : teamMembers.entrySet()) {
            teamsMap.put(entry.getKey(), Map.of("members", entry.getValue()));
        }

        Map<String, Object> root = Map.of("teams", teamsMap);
        Path rosterPath = baseDir.resolve("roster.json");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(rosterPath.toFile(), root);
        return rosterPath;
    }

    static String buildScriptedInput(ReportE2EScenario scenario) {
        var jira = scenario.jira();
        var sb = new StringBuilder();

        sb.append(String.join(", ", jira.tickets())).append('\n');

        sb.append(jira.description()).append('\n');
        sb.append('\n');

        for (String line : jira.acceptanceCriteria().split("\n")) {
            sb.append(line).append('\n');
        }
        sb.append('\n');

        Set<String> autoDetected = predictAutoDetected(scenario);
        for (var override : jira.impactOverrides()) {
            if (autoDetected.contains(override.area())) {
                continue;
            }
            sb.append(override.checked() ? "y" : "n").append('\n');
        }

        sb.append(jira.manualTestDescription()).append('\n');
        sb.append('\n');

        if (jira.hasAutomatedTests()) {
            sb.append("y\n");
            sb.append(jira.automatedTestResult()).append('\n');
            sb.append(jira.coveragePercent()).append('\n');
        } else {
            sb.append("n\n");
        }

        sb.append(jira.standardDeployment() ? "y" : "n").append('\n');
        sb.append(jira.featureToggle() ? "y" : "n").append('\n');
        sb.append(jira.manualScript() ? "y" : "n").append('\n');
        sb.append(jira.hypercare() ? "y" : "n").append('\n');

        sb.append(jira.dodComplete() ? "y" : "n").append('\n');

        sb.append(scenario.fixVersion()).append('\n');

        return sb.toString();
    }

    /**
     * Mirrors InteractivePrompter.detectImpactSignals() logic to predict
     * which impact areas will be auto-checked, so we skip them in scripted input.
     */
    private static Set<String> predictAutoDetected(ReportE2EScenario scenario) {
        Set<String> detected = new LinkedHashSet<>();
        List<String> allFiles = new ArrayList<>();
        for (var recipe : scenario.allRepos()) {
            for (var commit : recipe.commits()) {
                allFiles.addAll(commit.files().keySet());
            }
        }

        boolean hasScript = allFiles.stream().anyMatch(f -> {
            String lower = f.toLowerCase();
            return lower.endsWith(".sql") || lower.contains("migration") || lower.contains("migrate");
        });
        if (hasScript) {
            detected.add("Database(s) wijzigingen");
        }

        boolean hasFrontend = allFiles.stream().anyMatch(f -> {
            String lower = f.toLowerCase();
            return lower.endsWith(".js")
                    || lower.endsWith(".ts")
                    || lower.endsWith(".jsx")
                    || lower.endsWith(".tsx")
                    || lower.endsWith(".vue")
                    || lower.endsWith(".css")
                    || lower.endsWith(".scss");
        });
        if (hasFrontend) {
            detected.add("Front-end / Javascript");
        }

        if (scenario.isMultiRepo()) {
            detected.add("Impact op meer dan 1 module");
        }

        long distinctModules = allFiles.stream()
                .map(f -> f.contains("/") ? f.substring(0, f.indexOf('/')) : "root")
                .distinct()
                .count();
        if (distinctModules > 1) {
            detected.add("Impact op meer dan 1 module");
        }

        return detected;
    }
}
