package nl.example.qualityreport.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DiffSummariserTest {

    private static String loadFixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/fixtures/context/" + name));
    }

    @Nested
    class ThresholdBehavior {

        @Test
        void smallDiffShouldUseFullMode() {
            assertThat(DiffSummariser.isFullMode(500)).isTrue();
            assertThat(DiffSummariser.isFullMode(1)).isTrue();
            assertThat(DiffSummariser.isFullMode(0)).isTrue();
        }

        @Test
        void largeDiffShouldUseSummaryMode() {
            assertThat(DiffSummariser.isFullMode(501)).isFalse();
            assertThat(DiffSummariser.isFullMode(1000)).isFalse();
        }

        @Test
        void exactThresholdBoundary() {
            assertThat(DiffSummariser.isFullMode(500)).isTrue();
            assertThat(DiffSummariser.isFullMode(501)).isFalse();
        }
    }

    @Nested
    class FileDiffParsing {

        @Test
        void parsesSmallDiffFixture() throws IOException {
            String diff = loadFixture("small-diff.patch");
            List<DiffSummariser.FileDiff> files = DiffSummariser.parseFileDiffs(diff);

            assertThat(files).isNotEmpty();
            Set<String> paths =
                    files.stream().map(DiffSummariser.FileDiff::path).collect(Collectors.toSet());
            assertThat(paths)
                    .contains(
                            "src/main/java/nl/example/cache/PlCache.java", "src/test/java/nl/example/cache/PlCacheTest.java");
        }

        @Test
        void parsesLargeDiffFixtureWithAllFiles() throws IOException {
            String diff = loadFixture("large-diff.patch");
            List<DiffSummariser.FileDiff> files = DiffSummariser.parseFileDiffs(diff);

            Set<String> paths =
                    files.stream().map(DiffSummariser.FileDiff::path).collect(Collectors.toSet());
            assertThat(paths)
                    .contains(
                            "migrations/V42__add_cache_ttl_column.sql",
                            "src/main/java/nl/example/cache/PlCache.java",
                            "src/main/java/nl/example/query/GbaVQueryHandler.java",
                            "src/main/java/nl/example/auth/TokenValidator.java",
                            "src/main/java/nl/example/config/CacheConfig.java",
                            "src/main/java/nl/example/metrics/MetricsCollector.java");
        }

        @Test
        void computesInsertionsAndDeletions() throws IOException {
            String diff = loadFixture("small-diff.patch");
            List<DiffSummariser.FileDiff> files = DiffSummariser.parseFileDiffs(diff);

            DiffSummariser.FileDiff cacheFile = files.stream()
                    .filter(f -> f.path().contains("PlCache.java"))
                    .findFirst()
                    .orElseThrow();

            assertThat(cacheFile.insertions()).isPositive();
            assertThat(cacheFile.deletions()).isPositive();
        }

        @Test
        void detectsFileStatus() throws IOException {
            String diff = loadFixture("small-diff.patch");
            List<DiffSummariser.FileDiff> files = DiffSummariser.parseFileDiffs(diff);

            DiffSummariser.FileDiff testFile = files.stream()
                    .filter(f -> f.path().contains("PlCacheTest.java"))
                    .findFirst()
                    .orElseThrow();

            assertThat(testFile.status()).isEqualTo("added");
        }

        @Test
        void resultsAreSortedByPath() throws IOException {
            String diff = loadFixture("large-diff.patch");
            List<DiffSummariser.FileDiff> files = DiffSummariser.parseFileDiffs(diff);
            List<String> paths =
                    files.stream().map(DiffSummariser.FileDiff::path).toList();
            assertThat(paths).isSorted();
        }

        @Test
        void emptyDiffReturnsEmptyList() {
            assertThat(DiffSummariser.parseFileDiffs("")).isEmpty();
            assertThat(DiffSummariser.parseFileDiffs(null)).isEmpty();
        }
    }

    @Nested
    class FilesChangedXml {

        @Test
        void coversAllParsedFiles() throws IOException {
            String diff = loadFixture("large-diff.patch");
            List<DiffSummariser.FileDiff> files = DiffSummariser.parseFileDiffs(diff);
            String xml = DiffSummariser.buildFilesChangedXml(files);

            for (DiffSummariser.FileDiff f : files) {
                assertThat(xml).contains("path=\"" + f.path() + "\"");
            }
        }

        @Test
        void includesInsertionAndDeletionCounts() throws IOException {
            String diff = loadFixture("small-diff.patch");
            List<DiffSummariser.FileDiff> files = DiffSummariser.parseFileDiffs(diff);
            String xml = DiffSummariser.buildFilesChangedXml(files);

            assertThat(xml).contains("insertions=\"");
            assertThat(xml).contains("deletions=\"");
        }
    }

    @Nested
    class HighSignalHunkSelection {

        @Test
        void migrationFilesAreAlwaysIncluded() throws IOException {
            String diff = loadFixture("large-diff.patch");
            List<DiffSummariser.FileDiff> files = DiffSummariser.parseFileDiffs(diff);
            List<DiffSummariser.SelectedHunk> hunks = DiffSummariser.selectHighSignalHunks(files);

            assertThat(hunks)
                    .anyMatch(h -> h.priority() == DiffSummariser.HunkPriority.MIGRATION
                            && h.file().contains("V42__add_cache_ttl_column.sql"));
        }

        @Test
        void publicApiChangesAreSelected() throws IOException {
            String diff = loadFixture("large-diff.patch");
            List<DiffSummariser.FileDiff> files = DiffSummariser.parseFileDiffs(diff);
            List<DiffSummariser.SelectedHunk> hunks = DiffSummariser.selectHighSignalHunks(files);

            assertThat(hunks).anyMatch(h -> h.priority() == DiffSummariser.HunkPriority.PUBLIC_API);
        }

        @Test
        void totalLinesRespectBudget() throws IOException {
            String diff = loadFixture("large-diff.patch");
            List<DiffSummariser.FileDiff> files = DiffSummariser.parseFileDiffs(diff);
            List<DiffSummariser.SelectedHunk> hunks = DiffSummariser.selectHighSignalHunks(files);

            int totalLines = hunks.stream()
                    .mapToInt(DiffSummariser.SelectedHunk::lineCount)
                    .sum();
            assertThat(totalLines).isLessThanOrEqualTo(DiffSummariser.HUNK_BUDGET_LINES);
        }

        @Test
        void priorityOrderingIsRespected() throws IOException {
            String diff = loadFixture("large-diff.patch");
            List<DiffSummariser.FileDiff> files = DiffSummariser.parseFileDiffs(diff);
            List<DiffSummariser.SelectedHunk> hunks = DiffSummariser.selectHighSignalHunks(files);

            if (hunks.size() > 1) {
                int migrationIdx = -1;
                int apiIdx = -1;
                for (int i = 0; i < hunks.size(); i++) {
                    if (hunks.get(i).priority() == DiffSummariser.HunkPriority.MIGRATION && migrationIdx == -1)
                        migrationIdx = i;
                    if (hunks.get(i).priority() == DiffSummariser.HunkPriority.PUBLIC_API && apiIdx == -1) apiIdx = i;
                }
                if (migrationIdx >= 0 && apiIdx >= 0) {
                    assertThat(migrationIdx).isLessThan(apiIdx);
                }
            }
        }

        @Test
        void highSignalHunksXmlContainsReasonAttribute() throws IOException {
            String diff = loadFixture("large-diff.patch");
            List<DiffSummariser.FileDiff> files = DiffSummariser.parseFileDiffs(diff);
            List<DiffSummariser.SelectedHunk> hunks = DiffSummariser.selectHighSignalHunks(files);
            String xml = DiffSummariser.buildHighSignalHunksXml(hunks);

            assertThat(xml).contains("reason=\"");
            assertThat(xml).contains("<hunk ");
        }

        @Test
        void emptyDiffProducesNoHunks() {
            List<DiffSummariser.SelectedHunk> hunks = DiffSummariser.selectHighSignalHunks(List.of());
            assertThat(hunks).isEmpty();
        }
    }

    @Nested
    class FileClassification {

        @Test
        void sqlMigrationDetection() {
            assertThat(DiffSummariser.isSqlMigration("migrations/V1__init.sql")).isTrue();
            assertThat(DiffSummariser.isSqlMigration("db/migrate/001_add_users.rb"))
                    .isTrue();
            assertThat(DiffSummariser.isSqlMigration("src/main/Service.java")).isFalse();
        }

        @Test
        void configFileDetection() {
            assertThat(DiffSummariser.isConfigFile("application.yml")).isTrue();
            assertThat(DiffSummariser.isConfigFile("config.properties")).isTrue();
            assertThat(DiffSummariser.isConfigFile("settings.json")).isTrue();
            assertThat(DiffSummariser.isConfigFile("src/Main.java")).isFalse();
        }

        @Test
        void securityFileDetection() {
            assertThat(DiffSummariser.isSecurityRelated("src/auth/TokenService.java"))
                    .isTrue();
            assertThat(DiffSummariser.isSecurityRelated("SecurityConfig.java")).isTrue();
            assertThat(DiffSummariser.isSecurityRelated("src/model/User.java")).isFalse();
        }
    }

    @Nested
    class BinaryFileHandling {

        @Test
        void binaryFilesDetectedFromDiffOutput() {
            String diff =
                    """
                    diff --git a/image.png b/image.png
                    Binary files /dev/null and b/image.png differ
                    diff --git a/src/Main.java b/src/Main.java
                    @@ -1 +1,2 @@
                    -old
                    +new
                    +added
                    """;
            List<DiffSummariser.FileDiff> files = DiffSummariser.parseFileDiffs(diff);

            DiffSummariser.FileDiff imageFile = files.stream()
                    .filter(f -> f.path().equals("image.png"))
                    .findFirst()
                    .orElseThrow();
            assertThat(imageFile.binary()).isTrue();
            assertThat(imageFile.status()).isEqualTo("binary");

            DiffSummariser.FileDiff javaFile = files.stream()
                    .filter(f -> f.path().equals("src/Main.java"))
                    .findFirst()
                    .orElseThrow();
            assertThat(javaFile.binary()).isFalse();
        }

        @Test
        void binaryFileDescriptionSaysBinaryFile() {
            var file = new DiffSummariser.FileDiff("logo.png", "binary", 0, 0, List.of(), true);
            assertThat(DiffSummariser.describeFile(file)).isEqualTo("binary file");
        }

        @Test
        void gitBinaryPatchAlsoDetected() {
            String diff =
                    """
                    diff --git a/font.woff2 b/font.woff2
                    GIT binary patch
                    literal 1234
                    somedata
                    """;
            List<DiffSummariser.FileDiff> files = DiffSummariser.parseFileDiffs(diff);
            assertThat(files).hasSize(1);
            assertThat(files.getFirst().binary()).isTrue();
        }

        @Test
        void binaryFilesIncludedInXmlWithBinaryAttribute() {
            var file = new DiffSummariser.FileDiff("logo.png", "binary", 0, 0, List.of(), true);
            String xml = DiffSummariser.buildFilesChangedXml(List.of(file));
            assertThat(xml).contains("binary=\"true\"");
            assertThat(xml).contains("status=\"binary\"");
        }
    }

    @Nested
    class FileDescription {

        @Test
        void describesMigrationFile() {
            var file = new DiffSummariser.FileDiff(
                    "migrations/V1.sql",
                    "added",
                    3,
                    0,
                    List.of(
                            "@@ -0,0 +1,3 @@\n+ALTER TABLE foo ADD COLUMN bar TEXT;\n+CREATE INDEX idx_bar ON foo(bar);\n"));
            String desc = DiffSummariser.describeFile(file);
            assertThat(desc).contains("ALTER TABLE");
        }

        @Test
        void describesMethodAdditions() {
            var file = new DiffSummariser.FileDiff(
                    "Service.java",
                    "modified",
                    5,
                    0,
                    List.of("@@ +1,5 @@\n+    public void doWork() {\n+        // impl\n+    }\n"));
            String desc = DiffSummariser.describeFile(file);
            assertThat(desc).contains("Methods added: doWork");
        }

        @Test
        void fallsBackToLineStats() {
            var file = new DiffSummariser.FileDiff(
                    "readme.txt", "modified", 10, 3, List.of("@@ -1,3 +1,10 @@\n some text\n"));
            String desc = DiffSummariser.describeFile(file);
            assertThat(desc).isEqualTo("+10 / -3 lines");
        }
    }
}
