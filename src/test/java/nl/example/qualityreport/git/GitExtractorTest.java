package nl.example.qualityreport.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.RepoFile;
import nl.example.qualityreport.roster.Roster;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitExtractorTest {

    private static final Path ROSTER_FIXTURE = Path.of("src/test/resources/fixtures/roster-valid.json");
    private static final Path MULTI_EMAIL_ROSTER_FIXTURE =
            Path.of("src/test/resources/fixtures/roster-multi-email-valid.json");

    private static final PersonIdent TE_AUTHOR = new PersonIdent("Martijn Janssen", "m.janssen@example.nl");
    private static final PersonIdent BE_AUTHOR = new PersonIdent("Chris de Vries", "c.devries@example.nl");
    private static final PersonIdent UNKNOWN_AUTHOR = new PersonIdent("External Dev", "external@other.com");
    private static final PersonIdent BE_ALIAS_AUTHOR = new PersonIdent("Chris D", "chris.devries@external.org");
    private static final PersonIdent TE_ALIAS_AUTHOR = new PersonIdent("M. Janssen", "martijn@personal.dev");

    @TempDir
    Path tempDir;

    private Roster roster;

    @BeforeEach
    void setUp() throws IOException {
        roster = Roster.load(ROSTER_FIXTURE);
    }

    @Test
    void extract_singleRepo_returnsCommitsAndDiff() throws Exception {
        Path repoDir = createFixtureRepo("repo1");

        var extractor = new GitExtractor(roster);
        ChangeData data = extractor.extract(List.of(repoDir), "feature", "main");

        assertThat(data.commits()).hasSize(3);
        assertThat(data.rawDiff()).isNotEmpty();
        assertThat(data.changedFiles()).isNotEmpty();
    }

    @Test
    void extract_commitsHaveCorrectMetadata() throws Exception {
        Path repoDir = createFixtureRepo("repo2");

        var extractor = new GitExtractor(roster);
        ChangeData data = extractor.extract(List.of(repoDir), "feature", "main");

        assertThat(data.commits()).allSatisfy(commit -> {
            assertThat(commit.hash()).isNotBlank();
            assertThat(commit.author()).isNotBlank();
            assertThat(commit.email()).isNotBlank();
            assertThat(commit.message()).isNotBlank();
            assertThat(commit.date()).isNotNull();
        });
    }

    @Test
    void extract_commitsAreSortedByDateAscending() throws Exception {
        Path repoDir = createFixtureRepo("repo3");

        var extractor = new GitExtractor(roster);
        ChangeData data = extractor.extract(List.of(repoDir), "feature", "main");

        for (int i = 1; i < data.commits().size(); i++) {
            assertThat(data.commits().get(i).date())
                    .isAfterOrEqualTo(data.commits().get(i - 1).date());
        }
    }

    @Test
    void extract_rosterMapsRolesCorrectly() throws Exception {
        Path repoDir = createFixtureRepo("repo4");

        var extractor = new GitExtractor(roster);
        ChangeData data = extractor.extract(List.of(repoDir), "feature", "main");

        assertThat(data.commits())
                .anyMatch(c -> c.role().equals("TE") && c.team().equals("Team Alpha"))
                .anyMatch(c -> c.role().equals("BE") && c.team().equals("Team Alpha"));
    }

    @Test
    void extract_unknownAuthorGetsUnknownRole() throws Exception {
        Path repoDir = createRepoWithUnknownAuthor("repo5");

        var extractor = new GitExtractor(roster);
        ChangeData data = extractor.extract(List.of(repoDir), "feature", "main");

        assertThat(data.commits())
                .anyMatch(c -> c.role().equals("unknown") && c.team().equals("unknown"));
    }

    @Test
    void extract_perCommitStatsArePopulated() throws Exception {
        Path repoDir = createFixtureRepo("repo6");

        var extractor = new GitExtractor(roster);
        ChangeData data = extractor.extract(List.of(repoDir), "feature", "main");

        assertThat(data.commits()).allSatisfy(commit -> {
            assertThat(commit.filesChanged()).isGreaterThan(0);
            assertThat(commit.insertions()).isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    void extract_groupByRoleAggregatesCorrectly() throws Exception {
        Path repoDir = createFixtureRepo("repo7");

        var extractor = new GitExtractor(roster);
        ChangeData data = extractor.extract(List.of(repoDir), "feature", "main");

        var byRole = data.groupByRole();
        assertThat(byRole).containsKeys("TE", "BE");
        assertThat(byRole.get("TE").commitCount()).isEqualTo(2);
        assertThat(byRole.get("BE").commitCount()).isEqualTo(1);
    }

    @Test
    void extract_iDocAndScriptDetection() throws Exception {
        Path repoDir = createRepoWithSpecialFiles("repo8");

        var extractor = new GitExtractor(roster);
        ChangeData data = extractor.extract(List.of(repoDir), "feature", "main");

        assertThat(data.iDocCount()).isEqualTo(1);
        assertThat(data.scriptCount()).isEqualTo(1);

        var byType = data.countByFileType();
        assertThat(byType.get("iDoc")).isEqualTo(1L);
        assertThat(byType.get("Scripts")).isEqualTo(1L);
    }

    @Test
    void extract_multiRepo_aggregatesAllRepos() throws Exception {
        Path repo1 = createFixtureRepo("multi1");
        Path repo2 = createFixtureRepo("multi2");

        var extractor = new GitExtractor(roster);
        ChangeData data = extractor.extract(List.of(repo1, repo2), "feature", "main");

        assertThat(data.commits()).hasSize(6);
        assertThat(data.rawDiff()).isNotEmpty();
    }

    @Test
    void extract_multiRepo_commitsRetainRepoName() throws Exception {
        Path repo1 = createFixtureRepo("alpha");
        Path repo2 = createFixtureRepo("beta");

        var extractor = new GitExtractor(roster);
        ChangeData data = extractor.extract(List.of(repo1, repo2), "feature", "main");

        assertThat(data.commits()).anyMatch(c -> c.repoName().equals("alpha"));
        assertThat(data.commits()).anyMatch(c -> c.repoName().equals("beta"));
    }

    @Test
    void extract_multiRepo_collidingPaths_remainDistinctInRepoFiles() throws Exception {
        Path repo1 = createFixtureRepo("repoA");
        Path repo2 = createFixtureRepo("repoB");

        var extractor = new GitExtractor(roster);
        ChangeData data = extractor.extract(List.of(repo1, repo2), "feature", "main");

        List<RepoFile> serviceFiles = data.repoFiles().stream()
                .filter(rf -> rf.path().equals("Service.java"))
                .toList();
        assertThat(serviceFiles).hasSize(2);
        assertThat(serviceFiles).extracting(RepoFile::repoName).containsExactlyInAnyOrder("repoA", "repoB");
    }

    @Test
    void extract_unresolvableRef_throwsIOException() throws Exception {
        Path repoDir = createFixtureRepo("bad-ref");

        var extractor = new GitExtractor(roster);
        assertThatThrownBy(() -> extractor.extract(List.of(repoDir), "nonexistent", "main"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Cannot resolve refs in 1 repository")
                .hasMessageContaining("missing nonexistent");
    }

    @Test
    void extract_multiRepo_oneMissingRef_continuesWithWarning() throws Exception {
        Path goodRepo = createFixtureRepoWithExtraBranch("ref-ok", "release");
        Path badRepo = createFixtureRepo("ref-bad");

        var extractor = new GitExtractor(roster);
        GitExtractor.ExtractionResult result =
                extractor.extractWithWarnings(List.of(goodRepo, badRepo), "feature", "release");

        assertThat(result.changeData().commits()).isNotEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().getFirst()).contains("ref-bad");
        assertThat(result.warnings().getFirst()).contains("missing release");
    }

    @Test
    void extract_multiRepo_bothMissingRef_throwsWhenAllFail() throws Exception {
        Path repo1 = createFixtureRepo("both-bad1");
        Path repo2 = createFixtureRepo("both-bad2");

        var extractor = new GitExtractor(roster);
        assertThatThrownBy(() -> extractor.extractWithWarnings(List.of(repo1, repo2), "nonexistent", "also-missing"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("All 2 repositories failed");
    }

    @Test
    void extract_diffContainsChangedFileMarkers() throws Exception {
        Path repoDir = createFixtureRepo("diff-check");

        var extractor = new GitExtractor(roster);
        ChangeData data = extractor.extract(List.of(repoDir), "feature", "main");

        assertThat(data.rawDiff()).contains("diff --git");
    }

    // --- Multi-email alias tests ---

    @Test
    void extract_aliasEmail_resolvesToSameRoleAndTeam() throws Exception {
        Path repoDir = createRepoWithAliasAuthors("alias-repo");
        Roster multiRoster = Roster.load(MULTI_EMAIL_ROSTER_FIXTURE);

        var extractor = new GitExtractor(multiRoster);
        ChangeData data = extractor.extract(List.of(repoDir), "feature", "main");

        assertThat(data.commits()).allSatisfy(commit -> {
            assertThat(commit.role()).isNotEqualTo("unknown");
            assertThat(commit.team()).isNotEqualTo("unknown");
        });

        assertThat(data.commits())
                .filteredOn(c -> c.email().equals("chris.devries@external.org"))
                .allSatisfy(c -> {
                    assertThat(c.role()).isEqualTo("BE");
                    assertThat(c.team()).isEqualTo("Team Alpha");
                });

        assertThat(data.commits())
                .filteredOn(c -> c.email().equals("martijn@personal.dev"))
                .allSatisfy(c -> {
                    assertThat(c.role()).isEqualTo("TE");
                    assertThat(c.team()).isEqualTo("Team Alpha");
                });
    }

    @Test
    void extract_aliasEmail_unknownAuthorStillUnknown() throws Exception {
        Path repoDir = createRepoWithMixedAliasAndUnknown("alias-unknown");
        Roster multiRoster = Roster.load(MULTI_EMAIL_ROSTER_FIXTURE);

        var extractor = new GitExtractor(multiRoster);
        ChangeData data = extractor.extract(List.of(repoDir), "feature", "main");

        assertThat(data.commits())
                .filteredOn(c -> c.email().equals("external@other.com"))
                .allSatisfy(c -> {
                    assertThat(c.role()).isEqualTo("unknown");
                    assertThat(c.team()).isEqualTo("unknown");
                });

        assertThat(data.commits())
                .filteredOn(c -> c.email().equals("chris.devries@external.org"))
                .allSatisfy(c -> {
                    assertThat(c.role()).isEqualTo("BE");
                    assertThat(c.team()).isEqualTo("Team Alpha");
                });
    }

    // --- Merge commit filtering ---

    @Test
    void extract_mergeCommitsAreExcluded() throws Exception {
        Path repoDir = createRepoWithMergeCommit("merge-test");

        var extractor = new GitExtractor(roster);
        ChangeData data = extractor.extract(List.of(repoDir), "feature", "main");

        assertThat(data.commits()).noneMatch(c -> c.message().startsWith("Merge branch"));
        assertThat(data.commits()).hasSize(1);
    }

    // --- Target branch auto-detection ---

    @Test
    void detectDefaultBranch_returnsMain() throws Exception {
        Path repoDir = createFixtureRepo("detect-main");
        assertThat(GitExtractor.detectDefaultBranch(repoDir)).isEqualTo("main");
    }

    @Test
    void detectDefaultBranch_returnsMasterWhenNoMain() throws Exception {
        Path repoDir = tempDir.resolve("detect-master");
        Files.createDirectories(repoDir);
        try (Git git = Git.init()
                .setDirectory(repoDir.toFile())
                .setInitialBranch("master")
                .call()) {
            Files.writeString(repoDir.resolve("README.md"), "# Init");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("Initial commit")
                    .setAuthor(TE_AUTHOR)
                    .setCommitter(TE_AUTHOR)
                    .call();
        }
        assertThat(GitExtractor.detectDefaultBranch(repoDir)).isEqualTo("master");
    }

    // --- Multi-repo partial failure ---

    @Test
    void extract_multiRepo_partialFailure_continuesWithValidRepos() throws Exception {
        Path goodRepo = createFixtureRepoWithExtraBranch("partial-ok", "release");
        Path badRepo = createFixtureRepo("partial-bad");

        var extractor = new GitExtractor(roster);
        GitExtractor.ExtractionResult result =
                extractor.extractWithWarnings(List.of(goodRepo, badRepo), "feature", "release");

        assertThat(result.changeData().commits()).isNotEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().getFirst()).contains("Skipped partial-bad");
    }

    @Test
    void extract_multiRepo_allFailed_throwsIOException() throws Exception {
        Path bad1 = createFixtureRepo("all-bad1");
        Path bad2 = createFixtureRepo("all-bad2");

        var extractor = new GitExtractor(roster);
        assertThatThrownBy(() -> extractor.extractWithWarnings(List.of(bad1, bad2), "feature", "release"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("All 2 repositories failed");
    }

    // --- Ref validation includes branch suggestions ---

    @Test
    void extract_unresolvableRef_includesBranchSuggestions() throws Exception {
        Path repoDir = createFixtureRepo("suggest-ref");

        var extractor = new GitExtractor(roster);
        assertThatThrownBy(() -> extractor.extract(List.of(repoDir), "nonexistent", "main"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("available branches:")
                .hasMessageContaining("main")
                .hasMessageContaining("feature")
                .hasMessageContaining("Hint:");
    }

    // --- Fixture helpers ---

    private Path createRepoWithMergeCommit(String name) throws IOException, GitAPIException {
        Path repoDir = tempDir.resolve(name);
        Files.createDirectories(repoDir);

        try (Git git = Git.init()
                .setDirectory(repoDir.toFile())
                .setInitialBranch("main")
                .call()) {
            Files.writeString(repoDir.resolve("README.md"), "# Init");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("Initial commit")
                    .setAuthor(TE_AUTHOR)
                    .setCommitter(TE_AUTHOR)
                    .call();

            git.checkout().setCreateBranch(true).setName("feature").call();

            Files.writeString(repoDir.resolve("Feature.java"), "class Feature {}");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("feat: add Feature")
                    .setAuthor(TE_AUTHOR)
                    .setCommitter(TE_AUTHOR)
                    .call();

            git.checkout().setName("main").call();
            Files.writeString(repoDir.resolve("MainFix.java"), "class MainFix {}");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("fix: main-only fix")
                    .setAuthor(BE_AUTHOR)
                    .setCommitter(BE_AUTHOR)
                    .call();

            git.checkout().setName("feature").call();
            git.merge()
                    .include(git.getRepository().resolve("main"))
                    .setMessage("Merge branch 'main' into feature")
                    .call();
        }

        return repoDir;
    }

    private Path createFixtureRepo(String name) throws IOException, GitAPIException {
        Path repoDir = tempDir.resolve(name);
        Files.createDirectories(repoDir);

        try (Git git = Git.init()
                .setDirectory(repoDir.toFile())
                .setInitialBranch("main")
                .call()) {
            // Initial commit on main
            Files.writeString(repoDir.resolve("README.md"), "# Init");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("Initial commit")
                    .setAuthor(TE_AUTHOR)
                    .setCommitter(TE_AUTHOR)
                    .call();

            // Feature branch
            git.checkout().setCreateBranch(true).setName("feature").call();

            // TE commit 1: Java file
            Files.writeString(repoDir.resolve("Service.java"), "public class Service {}");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("feat: add Service class")
                    .setAuthor(TE_AUTHOR)
                    .setCommitter(TE_AUTHOR)
                    .call();

            // BE commit
            Files.writeString(repoDir.resolve("Controller.java"), "public class Controller {}");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("feat: add Controller")
                    .setAuthor(BE_AUTHOR)
                    .setCommitter(BE_AUTHOR)
                    .call();

            // TE commit 2: modify file
            Files.writeString(repoDir.resolve("Service.java"), "public class Service { void run() {} }");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("feat: add run method")
                    .setAuthor(TE_AUTHOR)
                    .setCommitter(TE_AUTHOR)
                    .call();
        }

        return repoDir;
    }

    private Path createFixtureRepoWithExtraBranch(String name, String extraBranch) throws IOException, GitAPIException {
        Path repoDir = createFixtureRepo(name);
        try (Git git = Git.open(repoDir.toFile())) {
            git.checkout().setName("main").call();
            git.branchCreate().setName(extraBranch).call();
            git.checkout().setName("feature").call();
        }
        return repoDir;
    }

    private Path createRepoWithUnknownAuthor(String name) throws IOException, GitAPIException {
        Path repoDir = tempDir.resolve(name);
        Files.createDirectories(repoDir);

        try (Git git = Git.init()
                .setDirectory(repoDir.toFile())
                .setInitialBranch("main")
                .call()) {
            Files.writeString(repoDir.resolve("README.md"), "# Init");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("Initial commit")
                    .setAuthor(TE_AUTHOR)
                    .setCommitter(TE_AUTHOR)
                    .call();

            git.checkout().setCreateBranch(true).setName("feature").call();

            Files.writeString(repoDir.resolve("External.java"), "class External {}");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("feat: external contribution")
                    .setAuthor(UNKNOWN_AUTHOR)
                    .setCommitter(UNKNOWN_AUTHOR)
                    .call();
        }

        return repoDir;
    }

    private Path createRepoWithSpecialFiles(String name) throws IOException, GitAPIException {
        Path repoDir = tempDir.resolve(name);
        Files.createDirectories(repoDir);

        try (Git git = Git.init()
                .setDirectory(repoDir.toFile())
                .setInitialBranch("main")
                .call()) {
            Files.writeString(repoDir.resolve("README.md"), "# Init");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("Initial commit")
                    .setAuthor(TE_AUTHOR)
                    .setCommitter(TE_AUTHOR)
                    .call();

            git.checkout().setCreateBranch(true).setName("feature").call();

            Path templateDir = repoDir.resolve("templates");
            Files.createDirectories(templateDir);
            Files.writeString(templateDir.resolve("letter.ftl"), "<html>template</html>");

            Path migrationDir = repoDir.resolve("migrations");
            Files.createDirectories(migrationDir);
            Files.writeString(migrationDir.resolve("V42__add_column.sql"), "ALTER TABLE t ADD COLUMN c VARCHAR(255);");

            Files.writeString(repoDir.resolve("App.java"), "class App {}");

            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("feat: add templates, migrations, and code")
                    .setAuthor(TE_AUTHOR)
                    .setCommitter(TE_AUTHOR)
                    .call();
        }

        return repoDir;
    }

    private Path createRepoWithAliasAuthors(String name) throws IOException, GitAPIException {
        Path repoDir = tempDir.resolve(name);
        Files.createDirectories(repoDir);

        try (Git git = Git.init()
                .setDirectory(repoDir.toFile())
                .setInitialBranch("main")
                .call()) {
            Files.writeString(repoDir.resolve("README.md"), "# Init");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("Initial commit")
                    .setAuthor(TE_AUTHOR)
                    .setCommitter(TE_AUTHOR)
                    .call();

            git.checkout().setCreateBranch(true).setName("feature").call();

            Files.writeString(repoDir.resolve("Controller.java"), "class Controller {}");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("feat: add controller via alias email")
                    .setAuthor(BE_ALIAS_AUTHOR)
                    .setCommitter(BE_ALIAS_AUTHOR)
                    .call();

            Files.writeString(repoDir.resolve("Service.java"), "class Service {}");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("feat: add service via alias email")
                    .setAuthor(TE_ALIAS_AUTHOR)
                    .setCommitter(TE_ALIAS_AUTHOR)
                    .call();
        }

        return repoDir;
    }

    private Path createRepoWithMixedAliasAndUnknown(String name) throws IOException, GitAPIException {
        Path repoDir = tempDir.resolve(name);
        Files.createDirectories(repoDir);

        try (Git git = Git.init()
                .setDirectory(repoDir.toFile())
                .setInitialBranch("main")
                .call()) {
            Files.writeString(repoDir.resolve("README.md"), "# Init");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("Initial commit")
                    .setAuthor(TE_AUTHOR)
                    .setCommitter(TE_AUTHOR)
                    .call();

            git.checkout().setCreateBranch(true).setName("feature").call();

            Files.writeString(repoDir.resolve("Controller.java"), "class Controller {}");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("feat: contribution via alias")
                    .setAuthor(BE_ALIAS_AUTHOR)
                    .setCommitter(BE_ALIAS_AUTHOR)
                    .call();

            Files.writeString(repoDir.resolve("External.java"), "class External {}");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("feat: external contribution")
                    .setAuthor(UNKNOWN_AUTHOR)
                    .setCommitter(UNKNOWN_AUTHOR)
                    .call();
        }

        return repoDir;
    }
}
