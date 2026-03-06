package nl.example.qualityreport.llm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * Creates temporary git repositories that reproduce the small-cache-fix
 * and large-cache-overhaul scenarios used by accuracy and tool-use E2E tests.
 * Each method materialises a repo on disk with realistic branch history,
 * commit authors, and file content that exercises the same critical facts
 * the accuracy scorer checks for.
 */
final class ScenarioGitRepoBuilder {

    private ScenarioGitRepoBuilder() {}

    record RepoFixture(Path repoDir, Path rosterPath) {}

    // ── small scenario ──────────────────────────────────────────────────

    static RepoFixture buildSmallScenarioRepo(Path baseDir) throws IOException, GitAPIException {
        Path repoDir = baseDir.resolve("repo");
        Files.createDirectories(repoDir);

        try (Git git = Git.init()
                .setInitialBranch("main")
                .setDirectory(repoDir.toFile())
                .call()) {
            writeFile(repoDir, "src/main/java/nl/example/cache/PlCache.java", smallBasePlCache());
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("initial: add PlCache")
                    .setAuthor("System", "system@example.nl")
                    .call();

            git.branchCreate().setName("feature/test").call();
            git.checkout().setName("feature/test").call();

            writeFile(repoDir, "src/main/java/nl/example/cache/PlCache.java", smallModifiedPlCache());
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("fix: invalidate cache on PL update")
                    .setAuthor("Alice Dev", "alice@example.nl")
                    .call();

            writeFile(repoDir, "src/test/java/nl/example/cache/PlCacheTest.java", smallPlCacheTest());
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("test: add cache invalidation tests")
                    .setAuthor("Bob Test", "bob@example.nl")
                    .call();
        }

        Path roster = writeRoster(baseDir, false);
        return new RepoFixture(repoDir, roster);
    }

    // ── large scenario ──────────────────────────────────────────────────

    static RepoFixture buildLargeScenarioRepo(Path baseDir) throws IOException, GitAPIException {
        Path repoDir = baseDir.resolve("repo");
        Files.createDirectories(repoDir);

        try (Git git = Git.init()
                .setInitialBranch("main")
                .setDirectory(repoDir.toFile())
                .call()) {
            writeLargeBaseFiles(repoDir);
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("initial: base services")
                    .setAuthor("System", "system@example.nl")
                    .call();

            git.branchCreate().setName("feature/test").call();
            git.checkout().setName("feature/test").call();

            writeLargeAliceChanges(repoDir);
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("feat: add cache TTL and metrics")
                    .setAuthor("Alice Dev", "alice@example.nl")
                    .call();

            writeLargeBobChanges(repoDir);
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("test: add cache and query tests")
                    .setAuthor("Bob Test", "bob@example.nl")
                    .call();

            writeLargeCharlieChanges(repoDir);
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("feat: add token revocation")
                    .setAuthor("Charlie Sec", "charlie@example.nl")
                    .call();
        }

        Path roster = writeRoster(baseDir, true);
        return new RepoFixture(repoDir, roster);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static void writeFile(Path repoDir, String relativePath, String content) throws IOException {
        Path file = repoDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static Path writeRoster(Path baseDir, boolean includeTeamBeta) throws IOException {
        Path roster = baseDir.resolve("roster.json");
        String teamBetaSection = includeTeamBeta
                ? """
                        ,
                            "Team Beta": {
                              "members": [
                                {"name": "Charlie Sec", "email": "charlie@example.nl", "role": "BE"}
                              ]
                            }"""
                : "";

        Files.writeString(
                roster,
                """
                        {
                          "teams": {
                            "Team Alpha": {
                              "members": [
                                {"name": "Alice Dev", "email": "alice@example.nl", "role": "BE"},
                                {"name": "Bob Test", "email": "bob@example.nl", "role": "TE"}
                              ]
                            }%s
                          }
                        }
                        """
                        .formatted(teamBetaSection));
        return roster;
    }

    // ── small-scenario file contents ────────────────────────────────────

    private static String smallBasePlCache() {
        return """
                package nl.example.cache;

                import java.time.Instant;
                import java.util.Map;
                import java.util.Optional;
                import java.util.concurrent.ConcurrentHashMap;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;

                public class PlCache {

                    private static final Logger log = LoggerFactory.getLogger(PlCache.class);
                    private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();

                    public void put(String bsn, PlData data) {
                        entries.put(bsn, new CacheEntry(data, Instant.now()));
                    }

                    public Optional<PlData> get(String bsn) {
                        return Optional.ofNullable(entries.get(bsn))
                                .map(CacheEntry::data);
                    }

                    private record CacheEntry(PlData data, Instant timestamp) {}
                }
                """;
    }

    private static String smallModifiedPlCache() {
        return """
                package nl.example.cache;

                import java.time.Instant;
                import java.util.Map;
                import java.util.Optional;
                import java.util.concurrent.ConcurrentHashMap;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;

                public class PlCache {

                    private static final Logger log = LoggerFactory.getLogger(PlCache.class);
                    private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();

                    public void invalidate(String bsn) {
                        entries.remove(bsn);
                        log.info("Cache invalidated for BSN {}", bsn);
                    }

                    public void refreshEntry(String bsn, PlData data) {
                        entries.put(bsn, new CacheEntry(data, Instant.now()));
                    }

                    public void put(String bsn, PlData data) {
                        entries.put(bsn, new CacheEntry(data, Instant.now()));
                    }

                    public Optional<PlData> get(String bsn) {
                        CacheEntry entry = entries.get(bsn);
                        if (entry != null && entry.isExpired()) {
                            entries.remove(bsn);
                            return Optional.empty();
                        }
                        return Optional.ofNullable(entry).map(CacheEntry::data);
                    }

                    private record CacheEntry(PlData data, Instant timestamp) {
                        boolean isExpired() {
                            return false;
                        }
                    }
                }
                """;
    }

    private static String smallPlCacheTest() {
        return """
                package nl.example.cache;

                import org.junit.jupiter.api.Test;
                import static org.assertj.core.api.Assertions.assertThat;

                class PlCacheTest {

                    @Test
                    void invalidateShouldRemoveEntry() {
                        var cache = new PlCache();
                        cache.put("123", new PlData("test"));
                        cache.invalidate("123");
                        assertThat(cache.get("123")).isEmpty();
                    }

                    @Test
                    void refreshEntryShouldUpdateData() {
                        var cache = new PlCache();
                        cache.put("456", new PlData("old"));
                        cache.refreshEntry("456", new PlData("new"));
                        assertThat(cache.get("456")).isPresent();
                    }

                    @Test
                    void expiredEntryShouldBeRemoved() {
                        var cache = new PlCache();
                        cache.put("789", new PlData("stale"));
                        assertThat(cache.get("789")).isPresent();
                    }

                    @Test
                    void getReturnsEmptyForUnknownKey() {
                        assertThat(new PlCache().get("unknown")).isEmpty();
                    }
                }
                """;
    }

    // ── large-scenario file contents ────────────────────────────────────

    private static void writeLargeBaseFiles(Path repoDir) throws IOException {
        writeFile(repoDir, "src/main/java/nl/example/cache/PlCache.java", smallBasePlCache());

        writeFile(
                repoDir,
                "src/main/java/nl/example/query/GbaVQueryHandler.java",
                """
                        package nl.example.query;

                        import java.util.Optional;
                        import nl.example.cache.PlCache;
                        import nl.example.cache.PlData;
                        import org.slf4j.Logger;
                        import org.slf4j.LoggerFactory;

                        public class GbaVQueryHandler {

                            private static final Logger log = LoggerFactory.getLogger(GbaVQueryHandler.class);
                            private final PlCache cache;
                            private final GbaVClient client;

                            public GbaVQueryHandler(PlCache cache, GbaVClient client) {
                                this.cache = cache;
                                this.client = client;
                            }

                            public QueryResult doQuery(String bsn, Category category) {
                                Optional<PlData> cached = cache.get(bsn);
                                if (cached.isPresent()) {
                                    return QueryResult.fromCache(cached.get());
                                }
                                PlData fresh = client.fetch(bsn, category);
                                cache.put(bsn, fresh);
                                return QueryResult.fromFresh(fresh);
                            }

                            public void clearCache() {
                                // no-op for now
                            }
                        }
                        """);

        writeFile(
                repoDir,
                "src/main/java/nl/example/auth/TokenValidator.java",
                """
                        package nl.example.auth;

                        import org.slf4j.Logger;
                        import org.slf4j.LoggerFactory;

                        public class TokenValidator {

                            private static final Logger log = LoggerFactory.getLogger(TokenValidator.class);
                            private final JwtParser parser;

                            public TokenValidator(JwtParser parser) {
                                this.parser = parser;
                            }

                            public boolean validate(String token) {
                                try {
                                    parser.parseClaimsJws(token);
                                    return true;
                                } catch (Exception e) {
                                    log.warn("Token validation failed: {}", e.getMessage());
                                    return false;
                                }
                            }
                        }
                        """);

        writeFile(
                repoDir,
                "src/main/java/nl/example/service/DataSliceService.java",
                """
                        package nl.example.service;

                        import nl.example.query.GbaVQueryHandler;
                        import nl.example.query.QueryResult;
                        import nl.example.query.Category;
                        import org.slf4j.Logger;
                        import org.slf4j.LoggerFactory;

                        public class DataSliceService {

                            private static final Logger log = LoggerFactory.getLogger(DataSliceService.class);
                            private final GbaVQueryHandler queryHandler;
                            private final AuditLogger auditLogger;

                            public DataSliceService(GbaVQueryHandler queryHandler, AuditLogger auditLogger) {
                                this.queryHandler = queryHandler;
                                this.auditLogger = auditLogger;
                            }

                            public QueryResult lookup(String bsn, Category category, String userId) {
                                auditLogger.log("LOOKUP", userId, bsn, category.name());
                                return queryHandler.doQuery(bsn, category);
                            }
                        }
                        """);

        writeFile(
                repoDir,
                "src/main/java/nl/example/model/QueryResult.java",
                """
                        package nl.example.model;

                        public record QueryResult(PlData data, boolean fromCache) {
                            public static QueryResult fromCache(PlData data) {
                                return new QueryResult(data, true);
                            }
                            public static QueryResult fromFresh(PlData data) {
                                return new QueryResult(data, false);
                            }
                        }
                        """);

        writeFile(
                repoDir,
                "src/test/java/nl/example/query/GbaVQueryHandlerTest.java",
                """
                        package nl.example.query;

                        import static org.assertj.core.api.Assertions.assertThat;
                        import org.junit.jupiter.api.Test;

                        class GbaVQueryHandlerTest {

                            @Test
                            void queryShouldReturnCachedResult() {
                                var handler = new GbaVQueryHandler(cache, mockClient);
                                handler.doQuery("123", Category.CAT08);
                                var result = handler.doQuery("123", Category.CAT08);
                                assertThat(result.fromCache()).isTrue();
                            }
                        }
                        """);
    }

    private static void writeLargeAliceChanges(Path repoDir) throws IOException {
        writeFile(
                repoDir,
                "src/main/java/nl/example/cache/PlCache.java",
                """
                        package nl.example.cache;

                        import java.time.Duration;
                        import java.time.Instant;
                        import java.util.Objects;
                        import java.util.Map;
                        import java.util.Optional;
                        import java.util.concurrent.ConcurrentHashMap;
                        import org.slf4j.Logger;
                        import org.slf4j.LoggerFactory;

                        public class PlCache {

                            private static final Logger log = LoggerFactory.getLogger(PlCache.class);
                            private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();
                            private final Duration ttl;

                            public PlCache() {
                                this(Duration.ofSeconds(86400));
                            }

                            public PlCache(Duration ttl) {
                                this.ttl = Objects.requireNonNull(ttl);
                            }

                            public void put(String bsn, PlData data) {
                                entries.put(bsn, new CacheEntry(data, Instant.now()));
                                log.debug("Cached entry for BSN {} with TTL {}s", bsn, ttl.toSeconds());
                            }

                            public void invalidate(String bsn) {
                                entries.remove(bsn);
                                log.info("Cache invalidated for BSN {}", bsn);
                            }

                            public void invalidateAll() {
                                int size = entries.size();
                                entries.clear();
                                log.info("Cache cleared: {} entries removed", size);
                            }

                            public void refreshEntry(String bsn, PlData data) {
                                entries.put(bsn, new CacheEntry(data, Instant.now()));
                                log.debug("Cache refreshed for BSN {}", bsn);
                            }

                            public Optional<PlData> get(String bsn) {
                                CacheEntry entry = entries.get(bsn);
                                if (entry == null) {
                                    return Optional.empty();
                                }
                                if (isExpired(entry)) {
                                    entries.remove(bsn);
                                    log.debug("Expired cache entry removed for BSN {}", bsn);
                                    return Optional.empty();
                                }
                                return Optional.of(entry.data());
                            }

                            public int size() {
                                return entries.size();
                            }

                            private boolean isExpired(CacheEntry entry) {
                                return Duration.between(entry.timestamp(), Instant.now()).compareTo(ttl) > 0;
                            }

                            private record CacheEntry(PlData data, Instant timestamp) {
                                CacheEntry {
                                    Objects.requireNonNull(data);
                                    Objects.requireNonNull(timestamp);
                                }
                            }
                        }
                        """);

        writeFile(
                repoDir,
                "migrations/V42__add_cache_ttl_column.sql",
                """
                        ALTER TABLE pl_cache ADD COLUMN ttl_seconds INTEGER DEFAULT 86400;
                        CREATE INDEX idx_pl_cache_ttl ON pl_cache(ttl_seconds);
                        UPDATE pl_cache SET ttl_seconds = 86400 WHERE ttl_seconds IS NULL;
                        ALTER TABLE pl_cache ALTER COLUMN ttl_seconds SET NOT NULL;
                        COMMENT ON COLUMN pl_cache.ttl_seconds IS 'Cache TTL in seconds';
                        """);

        writeFile(
                repoDir,
                "src/main/java/nl/example/config/CacheConfig.java",
                """
                        package nl.example.config;

                        import java.time.Duration;

                        public record CacheConfig(
                                Duration ttl,
                                int maxEntries,
                                boolean enableMetrics
                        ) {
                            public static CacheConfig defaults() {
                                return new CacheConfig(Duration.ofSeconds(86400), 10000, true);
                            }

                            public CacheConfig withTtl(Duration ttl) {
                                return new CacheConfig(ttl, maxEntries, enableMetrics);
                            }
                        }
                        """);

        writeFile(
                repoDir,
                "src/main/java/nl/example/metrics/MetricsCollector.java",
                """
                        package nl.example.metrics;

                        import java.util.Map;
                        import java.util.concurrent.ConcurrentHashMap;
                        import java.util.concurrent.atomic.AtomicLong;

                        public class MetricsCollector {

                            private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

                            public void increment(String name) {
                                counters.computeIfAbsent(name, k -> new AtomicLong()).incrementAndGet();
                            }

                            public long get(String name) {
                                AtomicLong counter = counters.get(name);
                                return counter != null ? counter.get() : 0;
                            }

                            public Map<String, Long> snapshot() {
                                var result = new java.util.TreeMap<String, Long>();
                                counters.forEach((k, v) -> result.put(k, v.get()));
                                return result;
                            }

                            public void reset() {
                                counters.clear();
                            }
                        }
                        """);

        writeFile(
                repoDir,
                "src/main/java/nl/example/event/CacheEventPublisher.java",
                """
                        package nl.example.event;

                        import java.util.List;
                        import java.util.concurrent.CopyOnWriteArrayList;
                        import java.util.function.Consumer;

                        public class CacheEventPublisher {

                            public enum EventType { INVALIDATE, REFRESH, CLEAR, EXPIRE }

                            public record CacheEvent(EventType type, String key, String detail) {}

                            private final List<Consumer<CacheEvent>> listeners = new CopyOnWriteArrayList<>();

                            public void subscribe(Consumer<CacheEvent> listener) {
                                listeners.add(listener);
                            }

                            public void unsubscribe(Consumer<CacheEvent> listener) {
                                listeners.remove(listener);
                            }

                            public void publish(CacheEvent event) {
                                for (Consumer<CacheEvent> listener : listeners) {
                                    try {
                                        listener.accept(event);
                                    } catch (Exception e) {
                                        // Swallow listener exceptions to avoid disrupting publisher
                                    }
                                }
                            }

                            public void publishInvalidate(String key) {
                                publish(new CacheEvent(EventType.INVALIDATE, key, null));
                            }

                            public void publishRefresh(String key) {
                                publish(new CacheEvent(EventType.REFRESH, key, null));
                            }

                            public void publishClear() {
                                publish(new CacheEvent(EventType.CLEAR, null, "All entries cleared"));
                            }
                        }
                        """);

        writeFile(
                repoDir,
                "src/main/java/nl/example/query/GbaVQueryHandler.java",
                """
                        package nl.example.query;

                        import java.util.Optional;
                        import nl.example.cache.PlCache;
                        import nl.example.cache.PlData;
                        import nl.example.metrics.MetricsCollector;
                        import org.slf4j.Logger;
                        import org.slf4j.LoggerFactory;

                        public class GbaVQueryHandler {

                            private static final Logger log = LoggerFactory.getLogger(GbaVQueryHandler.class);
                            private final PlCache cache;
                            private final GbaVClient client;
                            private final MetricsCollector metrics;

                            public GbaVQueryHandler(PlCache cache, GbaVClient client, MetricsCollector metrics) {
                                this.cache = cache;
                                this.client = client;
                                this.metrics = metrics;
                            }

                            public QueryResult query(String bsn, Category category) {
                                metrics.increment("gba_v_query_total");
                                return doQuery(bsn, category);
                            }

                            public QueryResult doQuery(String bsn, Category category) {
                                Optional<PlData> cached = cache.get(bsn);
                                if (cached.isPresent()) {
                                    metrics.increment("gba_v_cache_hit");
                                    return QueryResult.fromCache(cached.get());
                                }
                                metrics.increment("gba_v_cache_miss");
                                PlData fresh = client.fetch(bsn, category);
                                cache.put(bsn, fresh);
                                return QueryResult.fromFresh(fresh);
                            }

                            public void clearCache() {
                                cache.invalidateAll();
                                metrics.increment("gba_v_cache_clear");
                                log.info("Query handler cache cleared");
                            }

                            public void refreshBsn(String bsn) {
                                PlData fresh = client.fetch(bsn, Category.ALL);
                                cache.refreshEntry(bsn, fresh);
                                metrics.increment("gba_v_cache_refresh");
                            }
                        }
                        """);

        writeFile(
                repoDir,
                "src/main/java/nl/example/service/DataSliceService.java",
                """
                        package nl.example.service;

                        import nl.example.event.CacheEventPublisher;
                        import nl.example.query.GbaVQueryHandler;
                        import nl.example.query.QueryResult;
                        import nl.example.query.Category;
                        import org.slf4j.Logger;
                        import org.slf4j.LoggerFactory;

                        public class DataSliceService {

                            private static final Logger log = LoggerFactory.getLogger(DataSliceService.class);
                            private final GbaVQueryHandler queryHandler;
                            private final AuditLogger auditLogger;
                            private final CacheEventPublisher eventPublisher;
                            private final FeatureFlags featureFlags;

                            public DataSliceService(GbaVQueryHandler queryHandler, AuditLogger auditLogger,
                                                    CacheEventPublisher eventPublisher, FeatureFlags featureFlags) {
                                this.queryHandler = queryHandler;
                                this.auditLogger = auditLogger;
                                this.eventPublisher = eventPublisher;
                                this.featureFlags = featureFlags;
                            }

                            public QueryResult lookup(String bsn, Category category, String userId) {
                                if (featureFlags.isEnabled("enhanced_cache_logging")) {
                                    log.info("Lookup requested by {} for BSN {} category {}", userId, bsn, category);
                                }
                                auditLogger.log("LOOKUP", userId, bsn, category.name());
                                QueryResult result = queryHandler.query(bsn, category);
                                if (!result.fromCache()) {
                                    eventPublisher.publishRefresh(bsn);
                                }
                                return result;
                            }

                            public void invalidateAndRefresh(String bsn, String userId) {
                                auditLogger.log("INVALIDATE_REFRESH", userId, bsn, "ALL");
                                queryHandler.refreshBsn(bsn);
                                eventPublisher.publishInvalidate(bsn);
                                log.info("BSN {} invalidated and refreshed by {}", bsn, userId);
                            }
                        }
                        """);

        writeFile(
                repoDir,
                "src/main/java/nl/example/service/FeatureFlags.java",
                """
                        package nl.example.service;

                        import java.util.Map;
                        import java.util.concurrent.ConcurrentHashMap;

                        public class FeatureFlags {

                            private final Map<String, Boolean> flags = new ConcurrentHashMap<>();

                            public boolean isEnabled(String flag) {
                                return flags.getOrDefault(flag, false);
                            }

                            public void enable(String flag) {
                                flags.put(flag, true);
                            }

                            public void disable(String flag) {
                                flags.put(flag, false);
                            }

                            public Map<String, Boolean> allFlags() {
                                return Map.copyOf(flags);
                            }
                        }
                        """);

        writeFile(
                repoDir,
                "src/main/java/nl/example/model/QueryResult.java",
                """
                        package nl.example.model;

                        import java.time.Duration;
                        import java.time.Instant;

                        public record QueryResult(PlData data, boolean fromCache, Instant timestamp) {
                            public static QueryResult fromCache(PlData data) {
                                return new QueryResult(data, true, Instant.now());
                            }
                            public static QueryResult fromFresh(PlData data) {
                                return new QueryResult(data, false, Instant.now());
                            }

                            public Duration age() {
                                return Duration.between(timestamp, Instant.now());
                            }
                        }
                        """);
    }

    private static void writeLargeBobChanges(Path repoDir) throws IOException {
        writeFile(
                repoDir,
                "src/test/java/nl/example/cache/PlCacheTest.java",
                """
                        package nl.example.cache;

                        import org.junit.jupiter.api.Test;
                        import java.time.Duration;
                        import static org.assertj.core.api.Assertions.assertThat;

                        class PlCacheTest {

                            @Test
                            void invalidateShouldRemoveEntry() {
                                var cache = new PlCache();
                                cache.put("123", new PlData("test"));
                                cache.invalidate("123");
                                assertThat(cache.get("123")).isEmpty();
                            }

                            @Test
                            void refreshEntryShouldUpdateData() {
                                var cache = new PlCache();
                                cache.put("456", new PlData("old"));
                                cache.refreshEntry("456", new PlData("new"));
                                assertThat(cache.get("456")).isPresent()
                                        .hasValueSatisfying(d -> assertThat(d.value()).isEqualTo("new"));
                            }

                            @Test
                            void expiredEntryShouldBeRemoved() {
                                var cache = new PlCache(Duration.ofMillis(1));
                                cache.put("789", new PlData("stale"));
                                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
                                assertThat(cache.get("789")).isEmpty();
                            }

                            @Test
                            void getReturnsEmptyForUnknownKey() {
                                assertThat(new PlCache().get("unknown")).isEmpty();
                            }

                            @Test
                            void invalidateAllClearsCache() {
                                var cache = new PlCache();
                                cache.put("a", new PlData("1"));
                                cache.put("b", new PlData("2"));
                                cache.invalidateAll();
                                assertThat(cache.size()).isZero();
                            }

                            @Test
                            void sizeShouldReturnEntryCount() {
                                var cache = new PlCache();
                                assertThat(cache.size()).isZero();
                                cache.put("x", new PlData("v"));
                                assertThat(cache.size()).isEqualTo(1);
                            }

                            @Test
                            void customTtlShouldBeRespected() {
                                var cache = new PlCache(Duration.ofHours(1));
                                cache.put("long", new PlData("lived"));
                                assertThat(cache.get("long")).isPresent();
                            }
                        }
                        """);

        writeFile(
                repoDir,
                "src/test/java/nl/example/query/GbaVQueryHandlerTest.java",
                """
                        package nl.example.query;

                        import static org.assertj.core.api.Assertions.assertThat;
                        import org.junit.jupiter.api.Test;

                        class GbaVQueryHandlerTest {

                            @Test
                            void queryShouldReturnCachedResult() {
                                var handler = new GbaVQueryHandler(cache, mockClient, metrics);
                                handler.query("123", Category.CAT08);
                                var result = handler.query("123", Category.CAT08);
                                assertThat(result.fromCache()).isTrue();
                            }

                            @Test
                            void queryShouldIncrementMetrics() {
                                var handler = new GbaVQueryHandler(cache, mockClient, metrics);
                                handler.query("123", Category.CAT08);
                                assertThat(metrics.get("gba_v_query_total")).isEqualTo(1);
                                assertThat(metrics.get("gba_v_cache_miss")).isEqualTo(1);
                                handler.query("123", Category.CAT08);
                                assertThat(metrics.get("gba_v_cache_hit")).isEqualTo(1);
                            }

                            @Test
                            void clearCacheShouldInvalidateAll() {
                                var handler = new GbaVQueryHandler(cache, mockClient, metrics);
                                handler.query("456", Category.CAT08);
                                handler.clearCache();
                                assertThat(metrics.get("gba_v_cache_clear")).isEqualTo(1);
                            }

                            @Test
                            void refreshBsnShouldFetchFreshData() {
                                var handler = new GbaVQueryHandler(cache, mockClient, metrics);
                                handler.refreshBsn("789");
                                assertThat(metrics.get("gba_v_cache_refresh")).isEqualTo(1);
                            }
                        }
                        """);

        writeFile(
                repoDir,
                "src/test/java/nl/example/service/DataSliceServiceTest.java",
                """
                        package nl.example.service;

                        import nl.example.cache.PlCache;
                        import nl.example.event.CacheEventPublisher;
                        import nl.example.query.GbaVQueryHandler;
                        import org.junit.jupiter.api.Test;
                        import static org.assertj.core.api.Assertions.assertThat;

                        class DataSliceServiceTest {

                            @Test
                            void lookupShouldPublishRefreshEventOnCacheMiss() {
                                var publisher = new CacheEventPublisher();
                                var service = createService(publisher);
                                var events = new java.util.ArrayList<CacheEventPublisher.CacheEvent>();
                                publisher.subscribe(events::add);
                                service.lookup("123", Category.CAT08, "user1");
                                assertThat(events).hasSize(1);
                                assertThat(events.get(0).type()).isEqualTo(CacheEventPublisher.EventType.REFRESH);
                            }

                            @Test
                            void invalidateAndRefreshShouldAuditLog() {
                                var publisher = new CacheEventPublisher();
                                var service = createService(publisher);
                                var events = new java.util.ArrayList<CacheEventPublisher.CacheEvent>();
                                publisher.subscribe(events::add);
                                service.invalidateAndRefresh("456", "admin");
                                assertThat(events).hasSize(1);
                                assertThat(events.get(0).type()).isEqualTo(CacheEventPublisher.EventType.INVALIDATE);
                            }

                            @Test
                            void lookupShouldNotPublishOnCacheHit() {
                                var publisher = new CacheEventPublisher();
                                var service = createService(publisher);
                                service.lookup("789", Category.CAT08, "user2");
                                var events = new java.util.ArrayList<CacheEventPublisher.CacheEvent>();
                                publisher.subscribe(events::add);
                                service.lookup("789", Category.CAT08, "user2");
                                assertThat(events).isEmpty();
                            }
                        }
                        """);
    }

    private static void writeLargeCharlieChanges(Path repoDir) throws IOException {
        writeFile(
                repoDir,
                "src/main/java/nl/example/auth/TokenValidator.java",
                """
                        package nl.example.auth;

                        import java.util.Set;
                        import java.util.concurrent.ConcurrentHashMap;
                        import org.slf4j.Logger;
                        import org.slf4j.LoggerFactory;

                        public class TokenValidator {

                            private static final Logger log = LoggerFactory.getLogger(TokenValidator.class);
                            private final JwtParser parser;
                            private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

                            public TokenValidator(JwtParser parser) {
                                this.parser = parser;
                            }

                            public boolean validate(String token) {
                                if (revokedTokens.contains(token)) {
                                    log.warn("Revoked token presented");
                                    return false;
                                }
                                try {
                                    parser.parseClaimsJws(token);
                                    return true;
                                } catch (Exception e) {
                                    log.warn("Token validation failed: {}", e.getMessage());
                                    return false;
                                }
                            }

                            public void revokeToken(String token) {
                                revokedTokens.add(token);
                                log.info("Token revoked");
                            }

                            public boolean isRevoked(String token) {
                                return revokedTokens.contains(token);
                            }

                            public void clearRevocationList() {
                                revokedTokens.clear();
                                log.info("Revocation list cleared");
                            }
                        }
                        """);

        writeFile(
                repoDir,
                "src/test/java/nl/example/auth/TokenValidatorTest.java",
                """
                        package nl.example.auth;

                        import org.junit.jupiter.api.Test;
                        import static org.assertj.core.api.Assertions.assertThat;

                        class TokenValidatorTest {

                            @Test
                            void revokedTokenShouldFailValidation() {
                                var validator = new TokenValidator(mockParser);
                                validator.revokeToken("abc123");
                                assertThat(validator.validate("abc123")).isFalse();
                            }

                            @Test
                            void isRevokedShouldReturnTrueForRevokedTokens() {
                                var validator = new TokenValidator(mockParser);
                                validator.revokeToken("xyz");
                                assertThat(validator.isRevoked("xyz")).isTrue();
                            }

                            @Test
                            void clearRevocationListShouldRemoveAllRevocations() {
                                var validator = new TokenValidator(mockParser);
                                validator.revokeToken("tok1");
                                validator.revokeToken("tok2");
                                validator.clearRevocationList();
                                assertThat(validator.isRevoked("tok1")).isFalse();
                                assertThat(validator.isRevoked("tok2")).isFalse();
                            }
                        }
                        """);
    }
}
