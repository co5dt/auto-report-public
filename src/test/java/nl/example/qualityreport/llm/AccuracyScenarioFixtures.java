package nl.example.qualityreport.llm;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import nl.example.qualityreport.git.GitExtractor;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.roster.Roster;

/**
 * Reusable accuracy-test scenarios backed by real temporary git repositories.
 * Each scenario materialises a repo, extracts {@link ChangeData} via
 * {@link GitExtractor}, and bundles it with realistic Jira data and
 * ground-truth facts for accuracy scoring.
 */
final class AccuracyScenarioFixtures {

    private AccuracyScenarioFixtures() {}

    record Scenario(
            String name,
            String description,
            ChangeData changeData,
            JiraData jiraData,
            List<String> criticalFacts,
            List<String> expectedAreas,
            List<String> knownAbsentClaims) {}

    static Scenario smallScenario(Path tempDir) throws Exception {
        var fixture = ScenarioGitRepoBuilder.buildSmallScenarioRepo(tempDir);
        ChangeData changeData = extractChangeData(fixture);

        return new Scenario(
                "small-cache-fix",
                "Small bug fix: cache invalidation on PL update (2 commits, 1 team)",
                changeData,
                fullJiraData(),
                List.of("cache invalidation", "BSN", "PlCache", "expired entry", "PL update"),
                List.of("MuniPortal", "Database(s) wijzigingen"),
                List.of(
                        "token revocation",
                        "MetricsCollector",
                        "FeatureFlags",
                        "CacheEventPublisher",
                        "DataSliceService"));
    }

    static Scenario largeScenario(Path tempDir) throws Exception {
        var fixture = ScenarioGitRepoBuilder.buildLargeScenarioRepo(tempDir);
        ChangeData changeData = extractChangeData(fixture);

        return new Scenario(
                "large-cache-overhaul",
                "Large change: cache TTL, metrics, token revocation, event publishing (3 commits, 2 teams)",
                changeData,
                fullJiraData(),
                List.of(
                        "cache TTL",
                        "SQL migration",
                        "V42__add_cache_ttl_column",
                        "token revocation",
                        "MetricsCollector",
                        "CacheEventPublisher",
                        "DataSliceService",
                        "FeatureFlags",
                        "GbaVQueryHandler"),
                List.of("MuniPortal", "Database(s) wijzigingen"),
                List.of());
    }

    private static ChangeData extractChangeData(ScenarioGitRepoBuilder.RepoFixture fixture) throws Exception {
        Roster roster = Roster.load(fixture.rosterPath());
        GitExtractor extractor = new GitExtractor(roster);
        return extractor.extract(List.of(fixture.repoDir()), "feature/test", "main");
    }

    private static JiraData fullJiraData() {
        return new JiraData(
                List.of("PROJ-128530", "PROJ-128531"),
                "The GBA-V query for category 08 returns stale data after PL update.",
                "- After PL update, subsequent query returns fresh data\n- Cache TTL respects configured window",
                new JiraData.Impact(Map.of(
                        "MuniPortal", true,
                        "Database(s) wijzigingen", true,
                        "Front-end / Javascript", false)),
                new JiraData.TestEvidence(
                        "Verified locally that fresh data is returned", true, "42 passed, 0 failed", "78%"),
                new JiraData.Deployment(true, false, false, false),
                true,
                "5.13.0");
    }
}
