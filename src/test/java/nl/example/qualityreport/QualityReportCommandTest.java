package nl.example.qualityreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import nl.example.qualityreport.llm.LlmProvider;
import nl.example.qualityreport.llm.OllamaProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class QualityReportCommandTest {

    @Test
    void helpShowsUsageAndOptions() {
        var result = execute("--help");
        assertThat(result.exitCode).isZero();
        assertThat(result.stdout).contains("quality-report");
        assertThat(result.stdout).contains("--repo");
        assertThat(result.stdout).contains("--branch");
        assertThat(result.stdout).contains("--target");
        assertThat(result.stdout).contains("--roster");
        assertThat(result.stdout).contains("--provider");
        assertThat(result.stdout).contains("--model");
        assertThat(result.stdout).contains("--no-llm");
        assertThat(result.stdout).contains("--verbose");
        assertThat(result.stdout).contains("--num-ctx");
        assertThat(result.stdout).contains("--two-pass");
        assertThat(result.stdout).contains("--output");
        assertThat(result.stdout).contains("--[no-]evidence-first");
        assertThat(result.stdout).contains("--[no-]verify-narrative");
        assertThat(result.stdout).contains("--debug-artifacts");
    }

    @Test
    void versionShowsVersionString() {
        var result = execute("--version");
        assertThat(result.exitCode).isZero();
        assertThat(result.stdout).contains("1.0.0");
    }

    @Test
    void missingRequiredRepoReturnsNonZero() {
        var result = execute("--branch", "feature/x");
        assertThat(result.exitCode).isNotZero();
        assertThat(result.stderr).contains("--repo");
    }

    @Test
    void missingRequiredBranchReturnsNonZero() {
        var result = execute("--repo", "/tmp/fake");
        assertThat(result.exitCode).isNotZero();
        assertThat(result.stderr).contains("--branch");
    }

    @Test
    void defaultTargetIsNull_autoDetected() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b");
        assertThat(cmd.target).isNull();
    }

    @Test
    void defaultRosterIsTeamRosterJson() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b");
        assertThat(cmd.rosterPath).isEqualTo(Path.of("team-roster.json"));
    }

    @Test
    void defaultProviderIsAnthropic() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b");
        assertThat(cmd.provider).isEqualTo("anthropic");
    }

    @Test
    void modelDefaultsToNull() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b");
        assertThat(cmd.model).isNull();
    }

    @Test
    void modelCanBeSetViaOption() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b", "--model", "qwen2.5-coder:3b");
        assertThat(cmd.model).isEqualTo("qwen2.5-coder:3b");
    }

    @Test
    void modelShortFlagWorks() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b", "-m", "qwen2.5-coder:3b");
        assertThat(cmd.model).isEqualTo("qwen2.5-coder:3b");
    }

    @Test
    void noLlmDefaultsToFalse() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b");
        assertThat(cmd.noLlm).isFalse();
    }

    @Test
    void numCtxDefaultsToZero() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b");
        assertThat(cmd.numCtx).isZero();
    }

    @Test
    void numCtxCanBeSet() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b", "--num-ctx", "32768");
        assertThat(cmd.numCtx).isEqualTo(32768);
    }

    @Test
    void verboseDefaultsToFalse() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b");
        assertThat(cmd.verbose).isFalse();
    }

    @Test
    void verboseCanBeEnabled() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b", "--verbose");
        assertThat(cmd.verbose).isTrue();
    }

    @Test
    void verboseShortFlagWorks() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b", "-v");
        assertThat(cmd.verbose).isTrue();
    }

    @Test
    void multipleReposAreParsed() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/a", "/tmp/b", "--branch", "x");
        assertThat(cmd.repos).containsExactly(Path.of("/tmp/a"), Path.of("/tmp/b"));
    }

    @Test
    void evidenceFirstDefaultsToTrue() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b");
        assertThat(cmd.evidenceFirst).isTrue();
    }

    @Test
    void evidenceFirstCanBeDisabled() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b", "--no-evidence-first");
        assertThat(cmd.evidenceFirst).isFalse();
    }

    @Test
    void verifyNarrativeDefaultsToTrue() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b");
        assertThat(cmd.verifyNarrative).isTrue();
    }

    @Test
    void debugArtifactsDefaultsToFalse() {
        QualityReportCommand cmd = new QualityReportCommand();
        new CommandLine(cmd).parseArgs("--repo", "/tmp/r", "--branch", "b");
        assertThat(cmd.debugArtifacts).isFalse();
    }

    @Test
    void createProviderReturnsOllamaForOllamaName() {
        LlmProvider provider = QualityReportCommand.createProvider("ollama", null, 0, false);
        assertThat(provider).isInstanceOf(OllamaProvider.class);
    }

    @Test
    void createProviderIsCaseInsensitive() {
        LlmProvider provider = QualityReportCommand.createProvider("OLLAMA", null, 0, false);
        assertThat(provider).isInstanceOf(OllamaProvider.class);
    }

    @Test
    void createProviderRejectsUnknown() {
        assertThatThrownBy(() -> QualityReportCommand.createProvider("gpt4", null, 0, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown LLM provider")
                .hasMessageContaining("gpt4")
                .hasMessageContaining("anthropic")
                .hasMessageContaining("ollama");
    }

    @Test
    void invalidRosterPathReturnsExitCode1(@TempDir Path tmp) {
        Path missingRoster = tmp.resolve("nonexistent.json");
        var result = executeCommand(
                "--repo",
                tmp.toString(),
                "--branch",
                "main",
                "--target",
                "main",
                "--roster",
                missingRoster.toString(),
                "--no-llm");
        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.stderr).containsIgnoringCase("error");
    }

    @Test
    void invalidProviderReturnsExitCode2(@TempDir Path tmp) throws Exception {
        Path roster = writeMinimalRoster(tmp);
        var result = executeCommand(
                "--repo", tmp.toString(),
                "--branch", "main",
                "--target", "main",
                "--roster", roster.toString(),
                "--provider", "deepseek");
        assertThat(result.exitCode).isEqualTo(2);
        assertThat(result.stderr).contains("Unknown LLM provider");
    }

    private static Path writeMinimalRoster(Path dir) throws Exception {
        Path roster = dir.resolve("roster.json");
        Files.writeString(
                roster,
                """
                {"teams":{"T":{"members":[{"name":"A","email":"a@test.nl","role":"BE"}]}}}
                """);
        return roster;
    }

    private static CapturedResult execute(String... args) {
        var stdoutBuf = new ByteArrayOutputStream();
        var stderrBuf = new ByteArrayOutputStream();
        var stdout = new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8);
        var stderr = new PrintStream(stderrBuf, true, StandardCharsets.UTF_8);

        QualityReportCommand cmd = new QualityReportCommand(
                new BufferedReader(new StringReader("")), stdout, stderr, (name, model, numCtx, verbose) -> {
                    throw new UnsupportedOperationException("no provider in test");
                });

        int exitCode = new CommandLine(cmd)
                .setOut(new java.io.PrintWriter(stdout, true))
                .setErr(new java.io.PrintWriter(stderr, true))
                .execute(args);

        return new CapturedResult(
                exitCode, stdoutBuf.toString(StandardCharsets.UTF_8), stderrBuf.toString(StandardCharsets.UTF_8));
    }

    private static CapturedResult executeCommand(String... args) {
        var stdoutBuf = new ByteArrayOutputStream();
        var stderrBuf = new ByteArrayOutputStream();
        var stdout = new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8);
        var stderr = new PrintStream(stderrBuf, true, StandardCharsets.UTF_8);

        QualityReportCommand cmd = new QualityReportCommand(
                new BufferedReader(new StringReader("")), stdout, stderr, (name, model, numCtx, verbose) -> {
                    throw new IllegalArgumentException(
                            "Unknown LLM provider: '" + name + "'. Supported: anthropic, ollama");
                });

        int exitCode = new CommandLine(cmd)
                .setOut(new java.io.PrintWriter(stdout, true))
                .setErr(new java.io.PrintWriter(stderr, true))
                .execute(args);

        return new CapturedResult(
                exitCode, stdoutBuf.toString(StandardCharsets.UTF_8), stderrBuf.toString(StandardCharsets.UTF_8));
    }

    record CapturedResult(int exitCode, String stdout, String stderr) {}
}
