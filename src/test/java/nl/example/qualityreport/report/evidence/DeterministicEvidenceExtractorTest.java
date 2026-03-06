package nl.example.qualityreport.report.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.CommitInfo;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.report.evidence.EvidenceFact.FactType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DeterministicEvidenceExtractorTest {

    @Nested
    class Tickets {

        @Test
        void extractsTicketsAsMustMention() {
            JiraData jira = jira(List.of("PROJ-1234", "PROJ-5678"));
            ChangeData changes = changes(List.of(), "", List.of());

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, jira);

            assertThat(bundle.mustMentionValues()).contains("PROJ-1234", "PROJ-5678");
            assertThat(bundle.facts().stream().filter(f -> f.type() == FactType.TICKET))
                    .hasSize(2)
                    .allMatch(EvidenceFact::mustMention);
        }
    }

    @Nested
    class ClassNames {

        @Test
        void extractsJavaClassNamesAsMustMention() {
            ChangeData changes = changes(
                    List.of(),
                    "",
                    List.of(
                            "src/main/java/nl/example/CacheConfig.java",
                            "src/main/java/nl/example/GbaLookupService.java",
                            "src/main/resources/application.properties"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            List<String> classValues = bundle.facts().stream()
                    .filter(f -> f.type() == FactType.CLASS_NAME)
                    .map(EvidenceFact::value)
                    .toList();
            assertThat(classValues).containsExactlyInAnyOrder("CacheConfig", "GbaLookupService");
        }

        @Test
        void ignoresNonJavaFiles() {
            ChangeData changes = changes(List.of(), "", List.of("README.md", "pom.xml", "src/main/resources/data.sql"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            assertThat(bundle.facts().stream().filter(f -> f.type() == FactType.CLASS_NAME))
                    .isEmpty();
        }
    }

    @Nested
    class Migrations {

        @Test
        void extractsSqlMigrationNames() {
            ChangeData changes = changes(
                    List.of(),
                    "",
                    List.of(
                            "src/main/resources/db/migration/V55__create_audit_log.sql",
                            "src/main/java/nl/example/Foo.java"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            List<String> migrations = bundle.facts().stream()
                    .filter(f -> f.type() == FactType.MIGRATION)
                    .map(EvidenceFact::value)
                    .toList();
            assertThat(migrations).containsExactly("V55__create_audit_log");
        }
    }

    @Nested
    class Methods {

        @Test
        void extractsAddedMethodNames() {
            String diff =
                    """
                    diff --git a/Foo.java b/Foo.java
                    @@ -0,0 +1,5 @@
                    +public class Foo {
                    +    public void lookupPerson(String bsn) {}
                    +    private int calculate(int a) { return a; }
                    +}
                    """;
            ChangeData changes = changes(List.of(), diff, List.of("Foo.java"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            List<String> methods = bundle.facts().stream()
                    .filter(f -> f.type() == FactType.METHOD)
                    .map(EvidenceFact::value)
                    .toList();
            assertThat(methods).contains("lookupPerson", "calculate");
        }

        @Test
        void methodsAreNotMustMention() {
            String diff =
                    """
                    diff --git a/Foo.java b/Foo.java
                    @@ -0,0 +1,3 @@
                    +public class Foo {
                    +    public void doWork() {}
                    +}
                    """;
            ChangeData changes = changes(List.of(), diff, List.of("Foo.java"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            assertThat(bundle.facts().stream().filter(f -> f.type() == FactType.METHOD))
                    .noneMatch(EvidenceFact::mustMention);
        }
    }

    @Nested
    class AnnotationLiterals {

        @Test
        void extractsCacheableAnnotationValue() {
            String diff =
                    """
                    diff --git a/Service.java b/Service.java
                    @@ -0,0 +1,5 @@
                    +@Service
                    +public class Service {
                    +    @Cacheable(value = "gba-persons", key = "#bsn")
                    +    public String lookup(String bsn) { return ""; }
                    +}
                    """;
            ChangeData changes = changes(List.of(), diff, List.of("Service.java"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            List<String> literals = bundle.facts().stream()
                    .filter(f -> f.type() == FactType.ANNOTATION_LITERAL)
                    .map(EvidenceFact::value)
                    .toList();
            assertThat(literals).contains("gba-persons");
        }

        @Test
        void ignoresBoringLiterals() {
            String diff =
                    """
                    diff --git a/Foo.java b/Foo.java
                    @@ -0,0 +1,3 @@
                    +@Value("${app.name}")
                    +@Retention("42")
                    +public class Foo {}
                    """;
            ChangeData changes = changes(List.of(), diff, List.of("Foo.java"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            assertThat(bundle.facts().stream().filter(f -> f.type() == FactType.ANNOTATION_LITERAL))
                    .isEmpty();
        }

        @Test
        void annotationLiteralsAreMustMention() {
            String diff =
                    """
                    diff --git a/Foo.java b/Foo.java
                    @@ -0,0 +1,3 @@
                    +@CacheEvict(value = "gba-persons")
                    +public class Foo {}
                    """;
            ChangeData changes = changes(List.of(), diff, List.of("Foo.java"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            assertThat(bundle.facts().stream().filter(f -> f.type() == FactType.ANNOTATION_LITERAL))
                    .allMatch(EvidenceFact::mustMention);
        }
    }

    @Nested
    class ApiPaths {

        @Test
        void extractsRequestMappingPaths() {
            String diff =
                    """
                    diff --git a/Controller.java b/Controller.java
                    @@ -0,0 +1,5 @@
                    +@RestController
                    +@RequestMapping("/api/v2/person")
                    +public class Controller {
                    +    @GetMapping("/{bsn}")
                    +    public String get(@PathVariable String bsn) { return ""; }
                    +}
                    """;
            ChangeData changes = changes(List.of(), diff, List.of("Controller.java"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            List<String> paths = bundle.facts().stream()
                    .filter(f -> f.type() == FactType.API_PATH)
                    .map(EvidenceFact::value)
                    .toList();
            assertThat(paths).contains("/api/v2/person", "/{bsn}");
        }
    }

    @Nested
    class ConfigKeys {

        @Test
        void extractsPropertyKeys() {
            String diff =
                    """
                    diff --git a/application.properties b/application.properties
                    @@ -0,0 +1,3 @@
                    +spring.cache.type=redis
                    +spring.redis.host=localhost
                    +cache.gba.ttl-minutes=15
                    """;
            ChangeData changes = changes(List.of(), diff, List.of("application.properties"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            List<String> keys = bundle.facts().stream()
                    .filter(f -> f.type() == FactType.CONFIG_KEY)
                    .map(EvidenceFact::value)
                    .toList();
            assertThat(keys).contains("spring.cache.type", "spring.redis.host", "cache.gba.ttl-minutes");
        }

        @Test
        void configKeysAreNotMustMention() {
            String diff =
                    """
                    diff --git a/application.properties b/application.properties
                    @@ -0,0 +1,1 @@
                    +app.name=test
                    """;
            ChangeData changes = changes(List.of(), diff, List.of("application.properties"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            assertThat(bundle.facts().stream().filter(f -> f.type() == FactType.CONFIG_KEY))
                    .noneMatch(EvidenceFact::mustMention);
        }
    }

    @Nested
    class ConstantFields {

        @Test
        void extractsUpperSnakeConstantsFromDiff() {
            String diff =
                    """
                    diff --git a/src/main/java/nl/example/config/TimeoutDefaults.java b/src/main/java/nl/example/config/TimeoutDefaults.java
                    @@ -0,0 +1,7 @@
                    +public final class TimeoutDefaults {
                    +    public static final int HTTP_TIMEOUT_MS = 5000;
                    +    public static final int DB_TIMEOUT_MS = 3000;
                    +    private TimeoutDefaults() {}
                    +}
                    """;
            ChangeData changes = changes(List.of(), diff, List.of("src/main/java/nl/example/config/TimeoutDefaults.java"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            List<String> constValues = bundle.facts().stream()
                    .filter(f -> f.type() == FactType.CONSTANT_FIELD)
                    .map(EvidenceFact::value)
                    .toList();
            assertThat(constValues).containsExactlyInAnyOrder("HTTP_TIMEOUT_MS", "DB_TIMEOUT_MS");
        }

        @Test
        void promotesReferencedConstantToMustMention() {
            String diff =
                    """
                    diff --git a/src/main/java/nl/example/contract/ErrorCodes.java b/src/main/java/nl/example/contract/ErrorCodes.java
                    @@ -0,0 +1,5 @@
                    +public final class ErrorCodes {
                    +    public static final String INVALID_BSN = "ERR_INVALID_BSN";
                    +    private ErrorCodes() {}
                    +}
                    diff --git a/src/main/java/nl/example/search/SearchService.java b/src/main/java/nl/example/search/SearchService.java
                    @@ -0,0 +1,6 @@
                    +public class SearchService {
                    +    public void search(String bsn) {
                    +        if (bsn == null) throw new IllegalArgumentException(ErrorCodes.INVALID_BSN);
                    +    }
                    +}
                    """;
            ChangeData changes = changes(
                    List.of(),
                    diff,
                    List.of(
                            "src/main/java/nl/example/contract/ErrorCodes.java",
                            "src/main/java/nl/example/search/SearchService.java"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            var invalidBsn = bundle.facts().stream()
                    .filter(f ->
                            f.type() == FactType.CONSTANT_FIELD && f.value().equals("INVALID_BSN"))
                    .findFirst();
            assertThat(invalidBsn).isPresent();
            assertThat(invalidBsn.get().mustMention()).isTrue();
        }

        @Test
        void declarationOnlyConstantIsOptional() {
            String diff =
                    """
                    diff --git a/src/main/java/nl/example/config/Limits.java b/src/main/java/nl/example/config/Limits.java
                    @@ -0,0 +1,4 @@
                    +public final class Limits {
                    +    public static final int MAX_RESULTS = 100;
                    +    private Limits() {}
                    +}
                    """;
            ChangeData changes = changes(List.of(), diff, List.of("src/main/java/nl/example/config/Limits.java"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            var maxResults = bundle.facts().stream()
                    .filter(f ->
                            f.type() == FactType.CONSTANT_FIELD && f.value().equals("MAX_RESULTS"))
                    .findFirst();
            assertThat(maxResults).isPresent();
            assertThat(maxResults.get().mustMention()).isFalse();
        }

        @Test
        void ignoresTestFileConstants() {
            String diff =
                    """
                    diff --git a/src/test/java/nl/example/TestConstants.java b/src/test/java/nl/example/TestConstants.java
                    @@ -0,0 +1,3 @@
                    +public class TestConstants {
                    +    public static final String TEST_BSN = "123456789";
                    +}
                    """;
            ChangeData changes = changes(List.of(), diff, List.of("src/test/java/nl/example/TestConstants.java"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            assertThat(bundle.facts().stream().filter(f -> f.type() == FactType.CONSTANT_FIELD))
                    .isEmpty();
        }

        @Test
        void filtersNoisyConstants() {
            String diff =
                    """
                    diff --git a/src/main/java/nl/example/Service.java b/src/main/java/nl/example/Service.java
                    @@ -0,0 +1,4 @@
                    +public class Service {
                    +    private static final Logger LOGGER = LoggerFactory.getLogger(Service.class);
                    +    public static final String REAL_CONSTANT = "value";
                    +}
                    """;
            ChangeData changes = changes(List.of(), diff, List.of("src/main/java/nl/example/Service.java"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            List<String> constValues = bundle.facts().stream()
                    .filter(f -> f.type() == FactType.CONSTANT_FIELD)
                    .map(EvidenceFact::value)
                    .toList();
            assertThat(constValues).containsExactly("REAL_CONSTANT");
            assertThat(constValues).doesNotContain("LOGGER");
        }

        @Test
        void requiresAtLeastOneUnderscore() {
            String diff =
                    """
                    diff --git a/src/main/java/nl/example/Config.java b/src/main/java/nl/example/Config.java
                    @@ -0,0 +1,3 @@
                    +public class Config {
                    +    public static final int MAXCOUNT = 50;
                    +}
                    """;
            ChangeData changes = changes(List.of(), diff, List.of("src/main/java/nl/example/Config.java"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, emptyJira());

            assertThat(bundle.facts().stream().filter(f -> f.type() == FactType.CONSTANT_FIELD))
                    .isEmpty();
        }
    }

    @Nested
    class FullExtraction {

        @Test
        void combinedExtractionFromRealisticScenario() {
            String diff =
                    """
                    diff --git a/src/main/java/nl/example/cache/CacheConfig.java b/src/main/java/nl/example/cache/CacheConfig.java
                    @@ -0,0 +1,10 @@
                    +@Configuration
                    +@EnableCaching
                    +public class CacheConfig {
                    +    @Bean
                    +    public RedisCacheConfiguration cacheConfiguration() {
                    +        return RedisCacheConfiguration.defaultCacheConfig()
                    +                .entryTtl(Duration.ofMinutes(15));
                    +    }
                    +}
                    diff --git a/src/main/java/nl/example/cache/GbaLookupService.java b/src/main/java/nl/example/cache/GbaLookupService.java
                    @@ -0,0 +1,8 @@
                    +@Service
                    +public class GbaLookupService {
                    +    @Cacheable(value = "gba-persons", key = "#bsn")
                    +    public String lookupPerson(String bsn) {
                    +        return performGbaVQuery(bsn);
                    +    }
                    +}
                    diff --git a/src/main/resources/application.properties b/src/main/resources/application.properties
                    @@ -0,0 +1,2 @@
                    +spring.cache.type=redis
                    +cache.gba.ttl-minutes=15
                    """;
            ChangeData changes = changes(
                    List.of(),
                    diff,
                    List.of(
                            "src/main/java/nl/example/cache/CacheConfig.java",
                            "src/main/java/nl/example/cache/GbaLookupService.java",
                            "src/main/resources/application.properties"));

            JiraData jira = jira(List.of("PROJ-90016"));

            EvidenceBundle bundle = DeterministicEvidenceExtractor.extract(changes, jira);

            List<String> mustMentionValues = bundle.mustMentionValues();
            assertThat(mustMentionValues).contains("PROJ-90016", "CacheConfig", "GbaLookupService", "gba-persons");
            assertThat(bundle.size()).isGreaterThan(4);
        }
    }

    @Nested
    class EvidenceBundleModel {

        @Test
        void partitionsMustMentionAndOptional() {
            var facts = List.of(
                    new EvidenceFact("t0", FactType.TICKET, "PROJ-1", "jira", true),
                    new EvidenceFact("m0", FactType.METHOD, "doWork", "diff", false));
            var bundle = new EvidenceBundle(facts);

            assertThat(bundle.mustMention()).hasSize(1);
            assertThat(bundle.optional()).hasSize(1);
            assertThat(bundle.mustMentionValues()).containsExactly("PROJ-1");
        }

        @Test
        void tier1ContainsTicketsClassesMigrationsLiteralsConstants() {
            var facts = List.of(
                    new EvidenceFact("t0", FactType.TICKET, "PROJ-1", "jira", true),
                    new EvidenceFact("c0", FactType.CLASS_NAME, "FooService", "files", true),
                    new EvidenceFact("mig0", FactType.MIGRATION, "V1__init", "files", true),
                    new EvidenceFact("lit0", FactType.ANNOTATION_LITERAL, "gba-persons", "diff", true),
                    new EvidenceFact("con0", FactType.CONSTANT_FIELD, "HTTP_TIMEOUT_MS", "diff", true),
                    new EvidenceFact("d0", FactType.DOMAIN_TERM, "BSN", "dictionary", true),
                    new EvidenceFact("m0", FactType.METHOD, "doWork", "diff", false));
            var bundle = new EvidenceBundle(facts);

            assertThat(bundle.tier1Values())
                    .containsExactlyInAnyOrder("PROJ-1", "FooService", "V1__init", "gba-persons", "HTTP_TIMEOUT_MS");
            assertThat(bundle.tier2Values()).containsExactly("BSN");
        }

        @Test
        void tier2ContainsDomainTermsOnly() {
            var facts = List.of(
                    new EvidenceFact("d0", FactType.DOMAIN_TERM, "GBA-V", "dictionary", true),
                    new EvidenceFact("d1", FactType.DOMAIN_TERM, "CRUD", "llm", true));
            var bundle = new EvidenceBundle(facts);

            assertThat(bundle.tier1()).isEmpty();
            assertThat(bundle.tier2Values()).containsExactlyInAnyOrder("GBA-V", "CRUD");
        }
    }

    // --- Helpers ---

    private static ChangeData changes(List<CommitInfo> commits, String diff, List<String> files) {
        return ChangeData.from(commits, diff, files);
    }

    private static JiraData emptyJira() {
        return jira(List.of());
    }

    private static JiraData jira(List<String> tickets) {
        return new JiraData(
                tickets,
                "",
                "",
                new JiraData.Impact(Map.of()),
                JiraData.TestEvidence.none(),
                JiraData.Deployment.defaults(),
                true,
                "1.0.0");
    }
}
