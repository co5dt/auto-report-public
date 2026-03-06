package nl.example.qualityreport.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import nl.example.qualityreport.context.ContextBuilder;
import nl.example.qualityreport.git.CodeReferenceResolver;
import nl.example.qualityreport.git.GitFileContentProvider;
import nl.example.qualityreport.llm.LlmProviderException;
import nl.example.qualityreport.llm.RecordingLlmProvider;
import nl.example.qualityreport.model.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ToolAwareDeliberationTest {

    @Nested
    class ToolRequestThenVote {

        @Test
        void toolRequest_thenSecondPassVote_succeeds() throws IOException {
            List<String> responses = loadFixtureLines("tool-request-then-vote.jsonl");
            var llm = new RecordingLlmProvider(responses);

            var resolver =
                    new CodeReferenceResolver(List.of("src/main/java/Foo.java"), List.of("src/main/java/Foo.java"));
            var contentProvider = new StubContentProvider("public class Foo { void run() {} }");
            var executor =
                    new ToolExecutor(contentProvider, resolver, List.of(Path.of("/fake/repo")), "feature", "main");

            var deliberation =
                    new AgentDeliberation(llm, new ContextBuilder(), name -> "prompt for " + name, 3, true, executor);

            RiskAssessment result = deliberation.assess(changeData(), jiraData());

            assertThat(result.finalRisk()).isEqualTo(RiskLevel.MEDIUM);
            assertThat(result.roundReached()).isEqualTo(1);
            assertThat(result.consensusType()).isEqualTo(RiskAssessment.ConsensusType.UNANIMOUS);
            // 4 LLM calls: tool_request + re-call for diff-analyst, + 1 each for other agents
            assertThat(llm.callCount()).isEqualTo(4);
            // Second call to diff-analyst should include tool_result
            assertThat(llm.userMessages().get(1)).contains("<tool_result");
            assertThat(llm.userMessages().get(1)).contains("public class Foo");
        }
    }

    @Nested
    class ToolLoopDisabled {

        @Test
        void toolLoopDisabled_ignoresToolProtocol() {
            String toolReq =
                    """
                    {"tool_request":{"tool":"read_file","ref":"Foo.java","revision":"branch"}}""";
            var llm = new RecordingLlmProvider(List.of(toolReq, toolReq));

            var deliberation =
                    new AgentDeliberation(llm, new ContextBuilder(), name -> "prompt for " + name, 1, false, null);

            assertThatThrownBy(() -> deliberation.assess(changeData(), jiraData()))
                    .isInstanceOf(LlmProviderException.class)
                    .hasMessageContaining("Failed to parse vote");
        }
    }

    @Nested
    class ToolRequestValidation {

        @Test
        void toolRequestPathOutsideChangedFiles_resolvesViaTracked() throws IOException {
            String toolReq =
                    """
                    {"tool_request":{"tool":"read_file","ref":"Bar.java","revision":"branch"}}""";
            String vote =
                    """
                    {"vote":"LOW","confidence":0.9,"reasoning":"after reviewing Bar, low risk"}""";
            var llm = new RecordingLlmProvider(List.of(toolReq, vote));

            var resolver = new CodeReferenceResolver(List.of("src/main/java/Bar.java"), List.of());
            var contentProvider = new StubContentProvider("class Bar {}");
            var executor = new ToolExecutor(contentProvider, resolver, List.of(Path.of("/fake")), "feature", "main");

            var deliberation =
                    new AgentDeliberation(llm, new ContextBuilder(), name -> "prompt for " + name, 1, true, executor);

            RiskAssessment result = deliberation.assess(changeData(), jiraData());
            assertThat(result.finalRisk()).isEqualTo(RiskLevel.LOW);
            assertThat(llm.userMessages().get(1)).contains("class Bar");
        }

        @Test
        void toolRequestWithClassName_resolvesToCanonicalPath() throws IOException {
            String toolReq =
                    """
                    {"tool_request":{"tool":"read_file","ref":"Foo","revision":"branch"}}""";
            String vote =
                    """
                    {"vote":"HIGH","confidence":0.95,"reasoning":"critical method found"}""";
            var llm = new RecordingLlmProvider(List.of(toolReq, vote));

            var resolver =
                    new CodeReferenceResolver(List.of("src/main/java/Foo.java"), List.of("src/main/java/Foo.java"));
            var contentProvider = new StubContentProvider("class Foo { void critical() {} }");
            var executor = new ToolExecutor(contentProvider, resolver, List.of(Path.of("/fake")), "feature", "main");

            var deliberation =
                    new AgentDeliberation(llm, new ContextBuilder(), name -> "prompt for " + name, 1, true, executor);

            RiskAssessment result = deliberation.assess(changeData(), jiraData());
            assertThat(result.finalRisk()).isEqualTo(RiskLevel.HIGH);
            assertThat(llm.userMessages().get(1)).contains("resolved_path=\"src/main/java/Foo.java\"");
        }

        @Test
        void toolRequestWithAmbiguousClassName_returnsAmbiguousResult() throws IOException {
            String toolReq =
                    """
                    {"tool_request":{"tool":"read_file","ref":"Foo","revision":"branch"}}""";
            String vote =
                    """
                    {"vote":"MEDIUM","confidence":0.6,"reasoning":"ambiguous, assuming medium"}""";
            var llm = new RecordingLlmProvider(List.of(toolReq, vote));

            var resolver =
                    new CodeReferenceResolver(List.of("src/main/java/Foo.java", "src/test/java/Foo.java"), List.of());
            var contentProvider = new StubContentProvider("unused");
            var executor = new ToolExecutor(contentProvider, resolver, List.of(Path.of("/fake")), "feature", "main");

            var deliberation =
                    new AgentDeliberation(llm, new ContextBuilder(), name -> "prompt for " + name, 1, true, executor);

            RiskAssessment result = deliberation.assess(changeData(), jiraData());
            assertThat(result.finalRisk()).isEqualTo(RiskLevel.MEDIUM);
            assertThat(llm.userMessages().get(1)).contains("status=\"ambiguous\"");
            assertThat(llm.userMessages().get(1)).contains("src/main/java/Foo.java");
            assertThat(llm.userMessages().get(1)).contains("src/test/java/Foo.java");
        }
    }

    @Nested
    class ToolResultTruncation {

        @Test
        void toolResultTruncated_markerPreserved() throws IOException {
            String toolReq =
                    """
                    {"tool_request":{"tool":"read_file","ref":"Foo.java","revision":"branch"}}""";
            String vote =
                    """
                    {"vote":"LOW","confidence":0.7,"reasoning":"seems fine despite truncation"}""";
            var llm = new RecordingLlmProvider(List.of(toolReq, vote));

            String largeContent = "X".repeat(GitFileContentProvider.MAX_FILE_SIZE + 1000);
            var resolver = new CodeReferenceResolver(List.of("Foo.java"), List.of("Foo.java"));
            var contentProvider = new StubContentProvider(largeContent);
            var executor = new ToolExecutor(contentProvider, resolver, List.of(Path.of("/fake")), "feature", "main");

            var deliberation =
                    new AgentDeliberation(llm, new ContextBuilder(), name -> "prompt for " + name, 1, true, executor);

            RiskAssessment result = deliberation.assess(changeData(), jiraData());
            assertThat(result.finalRisk()).isEqualTo(RiskLevel.LOW);
        }
    }

    @Nested
    class NoToolRequest {

        @Test
        void directVote_noToolLoop_worksNormally() {
            String vote = """
                    {"vote":"LOW","confidence":0.9,"reasoning":"simple change"}""";
            var llm = new RecordingLlmProvider(List.of(vote));

            var deliberation =
                    new AgentDeliberation(llm, new ContextBuilder(), name -> "prompt for " + name, 1, true, null);

            RiskAssessment result = deliberation.assess(changeData(), jiraData());
            assertThat(result.finalRisk()).isEqualTo(RiskLevel.LOW);
            assertThat(llm.callCount()).isEqualTo(1);
        }
    }

    @Nested
    class CrossRepoToolResolution {

        @Test
        void collidingPathsAcrossRepos_returnsAmbiguousWithRepoLabels() throws IOException {
            String toolReq =
                    """
                    {"tool_request":{"tool":"read_file","ref":"Foo","revision":"branch"}}""";
            String vote =
                    """
                    {"vote":"MEDIUM","confidence":0.7,"reasoning":"ambiguous cross-repo ref"}""";
            var llm = new RecordingLlmProvider(List.of(toolReq, vote));

            var tracked = List.of(
                    new RepoFile("repoA", "src/main/java/Foo.java"), new RepoFile("repoB", "src/main/java/Foo.java"));
            var resolver = new CodeReferenceResolver(tracked, List.of(), true);
            var contentProvider = new StubContentProvider("class Foo {}");
            var executor = new ToolExecutor(
                    contentProvider,
                    resolver,
                    List.of(Path.of("/fake/repoA"), Path.of("/fake/repoB")),
                    "feature",
                    "main");

            var deliberation =
                    new AgentDeliberation(llm, new ContextBuilder(), name -> "prompt for " + name, 1, true, executor);

            RiskAssessment result = deliberation.assess(changeData(), jiraData());
            assertThat(result.finalRisk()).isEqualTo(RiskLevel.MEDIUM);
            assertThat(llm.userMessages().get(1)).contains("status=\"ambiguous\"");
            assertThat(llm.userMessages().get(1)).contains("[repoA]");
            assertThat(llm.userMessages().get(1)).contains("[repoB]");
        }
    }

    // --- test helpers ---

    private static ChangeData changeData() {
        CommitInfo commit = new CommitInfo(
                "abc1234",
                "Alice",
                "alice@example.nl",
                "BE",
                "Team Alpha",
                "feat: add validation",
                Instant.parse("2026-01-10T10:00:00Z"),
                2,
                30,
                10);

        return ChangeData.from(
                List.of(commit),
                "diff --git a/Foo.java b/Foo.java\n--- a/Foo.java\n+++ b/Foo.java\n@@ -1 +1,2 @@\n-old\n+new\n",
                List.of("src/main/java/Foo.java"));
    }

    private static JiraData jiraData() {
        return new JiraData(
                List.of("PROJ-12345"),
                "Improve validation",
                "handles null payloads",
                new JiraData.Impact(Map.of()),
                new JiraData.TestEvidence("tested", true, "22 passed", "72%"),
                JiraData.Deployment.defaults(),
                true,
                "1.2.3");
    }

    private static List<String> loadFixtureLines(String file) throws IOException {
        return Files.readAllLines(Path.of("src/test/resources/fixtures/analysis/" + file)).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private static final class StubContentProvider extends GitFileContentProvider {
        private final String content;

        StubContentProvider(String content) {
            this.content = content;
        }

        @Override
        public Optional<String> readFileAtRevision(Path repoPath, String filePath, String revision) {
            return Optional.of(content);
        }

        @Override
        public List<String> listTrackedFiles(Path repoPath, String revision) {
            return List.of();
        }
    }
}
