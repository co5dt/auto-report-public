package nl.example.qualityreport.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitFileContentProviderTest {

    private static final PersonIdent AUTHOR = new PersonIdent("Test Author", "test@example.com");

    @TempDir
    Path tempDir;

    private GitFileContentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new GitFileContentProvider();
    }

    @Nested
    class ReadFileAtRevision {

        @Test
        void readsFileOnBranch() throws Exception {
            Path repo = createRepoWithBranch();

            Optional<String> content = provider.readFileAtRevision(repo, "Service.java", "feature");

            assertThat(content).isPresent();
            assertThat(content.get()).contains("public class Service");
        }

        @Test
        void readsFileOnTarget() throws Exception {
            Path repo = createRepoWithBranch();

            Optional<String> content = provider.readFileAtRevision(repo, "README.md", "main");

            assertThat(content).isPresent();
            assertThat(content.get()).contains("# Init");
        }

        @Test
        void returnsEmptyForNonexistentFile() throws Exception {
            Path repo = createRepoWithBranch();

            Optional<String> content = provider.readFileAtRevision(repo, "doesnotexist.java", "feature");

            assertThat(content).isEmpty();
        }

        @Test
        void returnsEmptyForNonexistentRevision() throws Exception {
            Path repo = createRepoWithBranch();

            Optional<String> content = provider.readFileAtRevision(repo, "README.md", "nonexistent");

            assertThat(content).isEmpty();
        }

        @Test
        void readsModifiedFileAtCorrectRevision() throws Exception {
            Path repo = createRepoWithModification();

            Optional<String> onMain = provider.readFileAtRevision(repo, "App.java", "main");
            Optional<String> onFeature = provider.readFileAtRevision(repo, "App.java", "feature");

            assertThat(onMain).isPresent().hasValueSatisfying(c -> assertThat(c).contains("original"));
            assertThat(onFeature).isPresent().hasValueSatisfying(c -> assertThat(c)
                    .contains("modified"));
        }

        @Test
        void truncatesLargeFile() throws Exception {
            Path repo = createRepoWithLargeFile();

            Optional<String> content = provider.readFileAtRevision(repo, "large.txt", "main");

            assertThat(content).isPresent();
            assertThat(content.get()).contains("[truncated at");
            assertThat(content.get().length()).isLessThan(GitFileContentProvider.MAX_FILE_SIZE + 200);
        }

        @Test
        void handlesBinaryFile() throws Exception {
            Path repo = createRepoWithBinaryFile();

            Optional<String> content = provider.readFileAtRevision(repo, "data.bin", "main");

            assertThat(content).isPresent();
            assertThat(content.get()).contains("[binary file");
        }
    }

    @Nested
    class ListTrackedFiles {

        @Test
        void listsAllTrackedFiles() throws Exception {
            Path repo = createRepoWithBranch();

            List<String> files = provider.listTrackedFiles(repo, "feature");

            assertThat(files).contains("README.md", "Service.java");
        }

        @Test
        void returnsEmptyForNonexistentRevision() throws Exception {
            Path repo = createRepoWithBranch();

            List<String> files = provider.listTrackedFiles(repo, "nonexistent");

            assertThat(files).isEmpty();
        }

        @Test
        void listsFilesInSubdirectories() throws Exception {
            Path repo = createRepoWithSubdirectory();

            List<String> files = provider.listTrackedFiles(repo, "main");

            assertThat(files).contains("src/main/java/Foo.java", "README.md");
        }
    }

    // --- fixture helpers ---

    private Path createRepoWithBranch() throws IOException, GitAPIException {
        Path repoDir = tempDir.resolve("repo-branch");
        Files.createDirectories(repoDir);

        try (Git git = Git.init()
                .setDirectory(repoDir.toFile())
                .setInitialBranch("main")
                .call()) {
            Files.writeString(repoDir.resolve("README.md"), "# Init");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("init")
                    .setAuthor(AUTHOR)
                    .setCommitter(AUTHOR)
                    .call();

            git.checkout().setCreateBranch(true).setName("feature").call();
            Files.writeString(repoDir.resolve("Service.java"), "public class Service {}");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("add service")
                    .setAuthor(AUTHOR)
                    .setCommitter(AUTHOR)
                    .call();
        }

        return repoDir;
    }

    private Path createRepoWithModification() throws IOException, GitAPIException {
        Path repoDir = tempDir.resolve("repo-mod");
        Files.createDirectories(repoDir);

        try (Git git = Git.init()
                .setDirectory(repoDir.toFile())
                .setInitialBranch("main")
                .call()) {
            Files.writeString(repoDir.resolve("App.java"), "class App { /* original */ }");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("init")
                    .setAuthor(AUTHOR)
                    .setCommitter(AUTHOR)
                    .call();

            git.checkout().setCreateBranch(true).setName("feature").call();
            Files.writeString(repoDir.resolve("App.java"), "class App { /* modified */ }");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("modify")
                    .setAuthor(AUTHOR)
                    .setCommitter(AUTHOR)
                    .call();
        }

        return repoDir;
    }

    private Path createRepoWithLargeFile() throws IOException, GitAPIException {
        Path repoDir = tempDir.resolve("repo-large");
        Files.createDirectories(repoDir);

        try (Git git = Git.init()
                .setDirectory(repoDir.toFile())
                .setInitialBranch("main")
                .call()) {
            String largeContent = "X".repeat(GitFileContentProvider.MAX_FILE_SIZE + 5000);
            Files.writeString(repoDir.resolve("large.txt"), largeContent);
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("large file")
                    .setAuthor(AUTHOR)
                    .setCommitter(AUTHOR)
                    .call();
        }

        return repoDir;
    }

    private Path createRepoWithBinaryFile() throws IOException, GitAPIException {
        Path repoDir = tempDir.resolve("repo-binary");
        Files.createDirectories(repoDir);

        try (Git git = Git.init()
                .setDirectory(repoDir.toFile())
                .setInitialBranch("main")
                .call()) {
            byte[] binary = new byte[100];
            binary[50] = 0;
            Files.write(repoDir.resolve("data.bin"), binary);
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("binary file")
                    .setAuthor(AUTHOR)
                    .setCommitter(AUTHOR)
                    .call();
        }

        return repoDir;
    }

    private Path createRepoWithSubdirectory() throws IOException, GitAPIException {
        Path repoDir = tempDir.resolve("repo-subdir");
        Files.createDirectories(repoDir);

        try (Git git = Git.init()
                .setDirectory(repoDir.toFile())
                .setInitialBranch("main")
                .call()) {
            Files.writeString(repoDir.resolve("README.md"), "# Project");
            Path javaDir = repoDir.resolve("src/main/java");
            Files.createDirectories(javaDir);
            Files.writeString(javaDir.resolve("Foo.java"), "class Foo {}");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("init with subdir")
                    .setAuthor(AUTHOR)
                    .setCommitter(AUTHOR)
                    .call();
        }

        return repoDir;
    }
}
