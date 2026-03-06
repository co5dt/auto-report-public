package nl.example.qualityreport.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import nl.example.qualityreport.git.CodeReferenceResolver.Resolution;
import nl.example.qualityreport.model.RepoFile;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CodeReferenceResolverTest {

    private static final List<String> TRACKED = List.of(
            "README.md",
            "src/main/java/nl/example/service/PlCache.java",
            "src/main/java/nl/example/service/PlService.java",
            "src/main/java/nl/example/query/GbaVQueryHandler.java",
            "src/test/java/nl/example/service/PlCacheTest.java",
            "migrations/V42__add_cache_ttl_column.sql",
            "src/main/java/nl/example/util/StringUtils.java");

    private static final List<String> CHANGED = List.of(
            "src/main/java/nl/example/service/PlCache.java",
            "src/main/java/nl/example/query/GbaVQueryHandler.java",
            "migrations/V42__add_cache_ttl_column.sql");

    @Nested
    class ExactMatch {

        @Test
        void matchesExactChangedPath() {
            var resolver = new CodeReferenceResolver(TRACKED, CHANGED);

            Resolution r = resolver.resolve("src/main/java/nl/example/service/PlCache.java", true);

            assertThat(r.status()).isEqualTo(Resolution.Status.UNIQUE_MATCH);
            assertThat(r.matches()).containsExactly("src/main/java/nl/example/service/PlCache.java");
            assertThat(r.strategy()).isEqualTo("exact");
        }

        @Test
        void matchesExactTrackedPath() {
            var resolver = new CodeReferenceResolver(TRACKED, CHANGED);

            Resolution r = resolver.resolve("src/main/java/nl/example/util/StringUtils.java", false);

            assertThat(r.status()).isEqualTo(Resolution.Status.UNIQUE_MATCH);
            assertThat(r.strategy()).isEqualTo("exact");
        }
    }

    @Nested
    class BasenameMatch {

        @Test
        void matchesByBasename() {
            var resolver = new CodeReferenceResolver(TRACKED, CHANGED);

            Resolution r = resolver.resolve("GbaVQueryHandler.java", true);

            assertThat(r.status()).isEqualTo(Resolution.Status.UNIQUE_MATCH);
            assertThat(r.matches()).containsExactly("src/main/java/nl/example/query/GbaVQueryHandler.java");
            assertThat(r.strategy()).isEqualTo("basename");
        }

        @Test
        void ambiguousBasenameInTrackedFiles() {
            List<String> tracked = List.of("src/main/java/Foo.java", "src/test/java/Foo.java");
            var resolver = new CodeReferenceResolver(tracked, List.of());

            Resolution r = resolver.resolve("Foo.java", false);

            assertThat(r.status()).isEqualTo(Resolution.Status.AMBIGUOUS);
            assertThat(r.matches()).hasSize(2);
        }
    }

    @Nested
    class ClassNameMatch {

        @Test
        void matchesByClassName() {
            var resolver = new CodeReferenceResolver(TRACKED, CHANGED);

            Resolution r = resolver.resolve("PlCache", true);

            assertThat(r.status()).isEqualTo(Resolution.Status.UNIQUE_MATCH);
            assertThat(r.matches()).containsExactly("src/main/java/nl/example/service/PlCache.java");
            assertThat(r.strategy()).isEqualTo("class_name");
        }

        @Test
        void classNameFallsBackToTrackedWhenNotInChanged() {
            var resolver = new CodeReferenceResolver(TRACKED, CHANGED);

            Resolution r = resolver.resolve("StringUtils", true);

            assertThat(r.status()).isEqualTo(Resolution.Status.UNIQUE_MATCH);
            assertThat(r.matches()).containsExactly("src/main/java/nl/example/util/StringUtils.java");
            assertThat(r.strategy()).isEqualTo("class_name");
        }

        @Test
        void ambiguousClassName() {
            List<String> tracked = List.of("src/main/java/Foo.java", "src/test/java/Foo.java");
            var resolver = new CodeReferenceResolver(tracked, List.of());

            Resolution r = resolver.resolve("Foo", false);

            assertThat(r.status()).isEqualTo(Resolution.Status.AMBIGUOUS);
            assertThat(r.matches()).hasSize(2);
            assertThat(r.strategy()).isEqualTo("class_name");
        }

        @Test
        void classNameIgnoresRefsWithDots() {
            var resolver = new CodeReferenceResolver(TRACKED, CHANGED);

            Resolution r = resolver.resolve("PlCache.java", true);

            assertThat(r.strategy()).isNotEqualTo("class_name");
        }

        @Test
        void classNameIgnoresRefsWithSlashes() {
            var resolver = new CodeReferenceResolver(TRACKED, CHANGED);

            Resolution r = resolver.resolve("service/PlCache", true);

            assertThat(r.strategy()).isNotEqualTo("class_name");
        }
    }

    @Nested
    class SuffixMatch {

        @Test
        void matchesBySuffix() {
            var resolver = new CodeReferenceResolver(TRACKED, CHANGED);

            Resolution r = resolver.resolve("service/PlCache.java", true);

            assertThat(r.status()).isEqualTo(Resolution.Status.UNIQUE_MATCH);
            assertThat(r.matches()).containsExactly("src/main/java/nl/example/service/PlCache.java");
            assertThat(r.strategy()).isEqualTo("suffix");
        }

        @Test
        void ambiguousSuffix() {
            List<String> tracked = List.of("a/util/Helper.java", "b/util/Helper.java");
            var resolver = new CodeReferenceResolver(tracked, List.of());

            Resolution r = resolver.resolve("util/Helper.java", false);

            assertThat(r.status()).isEqualTo(Resolution.Status.AMBIGUOUS);
            assertThat(r.matches()).hasSize(2);
            assertThat(r.strategy()).isEqualTo("suffix");
        }
    }

    @Nested
    class NoMatch {

        @Test
        void returnsNoMatchForUnknownRef() {
            var resolver = new CodeReferenceResolver(TRACKED, CHANGED);

            Resolution r = resolver.resolve("CompletelyUnknown", false);

            assertThat(r.status()).isEqualTo(Resolution.Status.NO_MATCH);
            assertThat(r.matches()).isEmpty();
        }

        @Test
        void returnsNoMatchForNull() {
            var resolver = new CodeReferenceResolver(TRACKED, CHANGED);

            Resolution r = resolver.resolve(null, false);

            assertThat(r.status()).isEqualTo(Resolution.Status.NO_MATCH);
        }

        @Test
        void returnsNoMatchForBlank() {
            var resolver = new CodeReferenceResolver(TRACKED, CHANGED);

            Resolution r = resolver.resolve("   ", false);

            assertThat(r.status()).isEqualTo(Resolution.Status.NO_MATCH);
        }
    }

    @Nested
    class FallbackFromChangedToTracked {

        @Test
        void fallsBackToTrackedWhenNotInChanged() {
            var resolver = new CodeReferenceResolver(TRACKED, CHANGED);

            Resolution r = resolver.resolve("PlService.java", true);

            assertThat(r.status()).isEqualTo(Resolution.Status.UNIQUE_MATCH);
            assertThat(r.matches()).containsExactly("src/main/java/nl/example/service/PlService.java");
        }

        @Test
        void doesNotFallBackWhenRestrictToChangedIsFalse() {
            var resolver = new CodeReferenceResolver(TRACKED, List.of());

            Resolution r = resolver.resolve("PlCache.java", false);

            assertThat(r.status()).isEqualTo(Resolution.Status.UNIQUE_MATCH);
        }
    }

    @Nested
    class CrossRepoAmbiguity {

        @Test
        void samePathInTwoRepos_isAmbiguous() {
            var tracked = List.of(
                    new RepoFile("repoA", "src/main/java/Foo.java"), new RepoFile("repoB", "src/main/java/Foo.java"));
            var resolver = new CodeReferenceResolver(tracked, List.of(), true);

            Resolution r = resolver.resolve("Foo.java", false);

            assertThat(r.status()).isEqualTo(Resolution.Status.AMBIGUOUS);
            assertThat(r.repoMatches()).extracting(RepoFile::repoName).containsExactlyInAnyOrder("repoA", "repoB");
            assertThat(r.matches()).hasSize(2);
        }

        @Test
        void uniquePathInOneRepo_resolvesUnambiguously() {
            var tracked = List.of(
                    new RepoFile("repoA", "src/main/java/Foo.java"), new RepoFile("repoB", "src/main/java/Bar.java"));
            var resolver = new CodeReferenceResolver(tracked, List.of(), true);

            Resolution r = resolver.resolve("Foo", false);

            assertThat(r.status()).isEqualTo(Resolution.Status.UNIQUE_MATCH);
            assertThat(r.repoMatches().getFirst().repoName()).isEqualTo("repoA");
            assertThat(r.matches()).containsExactly("src/main/java/Foo.java");
        }

        @Test
        void exactPathInOneRepo_resolvesWithRepoContext() {
            var changed = List.of(new RepoFile("alpha", "src/Service.java"), new RepoFile("beta", "src/Service.java"));
            var resolver = new CodeReferenceResolver(List.of(), changed, true);

            Resolution r = resolver.resolve("src/Service.java", true);

            assertThat(r.status()).isEqualTo(Resolution.Status.AMBIGUOUS);
            assertThat(r.repoMatches()).hasSize(2);
        }
    }
}
