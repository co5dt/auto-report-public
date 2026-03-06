package nl.example.qualityreport.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import nl.example.qualityreport.model.ChangeData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contract test validating that real git repo fixtures produce meaningful
 * {@link ChangeData}. Runs on plain {@code mvn test} (no Ollama required).
 */
class AccuracyScenarioFixturesTest {

    @TempDir
    Path tempDir;

    @Test
    void smallScenario_extractsNonEmptyChangeData() throws Exception {
        var scenario = AccuracyScenarioFixtures.smallScenario(tempDir.resolve("small"));
        ChangeData cd = scenario.changeData();

        assertThat(cd.commits()).hasSize(2);
        assertThat(cd.rawDiff()).isNotBlank();
        assertThat(cd.changedFiles())
                .contains("src/main/java/nl/example/cache/PlCache.java", "src/test/java/nl/example/cache/PlCacheTest.java");

        assertThat(cd.commits()).anySatisfy(c -> {
            assertThat(c.author()).isEqualTo("Alice Dev");
            assertThat(c.role()).isEqualTo("BE");
            assertThat(c.team()).isEqualTo("Team Alpha");
        });
        assertThat(cd.commits()).anySatisfy(c -> {
            assertThat(c.author()).isEqualTo("Bob Test");
            assertThat(c.role()).isEqualTo("TE");
            assertThat(c.team()).isEqualTo("Team Alpha");
        });

        assertThat(cd.rawDiff()).contains("invalidate");
        assertThat(cd.rawDiff()).contains("BSN");
        assertThat(cd.rawDiff()).contains("PlCache");
    }

    @Test
    void largeScenario_extractsNonEmptyChangeData() throws Exception {
        var scenario = AccuracyScenarioFixtures.largeScenario(tempDir.resolve("large"));
        ChangeData cd = scenario.changeData();

        assertThat(cd.commits()).hasSize(3);
        assertThat(cd.rawDiff()).isNotBlank();
        assertThat(cd.changedFiles())
                .contains(
                        "migrations/V42__add_cache_ttl_column.sql",
                        "src/main/java/nl/example/cache/PlCache.java",
                        "src/main/java/nl/example/query/GbaVQueryHandler.java",
                        "src/main/java/nl/example/auth/TokenValidator.java",
                        "src/main/java/nl/example/metrics/MetricsCollector.java",
                        "src/main/java/nl/example/event/CacheEventPublisher.java",
                        "src/main/java/nl/example/service/DataSliceService.java",
                        "src/main/java/nl/example/service/FeatureFlags.java");

        assertThat(cd.commits()).anySatisfy(c -> {
            assertThat(c.author()).isEqualTo("Alice Dev");
            assertThat(c.team()).isEqualTo("Team Alpha");
        });
        assertThat(cd.commits()).anySatisfy(c -> {
            assertThat(c.author()).isEqualTo("Charlie Sec");
            assertThat(c.team()).isEqualTo("Team Beta");
        });

        assertThat(cd.rawDiff()).contains("MetricsCollector");
        assertThat(cd.rawDiff()).contains("CacheEventPublisher");
        assertThat(cd.rawDiff()).contains("FeatureFlags");
        assertThat(cd.rawDiff()).contains("revokeToken");
        assertThat(cd.rawDiff()).contains("V42__add_cache_ttl_column");
    }

    @Test
    void scenariosProvideJiraData() throws Exception {
        var small = AccuracyScenarioFixtures.smallScenario(tempDir.resolve("small-jira"));
        assertThat(small.jiraData()).isNotNull();
        assertThat(small.jiraData().tickets()).contains("PROJ-128530");

        var large = AccuracyScenarioFixtures.largeScenario(tempDir.resolve("large-jira"));
        assertThat(large.jiraData()).isNotNull();
        assertThat(large.criticalFacts()).isNotEmpty();
    }
}
