package nl.example.qualityreport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import nl.example.qualityreport.llm.StubLlmProvider;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class QualityReportCommandIntegrationTest {

    @Test
    void noLlmPathOutputsJsonAndSkipsReport(@TempDir Path tmp) throws Exception {
        Path repoDir = setupGitRepo(tmp);
        Path roster = writeRoster(tmp);
        BufferedReader input = loadFixtureInput();

        var stdoutBuf = new ByteArrayOutputStream();
        var stderrBuf = new ByteArrayOutputStream();
        var stdout = new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8);
        var stderr = new PrintStream(stderrBuf, true, StandardCharsets.UTF_8);

        StubLlmProvider llm = new StubLlmProvider();
        QualityReportCommand cmd =
                new QualityReportCommand(input, stdout, stderr, (name, model, numCtx, verbose) -> llm);

        int exitCode = new CommandLine(cmd)
                .setOut(new java.io.PrintWriter(stdout, true))
                .setErr(new java.io.PrintWriter(stderr, true))
                .execute(
                        "--repo",
                        repoDir.toString(),
                        "--branch",
                        "feature/test",
                        "--target",
                        "main",
                        "--roster",
                        roster.toString(),
                        "--no-llm");

        String output = stdoutBuf.toString(StandardCharsets.UTF_8);

        assertThat(exitCode).isZero();
        assertThat(llm.getCallCount()).isZero();
        assertThat(output).contains("\"changes\"");
        assertThat(output).contains("\"jira\"");
        assertThat(output).contains("PROJ-77777");
        assertThat(output).contains("Bug fix for null pointer");
        assertThat(stderrBuf.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void fullFlowGeneratesReportFile(@TempDir Path tmp) throws Exception {
        Path repoDir = setupGitRepo(tmp.resolve("repo"));
        Path roster = writeRoster(tmp);
        BufferedReader input = loadFixtureInput();
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        var stdoutBuf = new ByteArrayOutputStream();
        var stderrBuf = new ByteArrayOutputStream();
        var stdout = new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8);
        var stderr = new PrintStream(stderrBuf, true, StandardCharsets.UTF_8);

        StubLlmProvider llm = new StubLlmProvider(
                // 3 deliberation agent votes (unanimous round 1)
                "{\"vote\":\"LOW\",\"confidence\":0.9,\"reasoning\":\"Low risk change.\"}",
                "{\"vote\":\"LOW\",\"confidence\":0.85,\"reasoning\":\"Low process risk.\"}",
                "{\"vote\":\"LOW\",\"confidence\":0.88,\"reasoning\":\"Good evidence.\"}",
                // 2 report narrative calls (change + risk)
                "Dit betreft een kleine bugfix in de DataSlice laag.",
                "Het risico is laag gezien de beperkte impact.");

        QualityReportCommand cmd =
                new QualityReportCommand(input, stdout, stderr, (name, model, numCtx, verbose) -> llm);

        int exitCode = new CommandLine(cmd)
                .setOut(new java.io.PrintWriter(stdout, true))
                .setErr(new java.io.PrintWriter(stderr, true))
                .execute(
                        "--repo",
                        repoDir.toString(),
                        "--branch",
                        "feature/test",
                        "--target",
                        "main",
                        "--roster",
                        roster.toString(),
                        "--output",
                        outputDir.toString(),
                        "--provider",
                        "ollama");

        String output = stdoutBuf.toString(StandardCharsets.UTF_8);

        assertThat(exitCode).isZero();
        assertThat(output).contains("Report generated:");
        assertThat(llm.getCallCount()).isEqualTo(5);

        String reportLine = output.lines()
                .filter(l -> l.contains("Report generated:"))
                .findFirst()
                .orElseThrow();
        Path reportPath = Path.of(reportLine.replace("Report generated: ", "").trim());
        assertThat(reportPath).exists();
        assertThat(reportPath).startsWith(outputDir);

        String report = Files.readString(reportPath);
        assertThat(report).contains("PROJ-77777");
        assertThat(report).contains("Testrapport");
    }

    @Test
    void multipleReposAreAccepted(@TempDir Path tmp) throws Exception {
        Path repo1 = setupGitRepo(tmp.resolve("repo1"));
        Path repo2 = setupGitRepo(tmp.resolve("repo2"));
        Path roster = writeRoster(tmp);
        BufferedReader input = loadFixtureInput();

        var stdoutBuf = new ByteArrayOutputStream();
        var stderrBuf = new ByteArrayOutputStream();
        var stdout = new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8);
        var stderr = new PrintStream(stderrBuf, true, StandardCharsets.UTF_8);

        StubLlmProvider llm = new StubLlmProvider();
        QualityReportCommand cmd =
                new QualityReportCommand(input, stdout, stderr, (name, model, numCtx, verbose) -> llm);

        int exitCode = new CommandLine(cmd)
                .setOut(new java.io.PrintWriter(stdout, true))
                .setErr(new java.io.PrintWriter(stderr, true))
                .execute(
                        "--repo",
                        repo1.toString(),
                        repo2.toString(),
                        "--branch",
                        "feature/test",
                        "--target",
                        "main",
                        "--roster",
                        roster.toString(),
                        "--no-llm");

        assertThat(exitCode).isZero();
        String output = stdoutBuf.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("PROJ-77777");
    }

    @Test
    void missingRefInOneRepo_continuesWithWarning(@TempDir Path tmp) throws Exception {
        Path repo1 = setupGitRepo(tmp.resolve("repo-ok"));
        Path repo2 = setupGitRepoMainOnly(tmp.resolve("repo-missing"));
        Path roster = writeRoster(tmp);
        BufferedReader input = loadFixtureInput();

        var stdoutBuf = new ByteArrayOutputStream();
        var stderrBuf = new ByteArrayOutputStream();
        var stdout = new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8);
        var stderr = new PrintStream(stderrBuf, true, StandardCharsets.UTF_8);

        StubLlmProvider llm = new StubLlmProvider();
        QualityReportCommand cmd =
                new QualityReportCommand(input, stdout, stderr, (name, model, numCtx, verbose) -> llm);

        int exitCode = new CommandLine(cmd)
                .setOut(new java.io.PrintWriter(stdout, true))
                .setErr(new java.io.PrintWriter(stderr, true))
                .execute(
                        "--repo",
                        repo1.toString(),
                        repo2.toString(),
                        "--branch",
                        "feature/test",
                        "--target",
                        "main",
                        "--roster",
                        roster.toString(),
                        "--no-llm");

        String output = stdoutBuf.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("WARNING:");
        assertThat(output).contains("Skipped repo-missing");
    }

    @Test
    void allReposMissingRef_reportsError(@TempDir Path tmp) throws Exception {
        Path repo1 = setupGitRepo(tmp.resolve("bad1"));
        Path repo2 = setupGitRepo(tmp.resolve("bad2"));
        Path roster = writeRoster(tmp);
        BufferedReader input = loadFixtureInput();

        var stdoutBuf = new ByteArrayOutputStream();
        var stderrBuf = new ByteArrayOutputStream();
        var stdout = new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8);
        var stderr = new PrintStream(stderrBuf, true, StandardCharsets.UTF_8);

        StubLlmProvider llm = new StubLlmProvider();
        QualityReportCommand cmd =
                new QualityReportCommand(input, stdout, stderr, (name, model, numCtx, verbose) -> llm);

        int exitCode = new CommandLine(cmd)
                .setOut(new java.io.PrintWriter(stdout, true))
                .setErr(new java.io.PrintWriter(stderr, true))
                .execute(
                        "--repo",
                        repo1.toString(),
                        repo2.toString(),
                        "--branch",
                        "nonexistent",
                        "--target",
                        "also-missing",
                        "--roster",
                        roster.toString(),
                        "--no-llm");

        assertThat(exitCode).isNotZero();
        String errOutput = stderrBuf.toString(StandardCharsets.UTF_8);
        assertThat(errOutput).contains("All 2 repositories failed");
    }

    @Test
    void multiRepo_happyPath_aggregatesCommitsFromBothRepos(@TempDir Path tmp) throws Exception {
        Path repoA = setupGitRepoWithFile(tmp.resolve("backend"), "src/Service.java", "class Service {}");
        Path repoB = setupGitRepoWithFile(tmp.resolve("frontend"), "src/App.tsx", "export default App;");
        Path roster = writeMultiTeamRoster(tmp);
        BufferedReader input = loadFixtureInput();

        var stdoutBuf = new ByteArrayOutputStream();
        var stderrBuf = new ByteArrayOutputStream();
        var stdout = new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8);
        var stderr = new PrintStream(stderrBuf, true, StandardCharsets.UTF_8);

        StubLlmProvider llm = new StubLlmProvider();
        QualityReportCommand cmd =
                new QualityReportCommand(input, stdout, stderr, (name, model, numCtx, verbose) -> llm);

        int exitCode = new CommandLine(cmd)
                .setOut(new java.io.PrintWriter(stdout, true))
                .setErr(new java.io.PrintWriter(stderr, true))
                .execute(
                        "--repo",
                        repoA.toString(),
                        repoB.toString(),
                        "--branch",
                        "feature/test",
                        "--target",
                        "main",
                        "--roster",
                        roster.toString(),
                        "--no-llm");

        assertThat(exitCode).isZero();
        String output = stdoutBuf.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Service.java");
        assertThat(output).contains("App.tsx");
    }

    @Test
    void multiRepo_collidingPaths_bothReposDataPresent(@TempDir Path tmp) throws Exception {
        Path repoA = setupGitRepoWithFile(tmp.resolve("repoA"), "src/Main.java", "class Main { // repoA }");
        Path repoB = setupGitRepoWithFile(tmp.resolve("repoB"), "src/Main.java", "class Main { // repoB }");
        Path roster = writeRoster(tmp);
        BufferedReader input = loadFixtureInput();

        var stdoutBuf = new ByteArrayOutputStream();
        var stderrBuf = new ByteArrayOutputStream();
        var stdout = new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8);
        var stderr = new PrintStream(stderrBuf, true, StandardCharsets.UTF_8);

        StubLlmProvider llm = new StubLlmProvider();
        QualityReportCommand cmd =
                new QualityReportCommand(input, stdout, stderr, (name, model, numCtx, verbose) -> llm);

        int exitCode = new CommandLine(cmd)
                .setOut(new java.io.PrintWriter(stdout, true))
                .setErr(new java.io.PrintWriter(stderr, true))
                .execute(
                        "--repo",
                        repoA.toString(),
                        repoB.toString(),
                        "--branch",
                        "feature/test",
                        "--target",
                        "main",
                        "--roster",
                        roster.toString(),
                        "--no-llm");

        assertThat(exitCode).isZero();
        String output = stdoutBuf.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("repoA");
        assertThat(output).contains("repoB");
    }

    @Test
    void emptyDiff_sameBranchAsTarget_reportsError(@TempDir Path tmp) throws Exception {
        Path repoDir = setupGitRepo(tmp.resolve("empty-diff"));
        Path roster = writeRoster(tmp);
        BufferedReader input = loadFixtureInput();

        var stdoutBuf = new ByteArrayOutputStream();
        var stderrBuf = new ByteArrayOutputStream();
        var stdout = new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8);
        var stderr = new PrintStream(stderrBuf, true, StandardCharsets.UTF_8);

        StubLlmProvider llm = new StubLlmProvider();
        QualityReportCommand cmd =
                new QualityReportCommand(input, stdout, stderr, (name, model, numCtx, verbose) -> llm);

        int exitCode = new CommandLine(cmd)
                .setOut(new java.io.PrintWriter(stdout, true))
                .setErr(new java.io.PrintWriter(stderr, true))
                .execute(
                        "--repo",
                        repoDir.toString(),
                        "--branch",
                        "main",
                        "--target",
                        "main",
                        "--roster",
                        roster.toString(),
                        "--no-llm");

        assertThat(exitCode).isNotZero();
        String errOutput = stderrBuf.toString(StandardCharsets.UTF_8);
        assertThat(errOutput).contains("No commits or changed files");
    }

    @Test
    void aliasRoster_resolvesSecondaryEmail(@TempDir Path tmp) throws Exception {
        Path repoDir = setupGitRepoWithAlias(tmp.resolve("alias-repo"));
        Path roster = writeAliasRoster(tmp);
        BufferedReader input = loadFixtureInput();

        var stdoutBuf = new ByteArrayOutputStream();
        var stderrBuf = new ByteArrayOutputStream();
        var stdout = new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8);
        var stderr = new PrintStream(stderrBuf, true, StandardCharsets.UTF_8);

        StubLlmProvider llm = new StubLlmProvider();
        QualityReportCommand cmd =
                new QualityReportCommand(input, stdout, stderr, (name, model, numCtx, verbose) -> llm);

        int exitCode = new CommandLine(cmd)
                .setOut(new java.io.PrintWriter(stdout, true))
                .setErr(new java.io.PrintWriter(stderr, true))
                .execute(
                        "--repo",
                        repoDir.toString(),
                        "--branch",
                        "feature/test",
                        "--target",
                        "main",
                        "--roster",
                        roster.toString(),
                        "--no-llm");

        assertThat(exitCode).isZero();
        String output = stdoutBuf.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("\"role\" : \"BE\"");
        assertThat(output).doesNotContain("\"role\" : \"unknown\"");
    }

    private static Path setupGitRepo(Path dir) throws Exception {
        Files.createDirectories(dir);
        try (Git git =
                Git.init().setInitialBranch("main").setDirectory(dir.toFile()).call()) {
            Path readme = dir.resolve("README.md");
            Files.writeString(readme, "# Test\n");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("initial commit")
                    .setAuthor("Test Dev", "dev@example.nl")
                    .call();

            git.branchCreate().setName("feature/test").call();
            git.checkout().setName("feature/test").call();

            Path src = dir.resolve("src/Main.java");
            Files.createDirectories(src.getParent());
            Files.writeString(src, "public class Main { /* fix null */ }\n");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("fix: null pointer in DataSlice")
                    .setAuthor("Test Dev", "dev@example.nl")
                    .call();
        }
        return dir;
    }

    private static Path setupGitRepoMainOnly(Path dir) throws Exception {
        Files.createDirectories(dir);
        try (Git git =
                Git.init().setInitialBranch("main").setDirectory(dir.toFile()).call()) {
            Path readme = dir.resolve("README.md");
            Files.writeString(readme, "# Test\n");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("initial commit")
                    .setAuthor("Test Dev", "dev@example.nl")
                    .call();
        }
        return dir;
    }

    private static Path writeRoster(Path dir) throws IOException {
        Path roster = dir.resolve("test-roster.json");
        Files.writeString(
                roster,
                """
                                                {
                                                  "teams": {
                                                    "TestTeam": {
                                                      "members": [
                                                        {"name": "Test Dev", "email": "dev@example.nl", "role": "BE"}
                                                      ]
                                                    }
                                                  }
                                                }
                                                """);
        return roster;
    }

    private static Path setupGitRepoWithFile(Path dir, String filePath, String content) throws Exception {
        Files.createDirectories(dir);
        try (Git git =
                Git.init().setInitialBranch("main").setDirectory(dir.toFile()).call()) {
            Path readme = dir.resolve("README.md");
            Files.writeString(readme, "# Test\n");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("initial commit")
                    .setAuthor("Test Dev", "dev@example.nl")
                    .call();

            git.branchCreate().setName("feature/test").call();
            git.checkout().setName("feature/test").call();

            Path src = dir.resolve(filePath);
            Files.createDirectories(src.getParent());
            Files.writeString(src, content + "\n");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("feat: add " + filePath)
                    .setAuthor("Test Dev", "dev@example.nl")
                    .call();
        }
        return dir;
    }

    private static Path writeMultiTeamRoster(Path dir) throws IOException {
        Path roster = dir.resolve("test-roster.json");
        Files.writeString(
                roster,
                """
                                                {
                                                  "teams": {
                                                    "Backend": {
                                                      "members": [
                                                        {"name": "Test Dev", "email": "dev@example.nl", "role": "BE"}
                                                      ]
                                                    },
                                                    "Frontend": {
                                                      "members": [
                                                        {"name": "Frontend Dev", "email": "frontend@example.nl", "role": "FE"}
                                                      ]
                                                    }
                                                  }
                                                }
                                                """);
        return roster;
    }

    private static Path setupGitRepoWithAlias(Path dir) throws Exception {
        Files.createDirectories(dir);
        try (Git git =
                Git.init().setInitialBranch("main").setDirectory(dir.toFile()).call()) {
            Path readme = dir.resolve("README.md");
            Files.writeString(readme, "# Test\n");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("initial commit")
                    .setAuthor("Test Dev", "dev@example.nl")
                    .call();

            git.branchCreate().setName("feature/test").call();
            git.checkout().setName("feature/test").call();

            Path src = dir.resolve("src/Main.java");
            Files.createDirectories(src.getParent());
            Files.writeString(src, "public class Main { /* fix */ }\n");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setSign(false)
                    .setMessage("fix: null pointer in DataSlice")
                    .setAuthor("Dev Alias", "dev-alias@personal.dev")
                    .call();
        }
        return dir;
    }

    private static Path writeAliasRoster(Path dir) throws IOException {
        Path roster = dir.resolve("test-roster.json");
        Files.writeString(
                roster,
                """
                                                {
                                                  "teams": {
                                                    "TestTeam": {
                                                      "members": [
                                                        {
                                                          "name": "Test Dev",
                                                          "email": "dev@example.nl",
                                                          "emails": ["dev@example.nl", "dev-alias@personal.dev"],
                                                          "role": "BE"
                                                        }
                                                      ]
                                                    }
                                                  }
                                                }
                                                """);
        return roster;
    }

    private static BufferedReader loadFixtureInput() {
        var stream =
                QualityReportCommandIntegrationTest.class.getResourceAsStream("/fixtures/cli/integration-input.txt");
        Objects.requireNonNull(stream, "Missing fixture: /fixtures/cli/integration-input.txt");
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
