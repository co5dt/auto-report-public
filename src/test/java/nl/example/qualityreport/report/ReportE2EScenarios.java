package nl.example.qualityreport.report;

import java.util.List;
import java.util.Map;
import nl.example.qualityreport.report.ReportE2EScenario.*;

/**
 * Central catalog of 24 end-to-end report scenarios.
 * 6 small, 8 medium, 6 large, 4 multi-repo — varying across commit count,
 * file count, change types, risk patterns, and Jira specificity.
 */
final class ReportE2EScenarios {

    private ReportE2EScenarios() {}

    static List<ReportE2EScenario> all() {
        return List.of(
                small1(),
                small2(),
                small3(),
                small4(),
                small5(),
                small6(),
                medium1(),
                medium2(),
                medium3(),
                medium4(),
                medium5(),
                medium6(),
                medium7(),
                medium8(),
                large1(),
                large2(),
                large3(),
                large4(),
                large5(),
                large6(),
                multiRepoSmall(),
                multiRepoMedium(),
                multiRepoLarge(),
                multiRepoXLarge());
    }

    // ────────────────────────────────────────────────────────────
    //  SMALL scenarios (1-2 commits, 2-3 files)
    // ────────────────────────────────────────────────────────────

    private static ReportE2EScenario small1() {
        return new ReportE2EScenario(
                "S01-config-update",
                "Simple configuration property change",
                Difficulty.SMALL,
                new RepoRecipe(
                        "config-service",
                        List.of(
                                commit(
                                        "Update cache TTL to 300s",
                                        "Jan Bakker",
                                        "jan@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/application.properties",
                                                "spring.cache.ttl=300\nspring.datasource.url=jdbc:postgresql://db:5432/gaas\n"))),
                        roster("Jan Bakker", "jan@example.nl", "BE", "Team Gamma")),
                jira(
                        List.of("PROJ-90001"),
                        "Increase cache TTL from 60s to 300s to reduce DB load",
                        "- Cache expires after 300 seconds\n- No stale data after TTL",
                        yesNoOverrides(false, false, false, false, false, false, false, false, false, false, false),
                        "Verified cache expiry via local testing",
                        true,
                        "15 passed, 0 failed",
                        "82%",
                        true,
                        false,
                        false,
                        false,
                        true),
                List.of("PROJ-90001", "application.properties", "cache", "TTL", "300"),
                List.of("MetricsCollector", "V99__add_monitoring", "GraphQL", "TokenRevocationService"),
                "feature/cache-ttl",
                "6.1.0",
                Difficulty.SMALL.defaultMinRecall);
    }

    private static ReportE2EScenario small2() {
        return new ReportE2EScenario(
                "S02-bugfix-null-check",
                "Null pointer bugfix in service layer",
                Difficulty.SMALL,
                new RepoRecipe(
                        "person-service",
                        List.of(
                                commit(
                                        "Fix NPE when person has no address",
                                        "Lisa de Vries",
                                        "lisa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/person/AddressResolver.java",
                                                "package nl.example.person;\n\npublic class AddressResolver {\n    public String resolve(Person person) {\n        if (person.getAddress() == null) {\n            return \"Onbekend\";\n        }\n        return person.getAddress().format();\n    }\n}\n",
                                                "src/test/java/nl/example/person/AddressResolverTest.java",
                                                "package nl.example.person;\n\nimport org.junit.jupiter.api.Test;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass AddressResolverTest {\n    @Test\n    void returnsOnbekendWhenNoAddress() {\n        var resolver = new AddressResolver();\n        assertEquals(\"Onbekend\", resolver.resolve(new Person(null)));\n    }\n}\n"))),
                        roster("Lisa de Vries", "lisa@example.nl", "BE", "Team Gamma")),
                jira(
                        List.of("PROJ-90002"),
                        "Fix NullPointerException when person record has no address",
                        "- No exception when address is null\n- Returns 'Onbekend' as fallback",
                        yesNoOverrides(false, false, false, false, false, false, false, false, false, false, false),
                        "Tested with person without address",
                        true,
                        "28 passed, 0 failed",
                        "74%",
                        true,
                        false,
                        false,
                        false,
                        true),
                List.of("PROJ-90002", "AddressResolver", "NullPointerException", "Onbekend"),
                List.of("PaymentGateway", "V88__refactor_billing", "WebSocketHandler", "KafkaConsumer"),
                "fix/npe-address",
                "6.1.1",
                Difficulty.SMALL.defaultMinRecall);
    }

    private static ReportE2EScenario small3() {
        return new ReportE2EScenario(
                "S03-logging-improvement",
                "Add structured logging to payment service",
                Difficulty.SMALL,
                new RepoRecipe(
                        "payment-service",
                        List.of(
                                commit(
                                        "Add structured logging for payment events",
                                        "Pieter Smit",
                                        "pieter@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/payment/PaymentLogger.java",
                                                "package nl.example.payment;\n\nimport org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;\n\npublic class PaymentLogger {\n    private static final Logger log = LoggerFactory.getLogger(PaymentLogger.class);\n\n    public void logPaymentInitiated(String txId, double amount) {\n        log.info(\"Payment initiated: txId={}, amount={}\", txId, amount);\n    }\n\n    public void logPaymentCompleted(String txId) {\n        log.info(\"Payment completed: txId={}\", txId);\n    }\n}\n"))),
                        roster("Pieter Smit", "pieter@example.nl", "BE", "Team Delta")),
                jira(
                        List.of("PROJ-90003"),
                        "Add structured logging for payment transaction lifecycle",
                        "- Log payment initiated with txId and amount\n- Log payment completed with txId",
                        yesNoOverrides(false, false, false, false, false, false, false, false, false, false, false),
                        "Checked log output in local environment",
                        false,
                        "",
                        "",
                        true,
                        false,
                        false,
                        false,
                        true),
                List.of("PROJ-90003", "PaymentLogger", "payment", "logging"),
                List.of("ElasticSearchClient", "V77__add_search_index", "gRPC", "Team AlphaExporter"),
                "feature/payment-logging",
                "6.2.0",
                Difficulty.SMALL.defaultMinRecall);
    }

    private static ReportE2EScenario small4() {
        return new ReportE2EScenario(
                "S04-dto-rename",
                "Rename DTO for API consistency",
                Difficulty.SMALL,
                new RepoRecipe(
                        "api-gateway",
                        List.of(
                                commit(
                                        "Rename PersonResponseDto to PersonResponse",
                                        "Eva Mol",
                                        "eva@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/api/PersonResponse.java",
                                                "package nl.example.api;\n\npublic record PersonResponse(String bsn, String name, String gemeente) {}\n",
                                                "src/main/java/nl/example/api/PersonController.java",
                                                "package nl.example.api;\n\nimport org.springframework.web.bind.annotation.*;\n\n@RestController\npublic class PersonController {\n    @GetMapping(\"/api/person/{bsn}\")\n    public PersonResponse getPerson(@PathVariable String bsn) {\n        return new PersonResponse(bsn, \"Test\", \"Amsterdam\");\n    }\n}\n"))),
                        roster("Eva Mol", "eva@example.nl", "BE", "Team Epsilon")),
                jira(
                        List.of("PROJ-90004"),
                        "Rename PersonResponseDto to PersonResponse for API naming consistency",
                        "- All API responses use short naming convention\n- No Dto suffix in public API types",
                        yesNoOverrides(false, false, false, false, false, false, false, false, false, false, false),
                        "Verified API contract via Swagger",
                        true,
                        "12 passed, 0 failed",
                        "90%",
                        true,
                        false,
                        false,
                        false,
                        true),
                List.of("PROJ-90004", "PersonResponse", "PersonController"),
                List.of("CacheInvalidator", "V66__add_cache_layer", "RabbitMqPublisher", "CircuitBreaker"),
                "feature/dto-rename",
                "6.2.0",
                Difficulty.SMALL.defaultMinRecall);
    }

    private static ReportE2EScenario small5() {
        return new ReportE2EScenario(
                "S05-test-coverage",
                "Add missing unit tests for validator",
                Difficulty.SMALL,
                new RepoRecipe(
                        "validator-module",
                        List.of(
                                commit(
                                        "Add unit tests for BSN validator edge cases",
                                        "Daan Hendriks",
                                        "daan@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/validate/BsnValidatorTest.java",
                                                "package nl.example.validate;\n\nimport org.junit.jupiter.api.Test;\nimport org.junit.jupiter.params.ParameterizedTest;\nimport org.junit.jupiter.params.provider.ValueSource;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass BsnValidatorTest {\n    private final BsnValidator validator = new BsnValidator();\n\n    @Test\n    void validBsnPasses() {\n        assertTrue(validator.isValid(\"123456789\"));\n    }\n\n    @ParameterizedTest\n    @ValueSource(strings = {\"12345\", \"\", \"abcdefghi\"})\n    void invalidBsnFails(String bsn) {\n        assertFalse(validator.isValid(bsn));\n    }\n}\n"))),
                        roster("Daan Hendriks", "daan@example.nl", "TE", "Team Gamma")),
                jira(
                        List.of("PROJ-90005"),
                        "Improve test coverage for BsnValidator with edge cases",
                        "- Test valid BSN\n- Test short, empty, and non-numeric BSN values",
                        yesNoOverrides(false, false, false, false, false, false, false, false, false, false, false),
                        "Tests run in CI pipeline",
                        true,
                        "45 passed, 0 failed",
                        "95%",
                        true,
                        false,
                        false,
                        false,
                        true),
                List.of("PROJ-90005", "BsnValidator", "BsnValidatorTest"),
                List.of("NotificationDispatcher", "V55__notification_queue", "OAuth2Handler", "BatchScheduler"),
                "feature/bsn-test-coverage",
                "6.2.0",
                Difficulty.SMALL.defaultMinRecall);
    }

    private static ReportE2EScenario small6() {
        return new ReportE2EScenario(
                "S06-readme-update",
                "Documentation update for new API endpoint",
                Difficulty.SMALL,
                new RepoRecipe(
                        "docs-module",
                        List.of(commit(
                                "Document new /api/gemeente endpoint",
                                "Sophie Jansen",
                                "sophie@example.nl",
                                "BE",
                                Map.of(
                                        "docs/api-reference.md",
                                        "# API Reference\n\n## GET /api/gemeente/{code}\n\nReturns municipality data by code.\n\n### Parameters\n- code: 4-digit municipality code\n\n### Response\n```json\n{\"code\": \"0363\", \"name\": \"Amsterdam\"}\n```\n",
                                        "docs/changelog.md",
                                        "# Changelog\n\n## 6.3.0\n- Added /api/gemeente endpoint documentation\n"))),
                        roster("Sophie Jansen", "sophie@example.nl", "BE", "Team Epsilon")),
                jira(
                        List.of("PROJ-90006"),
                        "Add documentation for the new gemeente API endpoint",
                        "- API reference includes /api/gemeente\n- Changelog updated",
                        yesNoOverrides(false, false, false, false, false, false, false, false, false, false, false),
                        "Reviewed documentation locally",
                        false,
                        "",
                        "",
                        true,
                        false,
                        false,
                        false,
                        true),
                List.of("PROJ-90006", "gemeente", "api-reference", "changelog"),
                List.of("AuditTrailService", "V44__audit_schema", "LdapAuthenticator", "MetricsDashboard"),
                "feature/gemeente-docs",
                "6.3.0",
                Difficulty.SMALL.defaultMinRecall);
    }

    // ────────────────────────────────────────────────────────────
    //  MEDIUM scenarios (2-5 commits, 3-8 files)
    // ────────────────────────────────────────────────────────────

    private static ReportE2EScenario medium1() {
        return new ReportE2EScenario(
                "M01-db-migration-with-service",
                "Database migration with service layer changes",
                Difficulty.MEDIUM,
                new RepoRecipe(
                        "gba-service",
                        List.of(
                                commit(
                                        "Add nationality column to person table",
                                        "Tom van Dijk",
                                        "tom@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/db/migration/V42__add_nationality.sql",
                                                "ALTER TABLE person ADD COLUMN nationality VARCHAR(100) DEFAULT 'Nederlandse';\nCREATE INDEX idx_person_nationality ON person(nationality);\n")),
                                commit(
                                        "Update PersonEntity with nationality field",
                                        "Tom van Dijk",
                                        "tom@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/gba/entity/PersonEntity.java",
                                                "package nl.example.gba.entity;\n\nimport jakarta.persistence.*;\n\n@Entity\n@Table(name = \"person\")\npublic class PersonEntity {\n    @Id\n    private Long id;\n    private String bsn;\n    private String name;\n    private String nationality;\n\n    public String getNationality() { return nationality; }\n    public void setNationality(String nationality) { this.nationality = nationality; }\n}\n")),
                                commit(
                                        "Expose nationality in PersonService",
                                        "Tom van Dijk",
                                        "tom@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/gba/service/PersonService.java",
                                                "package nl.example.gba.service;\n\nimport nl.example.gba.entity.PersonEntity;\nimport nl.example.gba.repository.PersonRepository;\nimport org.springframework.stereotype.Service;\n\n@Service\npublic class PersonService {\n    private final PersonRepository repo;\n    public PersonService(PersonRepository repo) { this.repo = repo; }\n\n    public String getNationality(String bsn) {\n        return repo.findByBsn(bsn)\n                .map(PersonEntity::getNationality)\n                .orElse(\"Onbekend\");\n    }\n}\n")),
                                commit(
                                        "Add nationality tests",
                                        "Karin Willems",
                                        "karin@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/gba/service/PersonServiceTest.java",
                                                "package nl.example.gba.service;\n\nimport org.junit.jupiter.api.Test;\nimport static org.junit.jupiter.api.Assertions.*;\nimport static org.mockito.Mockito.*;\n\nclass PersonServiceTest {\n    @Test\n    void returnsNationalityWhenPresent() {\n        // test implementation\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Tom van Dijk", "tom@example.nl", "BE", "Team Alpha"),
                                new RosterEntry("Karin Willems", "karin@example.nl", "TE", "Team Alpha"))),
                jira(
                        List.of("PROJ-90010"),
                        "Add nationality field to person records for GBA-V compliance",
                        "- Person table has nationality column\n- Default value is 'Nederlandse'\n- Service returns nationality or 'Onbekend'",
                        yesNoOverrides(false, false, false, false, true, true, false, false, false, false, false),
                        "Tested with and without nationality data",
                        true,
                        "35 passed, 0 failed",
                        "71%",
                        true,
                        false,
                        false,
                        false,
                        true),
                List.of("PROJ-90010", "V42__add_nationality", "nationality", "PersonEntity", "PersonService"),
                List.of("PaymentProcessor", "WebSocketBridge", "V33__create_payment_log", "ReactComponent"),
                "feature/nationality-field",
                "6.2.0",
                Difficulty.MEDIUM.defaultMinRecall);
    }

    private static ReportE2EScenario medium2() {
        return new ReportE2EScenario(
                "M02-rest-endpoint-crud",
                "New REST endpoint with full CRUD operations",
                Difficulty.MEDIUM,
                new RepoRecipe(
                        "document-service",
                        List.of(
                                commit(
                                        "Add Document entity and repository",
                                        "Ahmed El Amrani",
                                        "ahmed@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/doc/DocumentEntity.java",
                                                "package nl.example.doc;\n\nimport jakarta.persistence.*;\nimport java.time.LocalDateTime;\n\n@Entity\npublic class DocumentEntity {\n    @Id @GeneratedValue\n    private Long id;\n    private String bsn;\n    private String type;\n    private String status;\n    private LocalDateTime createdAt;\n}\n",
                                                "src/main/java/nl/example/doc/DocumentRepository.java",
                                                "package nl.example.doc;\n\nimport org.springframework.data.jpa.repository.JpaRepository;\nimport java.util.List;\n\npublic interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {\n    List<DocumentEntity> findByBsn(String bsn);\n}\n")),
                                commit(
                                        "Add DocumentController with CRUD endpoints",
                                        "Ahmed El Amrani",
                                        "ahmed@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/doc/DocumentController.java",
                                                "package nl.example.doc;\n\nimport org.springframework.web.bind.annotation.*;\nimport java.util.List;\n\n@RestController\n@RequestMapping(\"/api/documents\")\npublic class DocumentController {\n    private final DocumentRepository repo;\n    public DocumentController(DocumentRepository repo) { this.repo = repo; }\n\n    @GetMapping(\"/{bsn}\")\n    public List<DocumentEntity> getByBsn(@PathVariable String bsn) {\n        return repo.findByBsn(bsn);\n    }\n\n    @PostMapping\n    public DocumentEntity create(@RequestBody DocumentEntity doc) {\n        return repo.save(doc);\n    }\n\n    @DeleteMapping(\"/{id}\")\n    public void delete(@PathVariable Long id) {\n        repo.deleteById(id);\n    }\n}\n")),
                                commit(
                                        "Add integration tests for document API",
                                        "Fatima Bouras",
                                        "fatima@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/doc/DocumentControllerIT.java",
                                                "package nl.example.doc;\n\nimport org.junit.jupiter.api.Test;\nimport org.springframework.boot.test.context.SpringBootTest;\n\n@SpringBootTest\nclass DocumentControllerIT {\n    @Test\n    void createAndRetrieveDocument() {\n        // integration test\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Ahmed El Amrani", "ahmed@example.nl", "BE", "Team Delta"),
                                new RosterEntry("Fatima Bouras", "fatima@example.nl", "TE", "Team Delta"))),
                jira(
                        List.of("PROJ-90011"),
                        "Implement CRUD API for document management",
                        "- GET /api/documents/{bsn} returns documents\n- POST /api/documents creates a document\n- DELETE /api/documents/{id} removes a document",
                        yesNoOverrides(false, false, false, false, false, false, false, false, false, false, false),
                        "Tested all endpoints via Postman",
                        true,
                        "22 passed, 0 failed",
                        "68%",
                        true,
                        false,
                        false,
                        false,
                        true),
                List.of("PROJ-90011", "DocumentController", "DocumentEntity", "DocumentRepository"),
                List.of("ElasticIndexer", "V22__fulltext_search", "KafkaTopic", "Team AlphaAlert"),
                "feature/document-crud",
                "6.2.0",
                Difficulty.MEDIUM.defaultMinRecall);
    }

    private static ReportE2EScenario medium3() {
        return new ReportE2EScenario(
                "M03-feature-toggle",
                "Feature toggle for new search functionality",
                Difficulty.MEDIUM,
                new RepoRecipe(
                        "search-module",
                        List.of(
                                commit(
                                        "Add feature toggle for extended search",
                                        "Rosa Kok",
                                        "rosa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/search/FeatureFlags.java",
                                                "package nl.example.search;\n\nimport org.springframework.beans.factory.annotation.Value;\nimport org.springframework.stereotype.Component;\n\n@Component\npublic class FeatureFlags {\n    @Value(\"${feature.extended-search:false}\")\n    private boolean extendedSearchEnabled;\n\n    public boolean isExtendedSearchEnabled() {\n        return extendedSearchEnabled;\n    }\n}\n",
                                                "src/main/resources/application.properties",
                                                "feature.extended-search=false\nsearch.max-results=100\n")),
                                commit(
                                        "Implement extended search with date range filter",
                                        "Rosa Kok",
                                        "rosa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/search/SearchService.java",
                                                "package nl.example.search;\n\nimport org.springframework.stereotype.Service;\nimport java.time.LocalDate;\nimport java.util.List;\n\n@Service\npublic class SearchService {\n    private final FeatureFlags flags;\n    public SearchService(FeatureFlags flags) { this.flags = flags; }\n\n    public List<String> search(String query, LocalDate from, LocalDate to) {\n        if (!flags.isExtendedSearchEnabled()) {\n            return basicSearch(query);\n        }\n        return extendedSearch(query, from, to);\n    }\n\n    private List<String> basicSearch(String query) { return List.of(); }\n    private List<String> extendedSearch(String q, LocalDate f, LocalDate t) { return List.of(); }\n}\n")),
                                commit(
                                        "Add search service tests",
                                        "Daan Hendriks",
                                        "daan@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/search/SearchServiceTest.java",
                                                "package nl.example.search;\n\nimport org.junit.jupiter.api.Test;\nimport static org.mockito.Mockito.*;\n\nclass SearchServiceTest {\n    @Test\n    void usesBasicSearchWhenToggleOff() {\n        var flags = mock(FeatureFlags.class);\n        when(flags.isExtendedSearchEnabled()).thenReturn(false);\n        var service = new SearchService(flags);\n        service.search(\"test\", null, null);\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Rosa Kok", "rosa@example.nl", "BE", "Team Epsilon"),
                                new RosterEntry("Daan Hendriks", "daan@example.nl", "TE", "Team Epsilon"))),
                jira(
                        List.of("PROJ-90012"),
                        "Add feature toggle for extended search with date range filtering",
                        "- Feature toggle controls search behavior\n- Extended search filters by date range\n- Default is basic search (toggle off)",
                        yesNoOverrides(false, false, false, false, false, false, false, false, false, false, false),
                        "Tested toggle on and off scenarios",
                        true,
                        "18 passed, 0 failed",
                        "76%",
                        true,
                        true,
                        false,
                        false,
                        true),
                List.of("PROJ-90012", "FeatureFlags", "SearchService", "extended-search"),
                List.of("V11__add_audit_column", "GraphQLResolver", "CacheWarmer"),
                "feature/extended-search-toggle",
                "6.3.0",
                Difficulty.MEDIUM.defaultMinRecall);
    }

    private static ReportE2EScenario medium4() {
        return new ReportE2EScenario(
                "M04-security-patch",
                "Security vulnerability fix in authentication module",
                Difficulty.MEDIUM,
                new RepoRecipe(
                        "auth-service",
                        List.of(
                                commit(
                                        "Fix SQL injection vulnerability in login query",
                                        "Bas Mulder",
                                        "bas@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/auth/LoginService.java",
                                                "package nl.example.auth;\n\nimport org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;\nimport org.springframework.stereotype.Service;\nimport java.util.Map;\n\n@Service\npublic class LoginService {\n    private final NamedParameterJdbcTemplate jdbc;\n    public LoginService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }\n\n    public boolean authenticate(String username, String password) {\n        String sql = \"SELECT COUNT(*) FROM users WHERE username = :user AND password_hash = :hash\";\n        int count = jdbc.queryForObject(sql,\n                Map.of(\"user\", username, \"hash\", hashPassword(password)), Integer.class);\n        return count > 0;\n    }\n\n    private String hashPassword(String pw) { return pw; }\n}\n")),
                                commit(
                                        "Add rate limiting to login endpoint",
                                        "Bas Mulder",
                                        "bas@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/auth/RateLimiter.java",
                                                "package nl.example.auth;\n\nimport java.util.concurrent.ConcurrentHashMap;\nimport java.util.concurrent.atomic.AtomicInteger;\n\npublic class RateLimiter {\n    private final ConcurrentHashMap<String, AtomicInteger> attempts = new ConcurrentHashMap<>();\n    private static final int MAX_ATTEMPTS = 5;\n\n    public boolean isAllowed(String username) {\n        AtomicInteger count = attempts.computeIfAbsent(username, k -> new AtomicInteger(0));\n        return count.incrementAndGet() <= MAX_ATTEMPTS;\n    }\n\n    public void reset(String username) {\n        attempts.remove(username);\n    }\n}\n")),
                                commit(
                                        "Add security tests for SQL injection and rate limiting",
                                        "Karin Willems",
                                        "karin@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/auth/LoginServiceTest.java",
                                                "package nl.example.auth;\n\nimport org.junit.jupiter.api.Test;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass LoginServiceTest {\n    @Test\n    void rejectsSqlInjection() {\n        // test parameterized query is safe\n    }\n}\n",
                                                "src/test/java/nl/example/auth/RateLimiterTest.java",
                                                "package nl.example.auth;\n\nimport org.junit.jupiter.api.Test;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass RateLimiterTest {\n    @Test\n    void blocksAfterMaxAttempts() {\n        var limiter = new RateLimiter();\n        for (int i = 0; i < 5; i++) assertTrue(limiter.isAllowed(\"user\"));\n        assertFalse(limiter.isAllowed(\"user\"));\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Bas Mulder", "bas@example.nl", "BE", "Team Alpha"),
                                new RosterEntry("Karin Willems", "karin@example.nl", "TE", "Team Alpha"))),
                jira(
                        List.of("PROJ-90013"),
                        "Fix SQL injection vulnerability and add rate limiting to login",
                        "- Login uses parameterized queries\n- Rate limiter blocks after 5 failed attempts\n- Security tests cover injection and rate limiting",
                        yesNoOverrides(true, false, true, false, false, false, false, false, false, false, false),
                        "Penetration test passed for injection vectors",
                        true,
                        "31 passed, 0 failed",
                        "84%",
                        true,
                        false,
                        false,
                        true,
                        true),
                List.of("PROJ-90013", "LoginService", "RateLimiter", "SQL injection", "parameterized"),
                List.of("NotificationQueue", "V10__add_notification", "ElasticAggregator", "gRPCStub"),
                "fix/security-auth",
                "6.1.2",
                Difficulty.MEDIUM.defaultMinRecall);
    }

    private static ReportE2EScenario medium5() {
        return new ReportE2EScenario(
                "M05-scheduler-job",
                "Scheduled batch job for data cleanup",
                Difficulty.MEDIUM,
                new RepoRecipe(
                        "batch-service",
                        List.of(
                                commit(
                                        "Add scheduled cleanup job for expired sessions",
                                        "Niels Bakker",
                                        "niels@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/batch/SessionCleanupJob.java",
                                                "package nl.example.batch;\n\nimport org.springframework.scheduling.annotation.Scheduled;\nimport org.springframework.stereotype.Component;\nimport java.time.LocalDateTime;\n\n@Component\npublic class SessionCleanupJob {\n    private final SessionRepository repo;\n    public SessionCleanupJob(SessionRepository repo) { this.repo = repo; }\n\n    @Scheduled(cron = \"0 0 2 * * *\")\n    public void cleanExpiredSessions() {\n        int deleted = repo.deleteExpiredBefore(LocalDateTime.now().minusDays(30));\n        System.out.printf(\"Cleaned %d expired sessions%n\", deleted);\n    }\n}\n",
                                                "src/main/java/nl/example/batch/SessionRepository.java",
                                                "package nl.example.batch;\n\nimport java.time.LocalDateTime;\n\npublic interface SessionRepository {\n    int deleteExpiredBefore(LocalDateTime cutoff);\n}\n")),
                                commit(
                                        "Add configuration for cleanup retention period",
                                        "Niels Bakker",
                                        "niels@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/application-batch.properties",
                                                "cleanup.retention-days=30\ncleanup.batch-size=1000\n"))),
                        List.of(new RosterEntry("Niels Bakker", "niels@example.nl", "BE", "Team Gamma"))),
                jira(
                        List.of("PROJ-90014"),
                        "Implement nightly scheduled job to clean expired sessions older than 30 days",
                        "- Job runs at 02:00 daily\n- Deletes sessions older than 30 days\n- Retention configurable via properties",
                        yesNoOverrides(false, false, true, true, true, false, false, false, false, false, false),
                        "Verified cleanup in test environment",
                        true,
                        "8 passed, 0 failed",
                        "60%",
                        true,
                        false,
                        false,
                        true,
                        true),
                List.of("PROJ-90014", "SessionCleanupJob", "cleanup", "expired sessions", "30 days"),
                List.of("WebSocketManager", "V09__websocket_sessions", "ReactHook"),
                "feature/session-cleanup",
                "6.3.0",
                Difficulty.MEDIUM.defaultMinRecall);
    }

    private static ReportE2EScenario medium6() {
        return new ReportE2EScenario(
                "M06-event-driven",
                "Event-driven notification on person status change",
                Difficulty.MEDIUM,
                new RepoRecipe(
                        "notification-service",
                        List.of(
                                commit(
                                        "Add PersonStatusChangedEvent and publisher",
                                        "Emma de Groot",
                                        "emma@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/event/PersonStatusChangedEvent.java",
                                                "package nl.example.event;\n\npublic record PersonStatusChangedEvent(String bsn, String oldStatus, String newStatus, String changedBy) {}\n",
                                                "src/main/java/nl/example/event/EventPublisher.java",
                                                "package nl.example.event;\n\nimport org.springframework.context.ApplicationEventPublisher;\nimport org.springframework.stereotype.Component;\n\n@Component\npublic class EventPublisher {\n    private final ApplicationEventPublisher publisher;\n    public EventPublisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }\n\n    public void publish(PersonStatusChangedEvent event) {\n        publisher.publishEvent(event);\n    }\n}\n")),
                                commit(
                                        "Add notification listener for status changes",
                                        "Emma de Groot",
                                        "emma@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/event/NotificationListener.java",
                                                "package nl.example.event;\n\nimport org.springframework.context.event.EventListener;\nimport org.springframework.stereotype.Component;\n\n@Component\npublic class NotificationListener {\n    @EventListener\n    public void onStatusChanged(PersonStatusChangedEvent event) {\n        String msg = String.format(\"Person %s status changed: %s -> %s\",\n                event.bsn(), event.oldStatus(), event.newStatus());\n        System.out.println(msg);\n    }\n}\n")),
                                commit(
                                        "Add event handling tests",
                                        "Fatima Bouras",
                                        "fatima@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/event/NotificationListenerTest.java",
                                                "package nl.example.event;\n\nimport org.junit.jupiter.api.Test;\n\nclass NotificationListenerTest {\n    @Test\n    void handlesStatusChange() {\n        var listener = new NotificationListener();\n        listener.onStatusChanged(new PersonStatusChangedEvent(\"123\", \"ACTIVE\", \"DECEASED\", \"system\"));\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Emma de Groot", "emma@example.nl", "BE", "Team Delta"),
                                new RosterEntry("Fatima Bouras", "fatima@example.nl", "TE", "Team Delta"))),
                jira(
                        List.of("PROJ-90015"),
                        "Implement event-driven notification when person status changes in GBA-V",
                        "- Event published when status changes\n- Listener logs notification\n- Status includes old and new value",
                        yesNoOverrides(false, true, false, false, false, false, false, false, false, false, false),
                        "Tested status change scenario end-to-end",
                        true,
                        "14 passed, 0 failed",
                        "72%",
                        true,
                        false,
                        false,
                        false,
                        true),
                List.of("PROJ-90015", "PersonStatusChangedEvent", "EventPublisher", "NotificationListener"),
                List.of("PaymentRefundService", "V08__refund_table", "StripeWebhook"),
                "feature/status-notifications",
                "6.3.0",
                Difficulty.MEDIUM.defaultMinRecall);
    }

    private static ReportE2EScenario medium7() {
        return new ReportE2EScenario(
                "M07-cache-layer",
                "Add Redis cache layer for GBA-V lookups",
                Difficulty.MEDIUM,
                new RepoRecipe(
                        "gba-cache",
                        List.of(
                                commit(
                                        "Add Redis cache configuration",
                                        "Marco Visser",
                                        "marco@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/cache/CacheConfig.java",
                                                "package nl.example.cache;\n\nimport org.springframework.cache.annotation.EnableCaching;\nimport org.springframework.context.annotation.Bean;\nimport org.springframework.context.annotation.Configuration;\nimport org.springframework.data.redis.cache.RedisCacheConfiguration;\nimport java.time.Duration;\n\n@Configuration\n@EnableCaching\npublic class CacheConfig {\n    @Bean\n    public RedisCacheConfiguration cacheConfiguration() {\n        return RedisCacheConfiguration.defaultCacheConfig()\n                .entryTtl(Duration.ofMinutes(15))\n                .disableCachingNullValues();\n    }\n}\n",
                                                "src/main/resources/application.properties",
                                                "spring.cache.type=redis\nspring.redis.host=localhost\nspring.redis.port=6379\ncache.gba.ttl-minutes=15\n")),
                                commit(
                                        "Add @Cacheable to GBA lookup service",
                                        "Marco Visser",
                                        "marco@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/cache/GbaLookupService.java",
                                                "package nl.example.cache;\n\nimport org.springframework.cache.annotation.Cacheable;\nimport org.springframework.stereotype.Service;\n\n@Service\npublic class GbaLookupService {\n    @Cacheable(value = \"gba-persons\", key = \"#bsn\")\n    public String lookupPerson(String bsn) {\n        return performGbaVQuery(bsn);\n    }\n\n    private String performGbaVQuery(String bsn) {\n        return \"person-data-\" + bsn;\n    }\n}\n")),
                                commit(
                                        "Add cache eviction and tests",
                                        "Marco Visser",
                                        "marco@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/cache/CacheEvictionService.java",
                                                "package nl.example.cache;\n\nimport org.springframework.cache.annotation.CacheEvict;\nimport org.springframework.stereotype.Service;\n\n@Service\npublic class CacheEvictionService {\n    @CacheEvict(value = \"gba-persons\", key = \"#bsn\")\n    public void evictPerson(String bsn) {}\n\n    @CacheEvict(value = \"gba-persons\", allEntries = true)\n    public void evictAll() {}\n}\n",
                                                "src/test/java/nl/example/cache/GbaLookupServiceTest.java",
                                                "package nl.example.cache;\n\nimport org.junit.jupiter.api.Test;\n\nclass GbaLookupServiceTest {\n    @Test\n    void cachesLookupResult() {\n        // verify caching behavior\n    }\n}\n"))),
                        List.of(new RosterEntry("Marco Visser", "marco@example.nl", "BE", "Team Alpha"))),
                jira(
                        List.of("PROJ-90016"),
                        "Add Redis cache layer for GBA-V person lookups to reduce response times",
                        "- 15-minute TTL on cached lookups\n- Cache eviction available per BSN or all\n- Null values are not cached",
                        yesNoOverrides(false, false, true, true, false, false, false, false, false, false, false),
                        "Load tested with and without cache",
                        true,
                        "19 passed, 0 failed",
                        "65%",
                        true,
                        false,
                        false,
                        true,
                        true),
                List.of(
                        "PROJ-90016",
                        "CacheConfig",
                        "GbaLookupService",
                        "CacheEvictionService",
                        "Redis",
                        "gba-persons"),
                List.of("ElectionVoteCounter", "V07__vote_tally", "BlockchainVerifier"),
                "feature/gba-cache",
                "6.3.0",
                Difficulty.MEDIUM.defaultMinRecall);
    }

    private static ReportE2EScenario medium8() {
        return new ReportE2EScenario(
                "M08-api-versioning",
                "API versioning for backward compatibility",
                Difficulty.MEDIUM,
                new RepoRecipe(
                        "api-versioning",
                        List.of(
                                commit(
                                        "Add API v2 endpoint with new response format",
                                        "Yuki Tanaka",
                                        "yuki@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/api/v2/PersonResponseV2.java",
                                                "package nl.example.api.v2;\n\nimport java.time.LocalDate;\n\npublic record PersonResponseV2(String bsn, String fullName, LocalDate birthDate, String municipality) {}\n",
                                                "src/main/java/nl/example/api/v2/PersonControllerV2.java",
                                                "package nl.example.api.v2;\n\nimport org.springframework.web.bind.annotation.*;\n\n@RestController\n@RequestMapping(\"/api/v2/person\")\npublic class PersonControllerV2 {\n    @GetMapping(\"/{bsn}\")\n    public PersonResponseV2 getPerson(@PathVariable String bsn) {\n        return new PersonResponseV2(bsn, \"Jan de Vries\", java.time.LocalDate.of(1985, 3, 15), \"Amsterdam\");\n    }\n}\n")),
                                commit(
                                        "Add deprecation notice to v1 endpoint",
                                        "Yuki Tanaka",
                                        "yuki@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/api/v1/PersonControllerV1.java",
                                                "package nl.example.api.v1;\n\nimport org.springframework.web.bind.annotation.*;\n\n/**\n * @deprecated Use /api/v2/person instead. Will be removed in 7.0.\n */\n@Deprecated(since = \"6.4.0\", forRemoval = true)\n@RestController\n@RequestMapping(\"/api/v1/person\")\npublic class PersonControllerV1 {\n    @GetMapping(\"/{bsn}\")\n    public String getPerson(@PathVariable String bsn) {\n        return \"{\\\"bsn\\\": \\\"\" + bsn + \"\\\"}\";\n    }\n}\n")),
                                commit(
                                        "Add API versioning tests",
                                        "Daan Hendriks",
                                        "daan@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/api/v2/PersonControllerV2Test.java",
                                                "package nl.example.api.v2;\n\nimport org.junit.jupiter.api.Test;\n\nclass PersonControllerV2Test {\n    @Test\n    void returnsV2Format() {\n        // verify new response format\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Yuki Tanaka", "yuki@example.nl", "BE", "Team Epsilon"),
                                new RosterEntry("Daan Hendriks", "daan@example.nl", "TE", "Team Epsilon"))),
                jira(
                        List.of("PROJ-90017", "PROJ-90018"),
                        "Implement API v2 with new response format and deprecate v1",
                        "- /api/v2/person returns new format with fullName and birthDate\n- /api/v1/person marked deprecated\n- Both versions work simultaneously",
                        yesNoOverrides(false, true, false, false, false, false, false, false, false, true, false),
                        "Tested both v1 and v2 endpoints",
                        true,
                        "27 passed, 0 failed",
                        "80%",
                        true,
                        false,
                        false,
                        false,
                        true),
                List.of("PROJ-90017", "PROJ-90018", "PersonControllerV2", "PersonResponseV2", "deprecated"),
                List.of("TenantMigrator", "V06__tenant_schema", "LdapSync", "KerberosAuth"),
                "feature/api-v2",
                "6.4.0",
                Difficulty.MEDIUM.defaultMinRecall);
    }

    // ────────────────────────────────────────────────────────────
    //  LARGE scenarios (5-12 commits, 8-15 files)
    // ────────────────────────────────────────────────────────────

    private static ReportE2EScenario large1() {
        return new ReportE2EScenario(
                "L01-multi-module-refactor",
                "Cross-module refactoring with migration and tests",
                Difficulty.LARGE,
                new RepoRecipe(
                        "platform-core",
                        List.of(
                                commit(
                                        "Add address validation service",
                                        "Jan Bakker",
                                        "jan@example.nl",
                                        "BE",
                                        Map.of(
                                                "modules/address/src/main/java/nl/example/address/AddressValidator.java",
                                                "package nl.example.address;\n\npublic class AddressValidator {\n    public boolean isValid(String postcode, String huisnummer) {\n        return postcode != null && postcode.matches(\"\\\\d{4}[A-Z]{2}\")\n                && huisnummer != null && !huisnummer.isBlank();\n    }\n}\n")),
                                commit(
                                        "Add BAG integration for address verification",
                                        "Jan Bakker",
                                        "jan@example.nl",
                                        "BE",
                                        Map.of(
                                                "modules/address/src/main/java/nl/example/address/BagClient.java",
                                                "package nl.example.address;\n\nimport java.net.http.HttpClient;\n\npublic class BagClient {\n    private final HttpClient client = HttpClient.newHttpClient();\n    private final String bagUrl;\n\n    public BagClient(String bagUrl) { this.bagUrl = bagUrl; }\n\n    public boolean verifyAddress(String postcode, String huisnummer) {\n        return true;\n    }\n}\n")),
                                commit(
                                        "Create address migration script",
                                        "Lisa de Vries",
                                        "lisa@example.nl",
                                        "BE",
                                        Map.of(
                                                "modules/address/src/main/resources/db/migration/V50__create_address_table.sql",
                                                "CREATE TABLE address (\n    id BIGSERIAL PRIMARY KEY,\n    person_id BIGINT REFERENCES person(id),\n    postcode VARCHAR(6) NOT NULL,\n    huisnummer VARCHAR(10) NOT NULL,\n    straat VARCHAR(200),\n    woonplaats VARCHAR(100),\n    verified BOOLEAN DEFAULT FALSE,\n    created_at TIMESTAMP DEFAULT NOW()\n);\n\nCREATE INDEX idx_address_person ON address(person_id);\nCREATE INDEX idx_address_postcode ON address(postcode);\n")),
                                commit(
                                        "Add AddressService and repository",
                                        "Jan Bakker",
                                        "jan@example.nl",
                                        "BE",
                                        Map.of(
                                                "modules/address/src/main/java/nl/example/address/AddressService.java",
                                                "package nl.example.address;\n\nimport org.springframework.stereotype.Service;\n\n@Service\npublic class AddressService {\n    private final AddressRepository repo;\n    private final BagClient bagClient;\n    private final AddressValidator validator;\n\n    public AddressService(AddressRepository repo, BagClient bagClient, AddressValidator validator) {\n        this.repo = repo;\n        this.bagClient = bagClient;\n        this.validator = validator;\n    }\n\n    public void registerAddress(long personId, String postcode, String huisnummer) {\n        if (!validator.isValid(postcode, huisnummer)) {\n            throw new IllegalArgumentException(\"Invalid address format\");\n        }\n        bagClient.verifyAddress(postcode, huisnummer);\n    }\n}\n",
                                                "modules/address/src/main/java/nl/example/address/AddressRepository.java",
                                                "package nl.example.address;\n\nimport org.springframework.data.jpa.repository.JpaRepository;\n\npublic interface AddressRepository extends JpaRepository<Object, Long> {\n}\n")),
                                commit(
                                        "Integrate address module into person API",
                                        "Lisa de Vries",
                                        "lisa@example.nl",
                                        "BE",
                                        Map.of(
                                                "modules/person-api/src/main/java/nl/example/person/PersonAddressEndpoint.java",
                                                "package nl.example.person;\n\nimport org.springframework.web.bind.annotation.*;\n\n@RestController\n@RequestMapping(\"/api/person/{bsn}/address\")\npublic class PersonAddressEndpoint {\n    @PostMapping\n    public void registerAddress(@PathVariable String bsn, @RequestBody AddressRequest req) {\n        // delegates to AddressService\n    }\n\n    record AddressRequest(String postcode, String huisnummer) {}\n}\n")),
                                commit(
                                        "Update configuration for address module",
                                        "Jan Bakker",
                                        "jan@example.nl",
                                        "BE",
                                        Map.of(
                                                "config/application-address.properties",
                                                "bag.api.url=https://bag.basisregistraties.overheid.nl/api/v1\nbag.api.timeout-ms=5000\naddress.validation.strict=true\n")),
                                commit(
                                        "Add address module tests",
                                        "Karin Willems",
                                        "karin@example.nl",
                                        "TE",
                                        Map.of(
                                                "modules/address/src/test/java/nl/example/address/AddressValidatorTest.java",
                                                "package nl.example.address;\n\nimport org.junit.jupiter.api.Test;\nimport org.junit.jupiter.params.ParameterizedTest;\nimport org.junit.jupiter.params.provider.CsvSource;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass AddressValidatorTest {\n    private final AddressValidator validator = new AddressValidator();\n\n    @ParameterizedTest\n    @CsvSource({\"1234AB,1,true\", \"1234ab,1,false\", \"12345,1,false\", \",1,false\"})\n    void validatesPostcodeFormat(String postcode, String nr, boolean expected) {\n        assertEquals(expected, validator.isValid(postcode, nr));\n    }\n}\n",
                                                "modules/address/src/test/java/nl/example/address/AddressServiceTest.java",
                                                "package nl.example.address;\n\nimport org.junit.jupiter.api.Test;\nimport static org.mockito.Mockito.*;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass AddressServiceTest {\n    @Test\n    void rejectsInvalidPostcode() {\n        var repo = mock(AddressRepository.class);\n        var bag = mock(BagClient.class);\n        var service = new AddressService(repo, bag, new AddressValidator());\n        assertThrows(IllegalArgumentException.class,\n                () -> service.registerAddress(1L, \"invalid\", \"1\"));\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Jan Bakker", "jan@example.nl", "BE", "Team Gamma"),
                                new RosterEntry("Lisa de Vries", "lisa@example.nl", "BE", "Team Gamma"),
                                new RosterEntry("Karin Willems", "karin@example.nl", "TE", "Team Gamma"))),
                jira(
                        List.of("PROJ-90020", "PROJ-90021"),
                        "Implement address module with BAG verification and person API integration",
                        "- Address table with postcode and huisnummer\n- BAG API integration for verification\n- REST endpoint under /api/person/{bsn}/address\n- Postcode format validation (4 digits + 2 uppercase letters)",
                        yesNoOverrides(false, true, true, true, true, true, false, false, false, false, false),
                        "Full integration test with mock BAG service",
                        true,
                        "52 passed, 0 failed",
                        "78%",
                        true,
                        false,
                        false,
                        true,
                        true),
                List.of(
                        "PROJ-90020",
                        "PROJ-90021",
                        "AddressValidator",
                        "BagClient",
                        "AddressService",
                        "V50__create_address_table",
                        "PersonAddressEndpoint"),
                List.of("PaymentReconciler", "V05__reconciliation_log", "StripeClient", "TwilioNotifier"),
                "feature/address-module",
                "6.4.0",
                40.0);
    }

    private static ReportE2EScenario large2() {
        return new ReportE2EScenario(
                "L02-audit-logging-system",
                "Comprehensive audit logging with database and interceptor",
                Difficulty.LARGE,
                new RepoRecipe(
                        "audit-platform",
                        List.of(
                                commit(
                                        "Create audit log table and entity",
                                        "Pieter Smit",
                                        "pieter@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/db/migration/V55__create_audit_log.sql",
                                                "CREATE TABLE audit_log (\n    id BIGSERIAL PRIMARY KEY,\n    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),\n    user_id VARCHAR(50) NOT NULL,\n    action VARCHAR(100) NOT NULL,\n    entity_type VARCHAR(100),\n    entity_id VARCHAR(100),\n    old_value TEXT,\n    new_value TEXT,\n    ip_address VARCHAR(45)\n);\n\nCREATE INDEX idx_audit_user ON audit_log(user_id);\nCREATE INDEX idx_audit_timestamp ON audit_log(timestamp);\n",
                                                "src/main/java/nl/example/audit/AuditLogEntity.java",
                                                "package nl.example.audit;\n\nimport jakarta.persistence.*;\nimport java.time.LocalDateTime;\n\n@Entity\n@Table(name = \"audit_log\")\npublic class AuditLogEntity {\n    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)\n    private Long id;\n    private LocalDateTime timestamp;\n    private String userId;\n    private String action;\n    private String entityType;\n    private String entityId;\n    @Column(columnDefinition = \"TEXT\")\n    private String oldValue;\n    @Column(columnDefinition = \"TEXT\")\n    private String newValue;\n    private String ipAddress;\n}\n")),
                                commit(
                                        "Add audit repository and service",
                                        "Pieter Smit",
                                        "pieter@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/audit/AuditRepository.java",
                                                "package nl.example.audit;\n\nimport org.springframework.data.jpa.repository.JpaRepository;\nimport java.time.LocalDateTime;\nimport java.util.List;\n\npublic interface AuditRepository extends JpaRepository<AuditLogEntity, Long> {\n    List<AuditLogEntity> findByUserIdOrderByTimestampDesc(String userId);\n    List<AuditLogEntity> findByTimestampBetween(LocalDateTime from, LocalDateTime to);\n}\n",
                                                "src/main/java/nl/example/audit/AuditService.java",
                                                "package nl.example.audit;\n\nimport org.springframework.stereotype.Service;\nimport java.time.LocalDateTime;\n\n@Service\npublic class AuditService {\n    private final AuditRepository repo;\n    public AuditService(AuditRepository repo) { this.repo = repo; }\n\n    public void logAction(String userId, String action, String entityType,\n                          String entityId, String oldVal, String newVal, String ip) {\n        var entry = new AuditLogEntity();\n        // populate and save\n        repo.save(entry);\n    }\n}\n")),
                                commit(
                                        "Add Spring AOP interceptor for automatic auditing",
                                        "Pieter Smit",
                                        "pieter@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/audit/AuditInterceptor.java",
                                                "package nl.example.audit;\n\nimport org.aspectj.lang.ProceedingJoinPoint;\nimport org.aspectj.lang.annotation.*;\nimport org.springframework.stereotype.Component;\n\n@Aspect\n@Component\npublic class AuditInterceptor {\n    private final AuditService auditService;\n    public AuditInterceptor(AuditService auditService) { this.auditService = auditService; }\n\n    @Around(\"@annotation(Audited)\")\n    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {\n        Object result = joinPoint.proceed();\n        auditService.logAction(\"system\", joinPoint.getSignature().getName(),\n                \"\", \"\", \"\", \"\", \"\");\n        return result;\n    }\n}\n",
                                                "src/main/java/nl/example/audit/Audited.java",
                                                "package nl.example.audit;\n\nimport java.lang.annotation.*;\n\n@Target(ElementType.METHOD)\n@Retention(RetentionPolicy.RUNTIME)\npublic @interface Audited {}\n")),
                                commit(
                                        "Add audit query endpoint",
                                        "Eva Mol",
                                        "eva@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/audit/AuditController.java",
                                                "package nl.example.audit;\n\nimport org.springframework.format.annotation.DateTimeFormat;\nimport org.springframework.web.bind.annotation.*;\nimport java.time.LocalDateTime;\nimport java.util.List;\n\n@RestController\n@RequestMapping(\"/api/audit\")\npublic class AuditController {\n    private final AuditRepository repo;\n    public AuditController(AuditRepository repo) { this.repo = repo; }\n\n    @GetMapping(\"/user/{userId}\")\n    public List<AuditLogEntity> byUser(@PathVariable String userId) {\n        return repo.findByUserIdOrderByTimestampDesc(userId);\n    }\n\n    @GetMapping(\"/range\")\n    public List<AuditLogEntity> byRange(\n            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,\n            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {\n        return repo.findByTimestampBetween(from, to);\n    }\n}\n")),
                                commit(
                                        "Add audit system tests",
                                        "Fatima Bouras",
                                        "fatima@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/audit/AuditServiceTest.java",
                                                "package nl.example.audit;\n\nimport org.junit.jupiter.api.Test;\nimport static org.mockito.Mockito.*;\n\nclass AuditServiceTest {\n    @Test\n    void logsAction() {\n        var repo = mock(AuditRepository.class);\n        var service = new AuditService(repo);\n        service.logAction(\"user1\", \"UPDATE\", \"Person\", \"123\", \"{}\", \"{}\", \"127.0.0.1\");\n        verify(repo).save(any());\n    }\n}\n",
                                                "src/test/java/nl/example/audit/AuditControllerTest.java",
                                                "package nl.example.audit;\n\nimport org.junit.jupiter.api.Test;\n\nclass AuditControllerTest {\n    @Test\n    void queriesByUser() {\n        // test audit query\n    }\n}\n")),
                                commit(
                                        "Add audit configuration",
                                        "Pieter Smit",
                                        "pieter@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/application-audit.properties",
                                                "audit.enabled=true\naudit.retention-days=365\naudit.async=true\naudit.include-old-values=true\n"))),
                        List.of(
                                new RosterEntry("Pieter Smit", "pieter@example.nl", "BE", "Team Delta"),
                                new RosterEntry("Eva Mol", "eva@example.nl", "BE", "Team Delta"),
                                new RosterEntry("Fatima Bouras", "fatima@example.nl", "TE", "Team Delta"))),
                jira(
                        List.of("PROJ-90022", "PROJ-90023"),
                        "Implement audit logging with AOP interceptor and query API for compliance",
                        "- All @Audited methods are automatically logged\n- Audit log stores old and new values\n- Query API supports user-based and time-range queries\n- Retention of 365 days",
                        yesNoOverrides(false, true, true, true, true, true, false, false, false, false, false),
                        "Verified audit entries in test database",
                        true,
                        "38 passed, 0 failed",
                        "73%",
                        true,
                        false,
                        false,
                        true,
                        true),
                List.of(
                        "PROJ-90022",
                        "PROJ-90023",
                        "AuditLogEntity",
                        "AuditService",
                        "AuditInterceptor",
                        "Audited",
                        "V55__create_audit_log",
                        "AuditController"),
                List.of("ElasticMigrator", "V04__search_mapping", "KafkaStream", "GrafanaDashboard"),
                "feature/audit-logging",
                "6.4.0",
                Difficulty.LARGE.defaultMinRecall);
    }

    private static ReportE2EScenario large3() {
        return new ReportE2EScenario(
                "L03-data-export-pipeline",
                "Data export pipeline with CSV and scheduling",
                Difficulty.LARGE,
                new RepoRecipe(
                        "export-service",
                        List.of(
                                commit(
                                        "Add export configuration entity",
                                        "Tom van Dijk",
                                        "tom@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/export/ExportConfig.java",
                                                "package nl.example.export;\n\nimport java.util.List;\n\npublic record ExportConfig(String name, String query, List<String> columns, String format) {\n    public enum Format { CSV, JSON, XML }\n}\n",
                                                "src/main/resources/db/migration/V60__create_export_tables.sql",
                                                "CREATE TABLE export_config (\n    id BIGSERIAL PRIMARY KEY,\n    name VARCHAR(100) UNIQUE NOT NULL,\n    query TEXT NOT NULL,\n    columns TEXT NOT NULL,\n    format VARCHAR(10) DEFAULT 'CSV'\n);\n\nCREATE TABLE export_history (\n    id BIGSERIAL PRIMARY KEY,\n    config_id BIGINT REFERENCES export_config(id),\n    started_at TIMESTAMP NOT NULL,\n    completed_at TIMESTAMP,\n    row_count INT,\n    file_path VARCHAR(500),\n    status VARCHAR(20) DEFAULT 'PENDING'\n);\n")),
                                commit(
                                        "Implement CSV writer and export engine",
                                        "Tom van Dijk",
                                        "tom@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/export/CsvWriter.java",
                                                "package nl.example.export;\n\nimport java.io.*;\nimport java.util.List;\n\npublic class CsvWriter {\n    public void write(List<String> headers, List<List<String>> rows, OutputStream out) throws IOException {\n        var writer = new BufferedWriter(new OutputStreamWriter(out));\n        writer.write(String.join(\";\", headers));\n        writer.newLine();\n        for (var row : rows) {\n            writer.write(String.join(\";\", row));\n            writer.newLine();\n        }\n        writer.flush();\n    }\n}\n",
                                                "src/main/java/nl/example/export/ExportEngine.java",
                                                "package nl.example.export;\n\nimport org.springframework.stereotype.Service;\nimport java.io.*;\nimport java.nio.file.*;\nimport java.time.LocalDateTime;\n\n@Service\npublic class ExportEngine {\n    private final CsvWriter csvWriter = new CsvWriter();\n\n    public Path execute(ExportConfig config) throws IOException {\n        Path outputDir = Paths.get(\"/tmp/exports\");\n        Files.createDirectories(outputDir);\n        Path file = outputDir.resolve(config.name() + \"-\" + System.currentTimeMillis() + \".csv\");\n        try (var fos = new FileOutputStream(file.toFile())) {\n            csvWriter.write(config.columns(), java.util.List.of(), fos);\n        }\n        return file;\n    }\n}\n")),
                                commit(
                                        "Add scheduled export runner",
                                        "Tom van Dijk",
                                        "tom@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/export/ScheduledExportRunner.java",
                                                "package nl.example.export;\n\nimport org.springframework.scheduling.annotation.Scheduled;\nimport org.springframework.stereotype.Component;\n\n@Component\npublic class ScheduledExportRunner {\n    private final ExportEngine engine;\n    public ScheduledExportRunner(ExportEngine engine) { this.engine = engine; }\n\n    @Scheduled(cron = \"0 0 4 * * *\")\n    public void runDailyExports() {\n        // fetch all active configs and run\n    }\n}\n")),
                                commit(
                                        "Add export REST API",
                                        "Ahmed El Amrani",
                                        "ahmed@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/export/ExportController.java",
                                                "package nl.example.export;\n\nimport org.springframework.web.bind.annotation.*;\nimport java.io.IOException;\nimport java.nio.file.Path;\n\n@RestController\n@RequestMapping(\"/api/export\")\npublic class ExportController {\n    private final ExportEngine engine;\n    public ExportController(ExportEngine engine) { this.engine = engine; }\n\n    @PostMapping(\"/run\")\n    public String triggerExport(@RequestBody ExportConfig config) throws IOException {\n        Path result = engine.execute(config);\n        return result.toString();\n    }\n}\n")),
                                commit(
                                        "Add export configuration properties",
                                        "Tom van Dijk",
                                        "tom@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/application-export.properties",
                                                "export.output-dir=/data/exports\nexport.max-rows=100000\nexport.schedule.enabled=true\nexport.retention-days=90\n")),
                                commit(
                                        "Add export tests",
                                        "Karin Willems",
                                        "karin@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/export/CsvWriterTest.java",
                                                "package nl.example.export;\n\nimport org.junit.jupiter.api.Test;\nimport java.io.ByteArrayOutputStream;\nimport java.util.List;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass CsvWriterTest {\n    @Test\n    void writesHeadersAndRows() throws Exception {\n        var writer = new CsvWriter();\n        var out = new ByteArrayOutputStream();\n        writer.write(List.of(\"a\", \"b\"), List.of(List.of(\"1\", \"2\")), out);\n        assertTrue(out.toString().contains(\"a;b\"));\n    }\n}\n",
                                                "src/test/java/nl/example/export/ExportEngineTest.java",
                                                "package nl.example.export;\n\nimport org.junit.jupiter.api.Test;\nimport org.junit.jupiter.api.io.TempDir;\nimport java.nio.file.Path;\n\nclass ExportEngineTest {\n    @Test\n    void executesExportToFile(@TempDir Path tmpDir) throws Exception {\n        // test export creates file\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Tom van Dijk", "tom@example.nl", "BE", "Team Alpha"),
                                new RosterEntry("Ahmed El Amrani", "ahmed@example.nl", "BE", "Team Alpha"),
                                new RosterEntry("Karin Willems", "karin@example.nl", "TE", "Team Alpha"))),
                jira(
                        List.of("PROJ-90024", "PROJ-90025"),
                        "Build data export pipeline with CSV output, scheduling, and REST trigger",
                        "- CSV export with configurable columns\n- Scheduled daily at 04:00\n- REST endpoint to trigger ad-hoc exports\n- Export history tracked in database\n- Max 100000 rows per export",
                        yesNoOverrides(false, false, true, true, true, true, false, false, false, false, false),
                        "Tested CSV output and schedule trigger",
                        true,
                        "29 passed, 0 failed",
                        "69%",
                        true,
                        false,
                        false,
                        true,
                        true),
                List.of(
                        "PROJ-90024",
                        "PROJ-90025",
                        "CsvWriter",
                        "ExportEngine",
                        "ExportController",
                        "ScheduledExportRunner",
                        "V60__create_export_tables"),
                List.of("OAuth2TokenStore", "V03__token_cache", "SamlProvider", "OpenIdConnect"),
                "feature/data-export",
                "6.4.0",
                Difficulty.LARGE.defaultMinRecall);
    }

    private static ReportE2EScenario large4() {
        return new ReportE2EScenario(
                "L04-multi-tenant",
                "Multi-tenant isolation with gemeente-scoped data",
                Difficulty.LARGE,
                new RepoRecipe(
                        "multi-tenant-core",
                        List.of(
                                commit(
                                        "Add tenant context holder",
                                        "Bas Mulder",
                                        "bas@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/tenant/TenantContext.java",
                                                "package nl.example.tenant;\n\npublic class TenantContext {\n    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();\n\n    public static void setTenant(String gemeenteCode) { CURRENT.set(gemeenteCode); }\n    public static String getTenant() { return CURRENT.get(); }\n    public static void clear() { CURRENT.remove(); }\n}\n")),
                                commit(
                                        "Add tenant filter for incoming requests",
                                        "Bas Mulder",
                                        "bas@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/tenant/TenantFilter.java",
                                                "package nl.example.tenant;\n\nimport jakarta.servlet.*;\nimport jakarta.servlet.http.HttpServletRequest;\nimport org.springframework.stereotype.Component;\nimport java.io.IOException;\n\n@Component\npublic class TenantFilter implements Filter {\n    @Override\n    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)\n            throws IOException, ServletException {\n        var httpReq = (HttpServletRequest) req;\n        String tenant = httpReq.getHeader(\"X-Gemeente-Code\");\n        if (tenant != null) TenantContext.setTenant(tenant);\n        try { chain.doFilter(req, res); }\n        finally { TenantContext.clear(); }\n    }\n}\n")),
                                commit(
                                        "Add tenant-aware data source routing",
                                        "Bas Mulder",
                                        "bas@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/tenant/TenantDataSourceRouter.java",
                                                "package nl.example.tenant;\n\nimport org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;\n\npublic class TenantDataSourceRouter extends AbstractRoutingDataSource {\n    @Override\n    protected Object determineCurrentLookupKey() {\n        return TenantContext.getTenant();\n    }\n}\n")),
                                commit(
                                        "Add tenant configuration and datasource setup",
                                        "Bas Mulder",
                                        "bas@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/tenant/TenantDataSourceConfig.java",
                                                "package nl.example.tenant;\n\nimport org.springframework.context.annotation.Bean;\nimport org.springframework.context.annotation.Configuration;\nimport javax.sql.DataSource;\nimport java.util.HashMap;\n\n@Configuration\npublic class TenantDataSourceConfig {\n    @Bean\n    public DataSource dataSource() {\n        var router = new TenantDataSourceRouter();\n        router.setTargetDataSources(new HashMap<>());\n        return router;\n    }\n}\n",
                                                "src/main/resources/application-tenant.properties",
                                                "tenant.default=0363\ntenant.datasources.0363.url=jdbc:postgresql://db:5432/gaas_amsterdam\ntenant.datasources.0518.url=jdbc:postgresql://db:5432/gaas_denhaag\n")),
                                commit(
                                        "Add tenant migration scripts",
                                        "Lisa de Vries",
                                        "lisa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/db/migration/V65__add_gemeente_code.sql",
                                                "ALTER TABLE person ADD COLUMN gemeente_code VARCHAR(4);\nUPDATE person SET gemeente_code = '0363' WHERE gemeente_code IS NULL;\nALTER TABLE person ALTER COLUMN gemeente_code SET NOT NULL;\nCREATE INDEX idx_person_gemeente ON person(gemeente_code);\n")),
                                commit(
                                        "Add tenant tests",
                                        "Daan Hendriks",
                                        "daan@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/tenant/TenantContextTest.java",
                                                "package nl.example.tenant;\n\nimport org.junit.jupiter.api.Test;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass TenantContextTest {\n    @Test\n    void storesAndClearsTenant() {\n        TenantContext.setTenant(\"0363\");\n        assertEquals(\"0363\", TenantContext.getTenant());\n        TenantContext.clear();\n        assertNull(TenantContext.getTenant());\n    }\n}\n",
                                                "src/test/java/nl/example/tenant/TenantFilterTest.java",
                                                "package nl.example.tenant;\n\nimport org.junit.jupiter.api.Test;\n\nclass TenantFilterTest {\n    @Test\n    void extractsTenantFromHeader() {\n        // test X-Gemeente-Code extraction\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Bas Mulder", "bas@example.nl", "BE", "Team Gamma"),
                                new RosterEntry("Lisa de Vries", "lisa@example.nl", "BE", "Team Gamma"),
                                new RosterEntry("Daan Hendriks", "daan@example.nl", "TE", "Team Gamma"))),
                jira(
                        List.of("PROJ-90026", "PROJ-90027", "PROJ-90028"),
                        "Implement multi-tenant isolation with gemeente-scoped data access",
                        "- Tenant resolved from X-Gemeente-Code header\n- Data source routing per gemeente\n- Person table scoped with gemeente_code column\n- Default tenant is 0363 (Amsterdam)",
                        yesNoOverrides(true, true, true, true, true, true, false, false, false, false, false),
                        "Tested tenant isolation with two gemeente codes",
                        true,
                        "41 passed, 0 failed",
                        "75%",
                        true,
                        false,
                        true,
                        true,
                        true),
                List.of(
                        "PROJ-90026",
                        "PROJ-90027",
                        "PROJ-90028",
                        "TenantContext",
                        "TenantFilter",
                        "TenantDataSourceRouter",
                        "V65__add_gemeente_code",
                        "gemeente_code"),
                List.of("BiometricScanner", "V02__biometric_data", "FaceRecognition", "NfcReader"),
                "feature/multi-tenant",
                "7.0.0",
                Difficulty.LARGE.defaultMinRecall);
    }

    private static ReportE2EScenario large5() {
        return new ReportE2EScenario(
                "L05-election-module",
                "Verkiezingen module with candidate management",
                Difficulty.LARGE,
                new RepoRecipe(
                        "verkiezingen-module",
                        List.of(
                                commit(
                                        "Add election and candidate entities",
                                        "Sophie Jansen",
                                        "sophie@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/election/ElectionEntity.java",
                                                "package nl.example.election;\n\nimport jakarta.persistence.*;\nimport java.time.LocalDate;\n\n@Entity\npublic class ElectionEntity {\n    @Id @GeneratedValue\n    private Long id;\n    private String name;\n    private LocalDate electionDate;\n    private String type;\n    private String status;\n}\n",
                                                "src/main/java/nl/example/election/CandidateEntity.java",
                                                "package nl.example.election;\n\nimport jakarta.persistence.*;\n\n@Entity\npublic class CandidateEntity {\n    @Id @GeneratedValue\n    private Long id;\n    @ManyToOne\n    private ElectionEntity election;\n    private String name;\n    private String party;\n    private int listPosition;\n}\n")),
                                commit(
                                        "Add election migration script",
                                        "Sophie Jansen",
                                        "sophie@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/db/migration/V70__create_election_tables.sql",
                                                "CREATE TABLE election (\n    id BIGSERIAL PRIMARY KEY,\n    name VARCHAR(200) NOT NULL,\n    election_date DATE NOT NULL,\n    type VARCHAR(50) NOT NULL,\n    status VARCHAR(20) DEFAULT 'PLANNED'\n);\n\nCREATE TABLE candidate (\n    id BIGSERIAL PRIMARY KEY,\n    election_id BIGINT REFERENCES election(id),\n    name VARCHAR(200) NOT NULL,\n    party VARCHAR(100),\n    list_position INT NOT NULL\n);\n\nCREATE INDEX idx_election_date ON election(election_date);\n")),
                                commit(
                                        "Add election service and repository",
                                        "Sophie Jansen",
                                        "sophie@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/election/ElectionRepository.java",
                                                "package nl.example.election;\n\nimport org.springframework.data.jpa.repository.JpaRepository;\nimport java.time.LocalDate;\nimport java.util.List;\n\npublic interface ElectionRepository extends JpaRepository<ElectionEntity, Long> {\n    List<ElectionEntity> findByElectionDateAfter(LocalDate date);\n}\n",
                                                "src/main/java/nl/example/election/ElectionService.java",
                                                "package nl.example.election;\n\nimport org.springframework.stereotype.Service;\nimport java.time.LocalDate;\nimport java.util.List;\n\n@Service\npublic class ElectionService {\n    private final ElectionRepository repo;\n    public ElectionService(ElectionRepository repo) { this.repo = repo; }\n\n    public List<ElectionEntity> getUpcomingElections() {\n        return repo.findByElectionDateAfter(LocalDate.now());\n    }\n}\n")),
                                commit(
                                        "Add election REST controller",
                                        "Ahmed El Amrani",
                                        "ahmed@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/election/ElectionController.java",
                                                "package nl.example.election;\n\nimport org.springframework.web.bind.annotation.*;\nimport java.util.List;\n\n@RestController\n@RequestMapping(\"/api/elections\")\npublic class ElectionController {\n    private final ElectionService service;\n    public ElectionController(ElectionService service) { this.service = service; }\n\n    @GetMapping(\"/upcoming\")\n    public List<ElectionEntity> upcoming() {\n        return service.getUpcomingElections();\n    }\n}\n")),
                                commit(
                                        "Add candidate import functionality",
                                        "Sophie Jansen",
                                        "sophie@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/election/CandidateImporter.java",
                                                "package nl.example.election;\n\nimport org.springframework.stereotype.Component;\nimport java.io.*;\nimport java.util.ArrayList;\nimport java.util.List;\n\n@Component\npublic class CandidateImporter {\n    public List<CandidateEntity> importFromCsv(InputStream csvInput) throws IOException {\n        var candidates = new ArrayList<CandidateEntity>();\n        try (var reader = new BufferedReader(new InputStreamReader(csvInput))) {\n            String line;\n            while ((line = reader.readLine()) != null) {\n                // parse CSV line\n            }\n        }\n        return candidates;\n    }\n}\n")),
                                commit(
                                        "Add election module tests",
                                        "Fatima Bouras",
                                        "fatima@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/election/ElectionServiceTest.java",
                                                "package nl.example.election;\n\nimport org.junit.jupiter.api.Test;\nimport static org.mockito.Mockito.*;\n\nclass ElectionServiceTest {\n    @Test\n    void returnsUpcomingElections() {\n        var repo = mock(ElectionRepository.class);\n        var service = new ElectionService(repo);\n        service.getUpcomingElections();\n        verify(repo).findByElectionDateAfter(any());\n    }\n}\n",
                                                "src/test/java/nl/example/election/CandidateImporterTest.java",
                                                "package nl.example.election;\n\nimport org.junit.jupiter.api.Test;\nimport java.io.ByteArrayInputStream;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass CandidateImporterTest {\n    @Test\n    void importsFromCsv() throws Exception {\n        var csv = \"name;party;position\\nJan;VVD;1\\n\";\n        var importer = new CandidateImporter();\n        var result = importer.importFromCsv(new ByteArrayInputStream(csv.getBytes()));\n        assertNotNull(result);\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Sophie Jansen", "sophie@example.nl", "BE", "Team Epsilon"),
                                new RosterEntry("Ahmed El Amrani", "ahmed@example.nl", "BE", "Team Epsilon"),
                                new RosterEntry("Fatima Bouras", "fatima@example.nl", "TE", "Team Epsilon"))),
                jira(
                        List.of("PROJ-90029", "PROJ-90030"),
                        "Implement verkiezingen module with election and candidate management",
                        "- Election and candidate entities with JPA mapping\n- Upcoming elections query\n- CSV import for candidates\n- REST endpoint for election queries\n- Database tables with proper indexing",
                        yesNoOverrides(false, false, false, false, true, true, false, false, true, false, false),
                        "End-to-end test with sample election data",
                        true,
                        "33 passed, 0 failed",
                        "71%",
                        true,
                        false,
                        false,
                        false,
                        true),
                List.of(
                        "PROJ-90029",
                        "PROJ-90030",
                        "ElectionEntity",
                        "CandidateEntity",
                        "ElectionService",
                        "CandidateImporter",
                        "V70__create_election_tables",
                        "ElectionController"),
                List.of("TaxCalculator", "V01__tax_rates", "InvoiceGenerator", "VatService"),
                "feature/verkiezingen",
                "7.0.0",
                Difficulty.LARGE.defaultMinRecall);
    }

    private static ReportE2EScenario large6() {
        return new ReportE2EScenario(
                "L06-travel-doc-renewal",
                "Reisdocumenten renewal workflow with status tracking",
                Difficulty.LARGE,
                new RepoRecipe(
                        "reisdocumenten-service",
                        List.of(
                                commit(
                                        "Add travel document entity and status enum",
                                        "Rosa Kok",
                                        "rosa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/reisdoc/TravelDocument.java",
                                                "package nl.example.reisdoc;\n\nimport jakarta.persistence.*;\nimport java.time.LocalDate;\n\n@Entity\npublic class TravelDocument {\n    @Id @GeneratedValue\n    private Long id;\n    private String bsn;\n    private String documentNumber;\n    @Enumerated(EnumType.STRING)\n    private DocumentType type;\n    @Enumerated(EnumType.STRING)\n    private DocumentStatus status;\n    private LocalDate issuedDate;\n    private LocalDate expiryDate;\n}\n",
                                                "src/main/java/nl/example/reisdoc/DocumentType.java",
                                                "package nl.example.reisdoc;\n\npublic enum DocumentType { PASPOORT, ID_KAART, NOODDOCUMENT }\n",
                                                "src/main/java/nl/example/reisdoc/DocumentStatus.java",
                                                "package nl.example.reisdoc;\n\npublic enum DocumentStatus { REQUESTED, PROCESSING, ISSUED, EXPIRED, REVOKED }\n")),
                                commit(
                                        "Add travel document migration",
                                        "Rosa Kok",
                                        "rosa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/db/migration/V75__create_travel_document.sql",
                                                "CREATE TABLE travel_document (\n    id BIGSERIAL PRIMARY KEY,\n    bsn VARCHAR(9) NOT NULL,\n    document_number VARCHAR(20) UNIQUE,\n    type VARCHAR(20) NOT NULL,\n    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',\n    issued_date DATE,\n    expiry_date DATE\n);\n\nCREATE INDEX idx_traveldoc_bsn ON travel_document(bsn);\nCREATE INDEX idx_traveldoc_expiry ON travel_document(expiry_date);\n")),
                                commit(
                                        "Add renewal service with validation",
                                        "Rosa Kok",
                                        "rosa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/reisdoc/RenewalService.java",
                                                "package nl.example.reisdoc;\n\nimport org.springframework.stereotype.Service;\nimport java.time.LocalDate;\n\n@Service\npublic class RenewalService {\n    private final TravelDocRepository repo;\n    public RenewalService(TravelDocRepository repo) { this.repo = repo; }\n\n    public TravelDocument requestRenewal(String bsn, DocumentType type) {\n        var existing = repo.findByBsnAndType(bsn, type);\n        if (existing.isPresent() && existing.get().getStatus() == DocumentStatus.PROCESSING) {\n            throw new IllegalStateException(\"Renewal already in progress\");\n        }\n        var doc = new TravelDocument();\n        doc.setBsn(bsn);\n        doc.setType(type);\n        doc.setStatus(DocumentStatus.REQUESTED);\n        return repo.save(doc);\n    }\n}\n",
                                                "src/main/java/nl/example/reisdoc/TravelDocRepository.java",
                                                "package nl.example.reisdoc;\n\nimport org.springframework.data.jpa.repository.JpaRepository;\nimport java.util.Optional;\n\npublic interface TravelDocRepository extends JpaRepository<TravelDocument, Long> {\n    Optional<TravelDocument> findByBsnAndType(String bsn, DocumentType type);\n}\n")),
                                commit(
                                        "Add travel document REST controller",
                                        "Marco Visser",
                                        "marco@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/reisdoc/TravelDocController.java",
                                                "package nl.example.reisdoc;\n\nimport org.springframework.web.bind.annotation.*;\n\n@RestController\n@RequestMapping(\"/api/reisdocumenten\")\npublic class TravelDocController {\n    private final RenewalService service;\n    public TravelDocController(RenewalService service) { this.service = service; }\n\n    @PostMapping(\"/renew\")\n    public TravelDocument renew(@RequestParam String bsn, @RequestParam DocumentType type) {\n        return service.requestRenewal(bsn, type);\n    }\n}\n")),
                                commit(
                                        "Add expiry notification scheduler",
                                        "Rosa Kok",
                                        "rosa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/reisdoc/ExpiryNotifier.java",
                                                "package nl.example.reisdoc;\n\nimport org.springframework.scheduling.annotation.Scheduled;\nimport org.springframework.stereotype.Component;\nimport java.time.LocalDate;\n\n@Component\npublic class ExpiryNotifier {\n    private final TravelDocRepository repo;\n    public ExpiryNotifier(TravelDocRepository repo) { this.repo = repo; }\n\n    @Scheduled(cron = \"0 0 8 * * MON\")\n    public void notifyExpiringSoon() {\n        LocalDate threeMonths = LocalDate.now().plusMonths(3);\n        // find documents expiring within 3 months and notify\n    }\n}\n")),
                                commit(
                                        "Add travel document tests",
                                        "Daan Hendriks",
                                        "daan@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/reisdoc/RenewalServiceTest.java",
                                                "package nl.example.reisdoc;\n\nimport org.junit.jupiter.api.Test;\nimport static org.mockito.Mockito.*;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass RenewalServiceTest {\n    @Test\n    void rejectsRenewalWhenAlreadyProcessing() {\n        var repo = mock(TravelDocRepository.class);\n        var service = new RenewalService(repo);\n        // setup existing PROCESSING document\n        assertThrows(IllegalStateException.class,\n                () -> service.requestRenewal(\"123456789\", DocumentType.PASPOORT));\n    }\n}\n",
                                                "src/test/java/nl/example/reisdoc/TravelDocControllerTest.java",
                                                "package nl.example.reisdoc;\n\nimport org.junit.jupiter.api.Test;\n\nclass TravelDocControllerTest {\n    @Test\n    void renewEndpointCallsService() {\n        // test controller delegation\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Rosa Kok", "rosa@example.nl", "BE", "Team Delta"),
                                new RosterEntry("Marco Visser", "marco@example.nl", "BE", "Team Delta"),
                                new RosterEntry("Daan Hendriks", "daan@example.nl", "TE", "Team Delta"))),
                jira(
                        List.of("PROJ-90031", "PROJ-90032"),
                        "Implement reisdocumenten renewal workflow with status tracking and expiry notifications",
                        "- Renewal request with duplicate check\n- Status tracking: REQUESTED -> PROCESSING -> ISSUED\n- Weekly expiry notification for documents expiring within 3 months\n- REST API for renewal requests\n- Database tables with proper constraints",
                        yesNoOverrides(false, false, false, false, true, true, false, true, false, false, false),
                        "Full renewal cycle tested manually",
                        true,
                        "25 passed, 0 failed",
                        "66%",
                        true,
                        false,
                        false,
                        true,
                        true),
                List.of(
                        "PROJ-90031",
                        "PROJ-90032",
                        "TravelDocument",
                        "RenewalService",
                        "ExpiryNotifier",
                        "TravelDocController",
                        "V75__create_travel_document",
                        "PASPOORT"),
                List.of("ElectionBallot", "V00__ballot_schema", "VoteCounter", "PartyRegistration"),
                "feature/reisdocumenten-renewal",
                "7.0.0",
                Difficulty.LARGE.defaultMinRecall);
    }

    // ────────────────────────────────────────────────────────────
    //  MULTI-REPO scenarios (2 repositories per scenario)
    // ────────────────────────────────────────────────────────────

    private static ReportE2EScenario multiRepoSmall() {
        return new ReportE2EScenario(
                "MR-S-shared-config",
                "Two repos: config lib + consuming service — small coordinated change",
                Difficulty.SMALL,
                new RepoRecipe(
                        "common-config",
                        List.of(
                                commit(
                                        "Add shared timeout constant",
                                        "Jan Bakker",
                                        "jan@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/config/TimeoutDefaults.java",
                                                "package nl.example.config;\n\npublic final class TimeoutDefaults {\n    public static final int HTTP_TIMEOUT_MS = 5000;\n    public static final int DB_TIMEOUT_MS = 3000;\n    private TimeoutDefaults() {}\n}\n")),
                                commit(
                                        "Add timeout configuration tests",
                                        "Jan Bakker",
                                        "jan@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/test/java/nl/example/config/TimeoutDefaultsTest.java",
                                                "package nl.example.config;\n\nimport org.junit.jupiter.api.Test;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass TimeoutDefaultsTest {\n    @Test\n    void httpTimeoutIsPositive() {\n        assertTrue(TimeoutDefaults.HTTP_TIMEOUT_MS > 0);\n    }\n}\n"))),
                        roster("Jan Bakker", "jan@example.nl", "BE", "Team Gamma")),
                List.of(new RepoRecipe(
                        "notification-service",
                        List.of(
                                commit(
                                        "Use shared timeout constants",
                                        "Lisa de Vries",
                                        "lisa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/notify/EmailSender.java",
                                                "package nl.example.notify;\n\nimport nl.example.config.TimeoutDefaults;\nimport java.net.http.HttpClient;\nimport java.time.Duration;\n\npublic class EmailSender {\n    private final HttpClient client = HttpClient.newBuilder()\n            .connectTimeout(Duration.ofMillis(TimeoutDefaults.HTTP_TIMEOUT_MS))\n            .build();\n\n    public void send(String to, String subject, String body) {\n        // sends email via SMTP gateway\n    }\n}\n"))),
                        roster("Lisa de Vries", "lisa@example.nl", "BE", "Team Gamma"))),
                jira(
                        List.of("PROJ-91001"),
                        "Centralize timeout configuration into shared library and apply to notification service",
                        "- Shared timeout constants in common-config\n- notification-service uses shared constants\n- No hardcoded timeout values",
                        yesNoOverrides(false, true, false, false, false, false, false, false, false, false, false),
                        "Verified timeout values in local environment",
                        true,
                        "8 passed, 0 failed",
                        "90%",
                        true,
                        false,
                        false,
                        false,
                        true),
                List.of("PROJ-91001", "TimeoutDefaults", "EmailSender", "HTTP_TIMEOUT_MS", "notification-service"),
                List.of("PaymentGateway", "V99__audit_log", "KafkaProducer", "GraphQLResolver"),
                "feature/shared-timeout",
                "8.0.0",
                Difficulty.SMALL.defaultMinRecall);
    }

    private static ReportE2EScenario multiRepoMedium() {
        return new ReportE2EScenario(
                "MR-M-api-contract",
                "Two repos: API contract library + backend consumer — medium coordinated change",
                Difficulty.MEDIUM,
                new RepoRecipe(
                        "api-contracts",
                        List.of(
                                commit(
                                        "Add citizen search request/response DTOs",
                                        "Tom van Dijk",
                                        "tom@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/contract/CitizenSearchRequest.java",
                                                "package nl.example.contract;\n\nimport jakarta.validation.constraints.NotBlank;\n\npublic record CitizenSearchRequest(\n        @NotBlank String bsn,\n        String firstName,\n        String lastName,\n        String dateOfBirth) {\n}\n",
                                                "src/main/java/nl/example/contract/CitizenSearchResponse.java",
                                                "package nl.example.contract;\n\nimport java.util.List;\n\npublic record CitizenSearchResponse(\n        List<CitizenResult> results,\n        int totalCount,\n        String searchId) {\n\n    public record CitizenResult(String bsn, String fullName, String dateOfBirth, String municipality) {}\n}\n")),
                                commit(
                                        "Add validation constants and error codes",
                                        "Tom van Dijk",
                                        "tom@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/contract/ErrorCodes.java",
                                                "package nl.example.contract;\n\npublic final class ErrorCodes {\n    public static final String INVALID_BSN = \"ERR_INVALID_BSN\";\n    public static final String CITIZEN_NOT_FOUND = \"ERR_CITIZEN_NOT_FOUND\";\n    public static final String SEARCH_LIMIT_EXCEEDED = \"ERR_SEARCH_LIMIT\";\n    private ErrorCodes() {}\n}\n")),
                                commit(
                                        "Add contract tests",
                                        "Karin Willems",
                                        "karin@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/contract/CitizenSearchRequestTest.java",
                                                "package nl.example.contract;\n\nimport org.junit.jupiter.api.Test;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass CitizenSearchRequestTest {\n    @Test\n    void requiresBsn() {\n        var req = new CitizenSearchRequest(\"123456789\", null, null, null);\n        assertNotNull(req.bsn());\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Tom van Dijk", "tom@example.nl", "BE", "Team Alpha"),
                                new RosterEntry("Karin Willems", "karin@example.nl", "TE", "Team Alpha"))),
                List.of(new RepoRecipe(
                        "citizen-search-service",
                        List.of(
                                commit(
                                        "Implement citizen search endpoint using contracts",
                                        "Tom van Dijk",
                                        "tom@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/search/CitizenSearchController.java",
                                                "package nl.example.search;\n\nimport nl.example.contract.*;\nimport org.springframework.web.bind.annotation.*;\n\n@RestController\n@RequestMapping(\"/api/citizens\")\npublic class CitizenSearchController {\n    private final CitizenSearchService service;\n    public CitizenSearchController(CitizenSearchService service) { this.service = service; }\n\n    @PostMapping(\"/search\")\n    public CitizenSearchResponse search(@RequestBody CitizenSearchRequest request) {\n        return service.search(request);\n    }\n}\n",
                                                "src/main/java/nl/example/search/CitizenSearchService.java",
                                                "package nl.example.search;\n\nimport nl.example.contract.*;\nimport org.springframework.stereotype.Service;\nimport java.util.List;\n\n@Service\npublic class CitizenSearchService {\n    public CitizenSearchResponse search(CitizenSearchRequest request) {\n        if (request.bsn() == null || request.bsn().length() != 9) {\n            throw new IllegalArgumentException(ErrorCodes.INVALID_BSN);\n        }\n        return new CitizenSearchResponse(List.of(), 0, \"search-\" + System.currentTimeMillis());\n    }\n}\n")),
                                commit(
                                        "Add search service configuration",
                                        "Tom van Dijk",
                                        "tom@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/application.properties",
                                                "search.max-results=100\nsearch.timeout-ms=5000\nsearch.audit-enabled=true\n")),
                                commit(
                                        "Add search service tests",
                                        "Karin Willems",
                                        "karin@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/search/CitizenSearchServiceTest.java",
                                                "package nl.example.search;\n\nimport nl.example.contract.*;\nimport org.junit.jupiter.api.Test;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass CitizenSearchServiceTest {\n    @Test\n    void rejectsInvalidBsn() {\n        var service = new CitizenSearchService();\n        var req = new CitizenSearchRequest(\"short\", null, null, null);\n        var ex = assertThrows(IllegalArgumentException.class, () -> service.search(req));\n        assertEquals(ErrorCodes.INVALID_BSN, ex.getMessage());\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Tom van Dijk", "tom@example.nl", "BE", "Team Alpha"),
                                new RosterEntry("Karin Willems", "karin@example.nl", "TE", "Team Alpha")))),
                jira(
                        List.of("PROJ-91010", "PROJ-91011"),
                        "Create citizen search API contracts and implement search service using shared DTOs",
                        "- Shared request/response contracts in api-contracts repo\n- citizen-search-service implements search endpoint\n- BSN validation with proper error codes\n- Search results with pagination metadata",
                        yesNoOverrides(false, true, false, false, false, false, true, false, false, false, false),
                        "Tested search with valid and invalid BSN values",
                        true,
                        "22 passed, 0 failed",
                        "75%",
                        true,
                        false,
                        false,
                        false,
                        true),
                List.of(
                        "PROJ-91010",
                        "CitizenSearchRequest",
                        "CitizenSearchResponse",
                        "CitizenSearchService",
                        "CitizenSearchController",
                        "ErrorCodes",
                        "INVALID_BSN"),
                List.of("PaymentReconciler", "V05__reconciliation_log", "KafkaConsumer", "ReactHook"),
                "feature/citizen-search-contracts",
                "8.1.0",
                Difficulty.MEDIUM.defaultMinRecall);
    }

    private static ReportE2EScenario multiRepoLarge() {
        return new ReportE2EScenario(
                "MR-L-event-driven",
                "Two repos: event publisher + event consumer — large event-driven architecture change",
                Difficulty.LARGE,
                new RepoRecipe(
                        "event-publisher",
                        List.of(
                                commit(
                                        "Add domain event base class and person events",
                                        "Marco Visser",
                                        "marco@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/events/DomainEvent.java",
                                                "package nl.example.events;\n\nimport java.time.Instant;\nimport java.util.UUID;\n\npublic abstract class DomainEvent {\n    private final String eventId = UUID.randomUUID().toString();\n    private final Instant occurredAt = Instant.now();\n    private final String aggregateType;\n    private final String aggregateId;\n\n    protected DomainEvent(String aggregateType, String aggregateId) {\n        this.aggregateType = aggregateType;\n        this.aggregateId = aggregateId;\n    }\n\n    public String getEventId() { return eventId; }\n    public Instant getOccurredAt() { return occurredAt; }\n    public String getAggregateType() { return aggregateType; }\n    public String getAggregateId() { return aggregateId; }\n}\n",
                                                "src/main/java/nl/example/events/PersonRegisteredEvent.java",
                                                "package nl.example.events;\n\npublic class PersonRegisteredEvent extends DomainEvent {\n    private final String bsn;\n    private final String fullName;\n    private final String municipality;\n\n    public PersonRegisteredEvent(String bsn, String fullName, String municipality) {\n        super(\"Person\", bsn);\n        this.bsn = bsn;\n        this.fullName = fullName;\n        this.municipality = municipality;\n    }\n\n    public String getBsn() { return bsn; }\n    public String getFullName() { return fullName; }\n    public String getMunicipality() { return municipality; }\n}\n")),
                                commit(
                                        "Add event publisher service with outbox pattern",
                                        "Marco Visser",
                                        "marco@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/events/EventPublisher.java",
                                                "package nl.example.events;\n\nimport org.springframework.stereotype.Service;\nimport java.util.List;\nimport java.util.concurrent.CopyOnWriteArrayList;\n\n@Service\npublic class EventPublisher {\n    private final OutboxRepository outbox;\n    private final List<DomainEvent> pendingEvents = new CopyOnWriteArrayList<>();\n\n    public EventPublisher(OutboxRepository outbox) { this.outbox = outbox; }\n\n    public void publish(DomainEvent event) {\n        outbox.save(new OutboxEntry(event));\n        pendingEvents.add(event);\n    }\n\n    public List<DomainEvent> drainPending() {\n        var drained = List.copyOf(pendingEvents);\n        pendingEvents.clear();\n        return drained;\n    }\n}\n",
                                                "src/main/java/nl/example/events/OutboxRepository.java",
                                                "package nl.example.events;\n\nimport org.springframework.data.jpa.repository.JpaRepository;\n\npublic interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {\n}\n",
                                                "src/main/java/nl/example/events/OutboxEntry.java",
                                                "package nl.example.events;\n\nimport jakarta.persistence.*;\nimport java.time.Instant;\n\n@Entity\n@Table(name = \"event_outbox\")\npublic class OutboxEntry {\n    @Id @GeneratedValue\n    private Long id;\n    private String eventType;\n    private String aggregateId;\n    private Instant createdAt;\n    @Column(columnDefinition = \"TEXT\")\n    private String payload;\n\n    protected OutboxEntry() {}\n    public OutboxEntry(DomainEvent event) {\n        this.eventType = event.getClass().getSimpleName();\n        this.aggregateId = event.getAggregateId();\n        this.createdAt = event.getOccurredAt();\n    }\n}\n")),
                                commit(
                                        "Add outbox migration",
                                        "Marco Visser",
                                        "marco@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/db/migration/V80__create_event_outbox.sql",
                                                "CREATE TABLE event_outbox (\n    id BIGSERIAL PRIMARY KEY,\n    event_type VARCHAR(100) NOT NULL,\n    aggregate_id VARCHAR(100) NOT NULL,\n    created_at TIMESTAMP NOT NULL DEFAULT NOW(),\n    payload TEXT,\n    published BOOLEAN DEFAULT FALSE\n);\n\nCREATE INDEX idx_outbox_unpublished ON event_outbox(published) WHERE published = FALSE;\n")),
                                commit(
                                        "Add event publisher tests",
                                        "Daan Hendriks",
                                        "daan@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/events/EventPublisherTest.java",
                                                "package nl.example.events;\n\nimport org.junit.jupiter.api.Test;\nimport static org.mockito.Mockito.*;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass EventPublisherTest {\n    @Test\n    void publishSavesToOutbox() {\n        var outbox = mock(OutboxRepository.class);\n        var publisher = new EventPublisher(outbox);\n        publisher.publish(new PersonRegisteredEvent(\"123456789\", \"Test\", \"Amsterdam\"));\n        verify(outbox).save(any());\n    }\n\n    @Test\n    void drainClearsPendingEvents() {\n        var outbox = mock(OutboxRepository.class);\n        var publisher = new EventPublisher(outbox);\n        publisher.publish(new PersonRegisteredEvent(\"123456789\", \"Test\", \"Amsterdam\"));\n        assertEquals(1, publisher.drainPending().size());\n        assertTrue(publisher.drainPending().isEmpty());\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Marco Visser", "marco@example.nl", "BE", "Team Delta"),
                                new RosterEntry("Daan Hendriks", "daan@example.nl", "TE", "Team Delta"))),
                List.of(new RepoRecipe(
                        "event-consumer",
                        List.of(
                                commit(
                                        "Add event listener and person sync handler",
                                        "Rosa Kok",
                                        "rosa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/consumer/EventListener.java",
                                                "package nl.example.consumer;\n\nimport org.springframework.stereotype.Component;\nimport java.util.Map;\nimport java.util.concurrent.ConcurrentHashMap;\n\n@Component\npublic class EventListener {\n    private final Map<String, EventHandler> handlers = new ConcurrentHashMap<>();\n\n    public void register(String eventType, EventHandler handler) {\n        handlers.put(eventType, handler);\n    }\n\n    public void onEvent(String eventType, String payload) {\n        var handler = handlers.get(eventType);\n        if (handler != null) handler.handle(payload);\n    }\n}\n",
                                                "src/main/java/nl/example/consumer/EventHandler.java",
                                                "package nl.example.consumer;\n\n@FunctionalInterface\npublic interface EventHandler {\n    void handle(String payload);\n}\n",
                                                "src/main/java/nl/example/consumer/PersonSyncHandler.java",
                                                "package nl.example.consumer;\n\nimport org.springframework.stereotype.Component;\n\n@Component\npublic class PersonSyncHandler implements EventHandler {\n    private final LocalPersonRepository localRepo;\n\n    public PersonSyncHandler(LocalPersonRepository localRepo) {\n        this.localRepo = localRepo;\n    }\n\n    @Override\n    public void handle(String payload) {\n        // parse event and sync person data to local store\n    }\n}\n")),
                                commit(
                                        "Add local person repository and migration",
                                        "Rosa Kok",
                                        "rosa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/consumer/LocalPersonRepository.java",
                                                "package nl.example.consumer;\n\nimport org.springframework.data.jpa.repository.JpaRepository;\n\npublic interface LocalPersonRepository extends JpaRepository<Object, Long> {\n}\n",
                                                "src/main/resources/db/migration/V01__create_local_person.sql",
                                                "CREATE TABLE local_person (\n    id BIGSERIAL PRIMARY KEY,\n    bsn VARCHAR(9) UNIQUE NOT NULL,\n    full_name VARCHAR(200),\n    municipality VARCHAR(100),\n    synced_at TIMESTAMP DEFAULT NOW()\n);\n")),
                                commit(
                                        "Add dead letter queue for failed events",
                                        "Rosa Kok",
                                        "rosa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/consumer/DeadLetterQueue.java",
                                                "package nl.example.consumer;\n\nimport org.springframework.stereotype.Component;\nimport java.util.Queue;\nimport java.util.concurrent.ConcurrentLinkedQueue;\n\n@Component\npublic class DeadLetterQueue {\n    private final Queue<FailedEvent> queue = new ConcurrentLinkedQueue<>();\n\n    public void enqueue(String eventType, String payload, Exception error) {\n        queue.add(new FailedEvent(eventType, payload, error.getMessage()));\n    }\n\n    public int size() { return queue.size(); }\n\n    record FailedEvent(String eventType, String payload, String errorMessage) {}\n}\n")),
                                commit(
                                        "Add consumer tests",
                                        "Daan Hendriks",
                                        "daan@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/consumer/EventListenerTest.java",
                                                "package nl.example.consumer;\n\nimport org.junit.jupiter.api.Test;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass EventListenerTest {\n    @Test\n    void dispatchesToRegisteredHandler() {\n        var listener = new EventListener();\n        var called = new boolean[]{false};\n        listener.register(\"TestEvent\", payload -> called[0] = true);\n        listener.onEvent(\"TestEvent\", \"{}\");\n        assertTrue(called[0]);\n    }\n\n    @Test\n    void ignoresUnknownEventTypes() {\n        var listener = new EventListener();\n        assertDoesNotThrow(() -> listener.onEvent(\"Unknown\", \"{}\"));\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Rosa Kok", "rosa@example.nl", "BE", "Team Delta"),
                                new RosterEntry("Daan Hendriks", "daan@example.nl", "TE", "Team Delta")))),
                jira(
                        List.of("PROJ-91020", "PROJ-91021", "PROJ-91022"),
                        "Implement event-driven person registration: publisher with outbox pattern in event-publisher repo, consumer with dead letter queue in event-consumer repo",
                        "- Domain events with outbox persistence\n- PersonRegisteredEvent published on registration\n- Consumer syncs person data to local store\n- Dead letter queue for failed event processing\n- Database migrations for outbox and local person tables",
                        yesNoOverrides(false, true, true, false, true, true, false, false, false, false, false),
                        "Full publish-consume cycle tested with mock infrastructure",
                        true,
                        "38 passed, 0 failed",
                        "72%",
                        true,
                        false,
                        false,
                        true,
                        true),
                List.of(
                        "PROJ-91020",
                        "PROJ-91021",
                        "DomainEvent",
                        "PersonRegisteredEvent",
                        "EventPublisher",
                        "OutboxEntry",
                        "V80__create_event_outbox",
                        "EventListener",
                        "PersonSyncHandler",
                        "DeadLetterQueue"),
                List.of("PaymentGateway", "StripeWebhook", "GraphQLMutation", "ReactDashboard"),
                "feature/event-driven-person",
                "8.2.0",
                40.0);
    }

    private static ReportE2EScenario multiRepoXLarge() {
        return new ReportE2EScenario(
                "MR-XL-full-stack-registration",
                "Two large repos: registration backend + BRP integration — full-stack cross-repo delivery",
                Difficulty.LARGE,
                new RepoRecipe(
                        "registration-backend",
                        List.of(
                                commit(
                                        "Add registration request entity and validation",
                                        "Jan Bakker",
                                        "jan@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/registration/RegistrationRequest.java",
                                                "package nl.example.registration;\n\nimport jakarta.persistence.*;\nimport java.time.LocalDate;\nimport java.time.Instant;\n\n@Entity\n@Table(name = \"registration_request\")\npublic class RegistrationRequest {\n    @Id @GeneratedValue\n    private Long id;\n    private String bsn;\n    private String firstName;\n    private String lastName;\n    private LocalDate dateOfBirth;\n    private String municipality;\n    @Enumerated(EnumType.STRING)\n    private RegistrationStatus status;\n    private Instant createdAt;\n    private Instant updatedAt;\n\n    public Long getId() { return id; }\n    public String getBsn() { return bsn; }\n    public void setBsn(String bsn) { this.bsn = bsn; }\n    public String getFirstName() { return firstName; }\n    public void setFirstName(String fn) { this.firstName = fn; }\n    public String getLastName() { return lastName; }\n    public void setLastName(String ln) { this.lastName = ln; }\n    public LocalDate getDateOfBirth() { return dateOfBirth; }\n    public void setDateOfBirth(LocalDate d) { this.dateOfBirth = d; }\n    public String getMunicipality() { return municipality; }\n    public void setMunicipality(String m) { this.municipality = m; }\n    public RegistrationStatus getStatus() { return status; }\n    public void setStatus(RegistrationStatus s) { this.status = s; }\n}\n",
                                                "src/main/java/nl/example/registration/RegistrationStatus.java",
                                                "package nl.example.registration;\n\npublic enum RegistrationStatus {\n    DRAFT, SUBMITTED, VALIDATING, APPROVED, REJECTED, COMPLETED\n}\n",
                                                "src/main/java/nl/example/registration/BsnValidator.java",
                                                "package nl.example.registration;\n\npublic class BsnValidator {\n    public boolean isValid(String bsn) {\n        if (bsn == null || bsn.length() != 9) return false;\n        try {\n            int sum = 0;\n            for (int i = 0; i < 9; i++) {\n                int digit = Character.getNumericValue(bsn.charAt(i));\n                int weight = (i < 8) ? (9 - i) : -1;\n                sum += digit * weight;\n            }\n            return sum % 11 == 0;\n        } catch (NumberFormatException e) {\n            return false;\n        }\n    }\n}\n")),
                                commit(
                                        "Add registration database migration",
                                        "Lisa de Vries",
                                        "lisa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/db/migration/V90__create_registration.sql",
                                                "CREATE TABLE registration_request (\n    id BIGSERIAL PRIMARY KEY,\n    bsn VARCHAR(9) NOT NULL,\n    first_name VARCHAR(100) NOT NULL,\n    last_name VARCHAR(100) NOT NULL,\n    date_of_birth DATE NOT NULL,\n    municipality VARCHAR(100),\n    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',\n    created_at TIMESTAMP DEFAULT NOW(),\n    updated_at TIMESTAMP DEFAULT NOW()\n);\n\nCREATE INDEX idx_registration_bsn ON registration_request(bsn);\nCREATE INDEX idx_registration_status ON registration_request(status);\nCREATE INDEX idx_registration_municipality ON registration_request(municipality);\n",
                                                "src/main/resources/db/migration/V91__create_registration_audit.sql",
                                                "CREATE TABLE registration_audit (\n    id BIGSERIAL PRIMARY KEY,\n    registration_id BIGINT REFERENCES registration_request(id),\n    old_status VARCHAR(20),\n    new_status VARCHAR(20) NOT NULL,\n    changed_by VARCHAR(100),\n    changed_at TIMESTAMP DEFAULT NOW(),\n    reason TEXT\n);\n")),
                                commit(
                                        "Add registration service with workflow",
                                        "Jan Bakker",
                                        "jan@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/registration/RegistrationService.java",
                                                "package nl.example.registration;\n\nimport org.springframework.stereotype.Service;\nimport org.springframework.transaction.annotation.Transactional;\n\n@Service\npublic class RegistrationService {\n    private final RegistrationRepository repo;\n    private final RegistrationAuditRepository auditRepo;\n    private final BsnValidator bsnValidator;\n\n    public RegistrationService(RegistrationRepository repo, RegistrationAuditRepository auditRepo, BsnValidator bsnValidator) {\n        this.repo = repo;\n        this.auditRepo = auditRepo;\n        this.bsnValidator = bsnValidator;\n    }\n\n    @Transactional\n    public RegistrationRequest submit(RegistrationRequest request) {\n        if (!bsnValidator.isValid(request.getBsn())) {\n            throw new IllegalArgumentException(\"Invalid BSN: \" + request.getBsn());\n        }\n        request.setStatus(RegistrationStatus.SUBMITTED);\n        var saved = repo.save(request);\n        auditRepo.logTransition(saved.getId(), null, RegistrationStatus.SUBMITTED, \"system\", \"Initial submission\");\n        return saved;\n    }\n\n    @Transactional\n    public RegistrationRequest approve(Long id, String approvedBy) {\n        var reg = repo.findById(id).orElseThrow();\n        var oldStatus = reg.getStatus();\n        reg.setStatus(RegistrationStatus.APPROVED);\n        auditRepo.logTransition(id, oldStatus, RegistrationStatus.APPROVED, approvedBy, \"Approved\");\n        return repo.save(reg);\n    }\n}\n",
                                                "src/main/java/nl/example/registration/RegistrationRepository.java",
                                                "package nl.example.registration;\n\nimport org.springframework.data.jpa.repository.JpaRepository;\nimport java.util.List;\n\npublic interface RegistrationRepository extends JpaRepository<RegistrationRequest, Long> {\n    List<RegistrationRequest> findByStatus(RegistrationStatus status);\n    List<RegistrationRequest> findByMunicipality(String municipality);\n}\n",
                                                "src/main/java/nl/example/registration/RegistrationAuditRepository.java",
                                                "package nl.example.registration;\n\nimport org.springframework.stereotype.Repository;\n\n@Repository\npublic class RegistrationAuditRepository {\n    public void logTransition(Long registrationId, RegistrationStatus oldStatus, RegistrationStatus newStatus, String changedBy, String reason) {\n        // persists audit entry\n    }\n}\n")),
                                commit(
                                        "Add registration REST controller",
                                        "Lisa de Vries",
                                        "lisa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/registration/RegistrationController.java",
                                                "package nl.example.registration;\n\nimport org.springframework.web.bind.annotation.*;\nimport java.util.List;\n\n@RestController\n@RequestMapping(\"/api/registrations\")\npublic class RegistrationController {\n    private final RegistrationService service;\n    public RegistrationController(RegistrationService service) { this.service = service; }\n\n    @PostMapping\n    public RegistrationRequest submit(@RequestBody RegistrationRequest request) {\n        return service.submit(request);\n    }\n\n    @PostMapping(\"/{id}/approve\")\n    public RegistrationRequest approve(@PathVariable Long id, @RequestParam String approvedBy) {\n        return service.approve(id, approvedBy);\n    }\n}\n")),
                                commit(
                                        "Add registration configuration",
                                        "Jan Bakker",
                                        "jan@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/application.properties",
                                                "registration.auto-validate=true\nregistration.max-pending=1000\nregistration.audit.retention-days=365\nspring.datasource.url=jdbc:postgresql://db:5432/registration\n")),
                                commit(
                                        "Add registration tests",
                                        "Karin Willems",
                                        "karin@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/registration/BsnValidatorTest.java",
                                                "package nl.example.registration;\n\nimport org.junit.jupiter.api.Test;\nimport org.junit.jupiter.params.ParameterizedTest;\nimport org.junit.jupiter.params.provider.ValueSource;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass BsnValidatorTest {\n    private final BsnValidator validator = new BsnValidator();\n\n    @Test\n    void rejectsNull() { assertFalse(validator.isValid(null)); }\n\n    @ParameterizedTest\n    @ValueSource(strings = {\"12345\", \"abcdefghi\", \"0000000000\"})\n    void rejectsInvalid(String bsn) { assertFalse(validator.isValid(bsn)); }\n}\n",
                                                "src/test/java/nl/example/registration/RegistrationServiceTest.java",
                                                "package nl.example.registration;\n\nimport org.junit.jupiter.api.Test;\nimport static org.mockito.Mockito.*;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass RegistrationServiceTest {\n    @Test\n    void submitRejectsInvalidBsn() {\n        var repo = mock(RegistrationRepository.class);\n        var audit = mock(RegistrationAuditRepository.class);\n        var service = new RegistrationService(repo, audit, new BsnValidator());\n        var req = new RegistrationRequest();\n        req.setBsn(\"invalid\");\n        assertThrows(IllegalArgumentException.class, () -> service.submit(req));\n    }\n\n    @Test\n    void approveChangesStatus() {\n        var repo = mock(RegistrationRepository.class);\n        var audit = mock(RegistrationAuditRepository.class);\n        var service = new RegistrationService(repo, audit, new BsnValidator());\n        var req = new RegistrationRequest();\n        req.setStatus(RegistrationStatus.SUBMITTED);\n        when(repo.findById(1L)).thenReturn(java.util.Optional.of(req));\n        when(repo.save(any())).thenReturn(req);\n        var result = service.approve(1L, \"admin\");\n        assertEquals(RegistrationStatus.APPROVED, result.getStatus());\n    }\n}\n")),
                                commit(
                                        "Add registration controller integration test",
                                        "Karin Willems",
                                        "karin@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/registration/RegistrationControllerTest.java",
                                                "package nl.example.registration;\n\nimport org.junit.jupiter.api.Test;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass RegistrationControllerTest {\n    @Test\n    void submitDelegatesToService() {\n        // test controller delegates to service\n    }\n\n    @Test\n    void approveDelegatesToService() {\n        // test controller delegates to service\n    }\n}\n"))),
                        List.of(
                                new RosterEntry("Jan Bakker", "jan@example.nl", "BE", "Team Gamma"),
                                new RosterEntry("Lisa de Vries", "lisa@example.nl", "BE", "Team Gamma"),
                                new RosterEntry("Karin Willems", "karin@example.nl", "TE", "Team Gamma"))),
                List.of(new RepoRecipe(
                        "brp-integration",
                        List.of(
                                commit(
                                        "Add BRP client and connection configuration",
                                        "Marco Visser",
                                        "marco@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/brp/BrpClient.java",
                                                "package nl.example.brp;\n\nimport java.net.http.HttpClient;\nimport java.net.http.HttpRequest;\nimport java.net.http.HttpResponse;\nimport java.net.URI;\nimport java.time.Duration;\n\npublic class BrpClient {\n    private final HttpClient client;\n    private final String baseUrl;\n    private final String apiKey;\n\n    public BrpClient(String baseUrl, String apiKey) {\n        this.baseUrl = baseUrl;\n        this.apiKey = apiKey;\n        this.client = HttpClient.newBuilder()\n                .connectTimeout(Duration.ofSeconds(10))\n                .build();\n    }\n\n    public BrpPerson lookupByBsn(String bsn) {\n        // calls BRP API\n        return new BrpPerson(bsn, \"\", \"\", \"\");\n    }\n}\n",
                                                "src/main/java/nl/example/brp/BrpPerson.java",
                                                "package nl.example.brp;\n\npublic record BrpPerson(String bsn, String fullName, String dateOfBirth, String municipality) {\n}\n",
                                                "src/main/java/nl/example/brp/BrpConfig.java",
                                                "package nl.example.brp;\n\nimport org.springframework.boot.context.properties.ConfigurationProperties;\n\n@ConfigurationProperties(prefix = \"brp\")\npublic class BrpConfig {\n    private String baseUrl;\n    private String apiKey;\n    private int timeoutMs = 10000;\n    private int maxRetries = 3;\n\n    public String getBaseUrl() { return baseUrl; }\n    public void setBaseUrl(String url) { this.baseUrl = url; }\n    public String getApiKey() { return apiKey; }\n    public void setApiKey(String key) { this.apiKey = key; }\n    public int getTimeoutMs() { return timeoutMs; }\n    public void setTimeoutMs(int t) { this.timeoutMs = t; }\n    public int getMaxRetries() { return maxRetries; }\n    public void setMaxRetries(int r) { this.maxRetries = r; }\n}\n")),
                                commit(
                                        "Add BRP verification service with retry logic",
                                        "Marco Visser",
                                        "marco@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/brp/BrpVerificationService.java",
                                                "package nl.example.brp;\n\nimport org.springframework.stereotype.Service;\nimport org.springframework.retry.annotation.Retryable;\n\n@Service\npublic class BrpVerificationService {\n    private final BrpClient brpClient;\n\n    public BrpVerificationService(BrpClient brpClient) {\n        this.brpClient = brpClient;\n    }\n\n    @Retryable(maxAttempts = 3)\n    public BrpVerificationResult verify(String bsn, String expectedName) {\n        BrpPerson person = brpClient.lookupByBsn(bsn);\n        boolean nameMatch = person.fullName().equalsIgnoreCase(expectedName);\n        return new BrpVerificationResult(person, nameMatch, nameMatch ? \"VERIFIED\" : \"NAME_MISMATCH\");\n    }\n}\n",
                                                "src/main/java/nl/example/brp/BrpVerificationResult.java",
                                                "package nl.example.brp;\n\npublic record BrpVerificationResult(BrpPerson person, boolean verified, String status) {\n}\n")),
                                commit(
                                        "Add BRP sync scheduler and metrics",
                                        "Rosa Kok",
                                        "rosa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/brp/BrpSyncScheduler.java",
                                                "package nl.example.brp;\n\nimport org.springframework.scheduling.annotation.Scheduled;\nimport org.springframework.stereotype.Component;\n\n@Component\npublic class BrpSyncScheduler {\n    private final BrpVerificationService verificationService;\n    private final BrpSyncMetrics metrics;\n\n    public BrpSyncScheduler(BrpVerificationService verificationService, BrpSyncMetrics metrics) {\n        this.verificationService = verificationService;\n        this.metrics = metrics;\n    }\n\n    @Scheduled(cron = \"0 0 2 * * *\")\n    public void syncPendingVerifications() {\n        metrics.incrementSyncRuns();\n        // find unverified registrations and verify via BRP\n    }\n}\n",
                                                "src/main/java/nl/example/brp/BrpSyncMetrics.java",
                                                "package nl.example.brp;\n\nimport org.springframework.stereotype.Component;\nimport java.util.concurrent.atomic.AtomicLong;\n\n@Component\npublic class BrpSyncMetrics {\n    private final AtomicLong syncRuns = new AtomicLong();\n    private final AtomicLong verificationsSucceeded = new AtomicLong();\n    private final AtomicLong verificationsFailed = new AtomicLong();\n\n    public void incrementSyncRuns() { syncRuns.incrementAndGet(); }\n    public void recordSuccess() { verificationsSucceeded.incrementAndGet(); }\n    public void recordFailure() { verificationsFailed.incrementAndGet(); }\n    public long getSyncRuns() { return syncRuns.get(); }\n}\n")),
                                commit(
                                        "Add BRP migration for verification results",
                                        "Rosa Kok",
                                        "rosa@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/db/migration/V01__create_brp_verification.sql",
                                                "CREATE TABLE brp_verification (\n    id BIGSERIAL PRIMARY KEY,\n    bsn VARCHAR(9) NOT NULL,\n    verified BOOLEAN NOT NULL,\n    status VARCHAR(30) NOT NULL,\n    brp_full_name VARCHAR(200),\n    brp_date_of_birth VARCHAR(10),\n    brp_municipality VARCHAR(100),\n    verified_at TIMESTAMP DEFAULT NOW()\n);\n\nCREATE INDEX idx_brp_verification_bsn ON brp_verification(bsn);\nCREATE INDEX idx_brp_verification_status ON brp_verification(status);\n")),
                                commit(
                                        "Add BRP application configuration",
                                        "Marco Visser",
                                        "marco@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/resources/application.properties",
                                                "brp.base-url=https://brp.basisregistraties.overheid.nl/api/v2\nbrp.api-key=${BRP_API_KEY}\nbrp.timeout-ms=10000\nbrp.max-retries=3\nspring.datasource.url=jdbc:postgresql://db:5432/brp_integration\n")),
                                commit(
                                        "Add BRP integration tests",
                                        "Daan Hendriks",
                                        "daan@example.nl",
                                        "TE",
                                        Map.of(
                                                "src/test/java/nl/example/brp/BrpClientTest.java",
                                                "package nl.example.brp;\n\nimport org.junit.jupiter.api.Test;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass BrpClientTest {\n    @Test\n    void lookupReturnsPersonForValidBsn() {\n        var client = new BrpClient(\"http://localhost:8080\", \"test-key\");\n        var person = client.lookupByBsn(\"123456789\");\n        assertNotNull(person);\n        assertEquals(\"123456789\", person.bsn());\n    }\n}\n",
                                                "src/test/java/nl/example/brp/BrpVerificationServiceTest.java",
                                                "package nl.example.brp;\n\nimport org.junit.jupiter.api.Test;\nimport static org.mockito.Mockito.*;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass BrpVerificationServiceTest {\n    @Test\n    void verifiedWhenNameMatches() {\n        var client = mock(BrpClient.class);\n        when(client.lookupByBsn(\"123456789\")).thenReturn(new BrpPerson(\"123456789\", \"Jan Bakker\", \"1990-01-01\", \"Amsterdam\"));\n        var service = new BrpVerificationService(client);\n        var result = service.verify(\"123456789\", \"Jan Bakker\");\n        assertTrue(result.verified());\n        assertEquals(\"VERIFIED\", result.status());\n    }\n\n    @Test\n    void notVerifiedWhenNameMismatch() {\n        var client = mock(BrpClient.class);\n        when(client.lookupByBsn(\"123456789\")).thenReturn(new BrpPerson(\"123456789\", \"Other Name\", \"1990-01-01\", \"Amsterdam\"));\n        var service = new BrpVerificationService(client);\n        var result = service.verify(\"123456789\", \"Jan Bakker\");\n        assertFalse(result.verified());\n        assertEquals(\"NAME_MISMATCH\", result.status());\n    }\n}\n",
                                                "src/test/java/nl/example/brp/BrpSyncSchedulerTest.java",
                                                "package nl.example.brp;\n\nimport org.junit.jupiter.api.Test;\nimport static org.mockito.Mockito.*;\nimport static org.junit.jupiter.api.Assertions.*;\n\nclass BrpSyncSchedulerTest {\n    @Test\n    void syncIncrementsMetrics() {\n        var service = mock(BrpVerificationService.class);\n        var metrics = new BrpSyncMetrics();\n        var scheduler = new BrpSyncScheduler(service, metrics);\n        scheduler.syncPendingVerifications();\n        assertEquals(1, metrics.getSyncRuns());\n    }\n}\n")),
                                commit(
                                        "Add BRP error handling and logging",
                                        "Marco Visser",
                                        "marco@example.nl",
                                        "BE",
                                        Map.of(
                                                "src/main/java/nl/example/brp/BrpException.java",
                                                "package nl.example.brp;\n\npublic class BrpException extends RuntimeException {\n    private final String errorCode;\n    public BrpException(String message, String errorCode) {\n        super(message);\n        this.errorCode = errorCode;\n    }\n    public BrpException(String message, String errorCode, Throwable cause) {\n        super(message, cause);\n        this.errorCode = errorCode;\n    }\n    public String getErrorCode() { return errorCode; }\n}\n",
                                                "src/main/java/nl/example/brp/BrpErrorHandler.java",
                                                "package nl.example.brp;\n\nimport org.springframework.web.bind.annotation.ExceptionHandler;\nimport org.springframework.web.bind.annotation.RestControllerAdvice;\nimport org.springframework.http.ResponseEntity;\n\n@RestControllerAdvice\npublic class BrpErrorHandler {\n    @ExceptionHandler(BrpException.class)\n    public ResponseEntity<ErrorResponse> handleBrpError(BrpException e) {\n        return ResponseEntity.badRequest().body(new ErrorResponse(e.getErrorCode(), e.getMessage()));\n    }\n\n    record ErrorResponse(String code, String message) {}\n}\n"))),
                        List.of(
                                new RosterEntry("Marco Visser", "marco@example.nl", "BE", "Team Delta"),
                                new RosterEntry("Rosa Kok", "rosa@example.nl", "BE", "Team Delta"),
                                new RosterEntry("Daan Hendriks", "daan@example.nl", "TE", "Team Delta")))),
                jira(
                        List.of("PROJ-91030", "PROJ-91031", "PROJ-91032", "PROJ-91033"),
                        "Full-stack registration with BRP verification: registration-backend handles citizen registration workflow with audit trail, brp-integration provides BRP verification with retry logic, metrics, and scheduled sync",
                        "- Registration with BSN validation and status workflow (DRAFT -> SUBMITTED -> APPROVED)\n- Audit trail for all status transitions\n- BRP API integration with retry logic\n- Scheduled BRP verification sync\n- Metrics for sync monitoring\n- Error handling with proper error codes\n- Database migrations for registration, audit, and verification tables",
                        yesNoOverrides(true, true, true, true, true, true, true, false, false, false, false),
                        "Full registration and verification cycle tested end-to-end",
                        true,
                        "65 passed, 0 failed",
                        "74%",
                        true,
                        false,
                        false,
                        true,
                        true),
                List.of(
                        "PROJ-91030",
                        "PROJ-91031",
                        "PROJ-91032",
                        "RegistrationRequest",
                        "RegistrationService",
                        "RegistrationController",
                        "BsnValidator",
                        "BrpClient",
                        "BrpVerificationService",
                        "BrpSyncScheduler",
                        "V90__create_registration",
                        "V91__create_registration_audit",
                        "V01__create_brp_verification",
                        "BrpException"),
                List.of("PaymentProcessor", "StripeWebhook", "ElasticSearchIndex", "KafkaStream", "ReactComponent"),
                "feature/registration-brp",
                "9.0.0",
                35.0);
    }

    // ────────────────────────────────────────────────────────────
    //  Helper factory methods
    // ────────────────────────────────────────────────────────────

    private static CommitSpec commit(
            String message, String author, String email, String role, Map<String, String> files) {
        return new CommitSpec(message, author, email, role, files);
    }

    private static List<RosterEntry> roster(String name, String email, String role, String team) {
        return List.of(new RosterEntry(name, email, role, team));
    }

    private static JiraFixture jira(
            List<String> tickets,
            String desc,
            String ac,
            List<ImpactOverride> impacts,
            String manualTest,
            boolean hasAuto,
            String autoResult,
            String coverage,
            boolean stdDeploy,
            boolean toggle,
            boolean script,
            boolean hypercare,
            boolean dod) {
        return new JiraFixture(
                tickets,
                desc,
                ac,
                impacts,
                manualTest,
                hasAuto,
                autoResult,
                coverage,
                stdDeploy,
                toggle,
                script,
                hypercare,
                dod);
    }

    /**
     * Creates impact overrides for all 11 areas in order:
     * MuniPortal, Impact op meer dan 1 module, Onderliggende techniek,
     * Operations deployment, Database(s), Datamodel, Balieprocessen,
     * Reisdocumenten, Verkiezingen, Verwijderen van functionaliteit,
     * Front-end / Javascript
     */
    private static List<ImpactOverride> yesNoOverrides(boolean... checks) {
        var areas = nl.example.qualityreport.model.JiraData.Impact.ALL_AREAS;
        var overrides = new java.util.ArrayList<ImpactOverride>();
        for (int i = 0; i < areas.size() && i < checks.length; i++) {
            overrides.add(new ImpactOverride(areas.get(i), checks[i]));
        }
        return overrides;
    }
}
