package nl.example.qualityreport.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ToolRequestTest {

    @Nested
    class Detection {

        @Test
        void detectsToolRequestJson() {
            String json =
                    """
                    {"tool_request":{"tool":"read_file","ref":"Foo.java","revision":"branch"}}""";
            assertThat(ToolRequest.isToolRequest(json)).isTrue();
        }

        @Test
        void rejectsVoteJson() {
            String json = """
                    {"vote":"MEDIUM","confidence":0.7,"reasoning":"looks ok"}""";
            assertThat(ToolRequest.isToolRequest(json)).isFalse();
        }

        @Test
        void rejectsMalformedJson() {
            assertThat(ToolRequest.isToolRequest("not json")).isFalse();
        }

        @Test
        void rejectsNull() {
            assertThat(ToolRequest.isToolRequest(null)).isFalse();
        }
    }

    @Nested
    class Parsing {

        @Test
        void parsesValidToolRequest() {
            String json =
                    """
                    {"tool_request":{"tool":"read_file","ref":"src/main/java/Foo.java","revision":"branch"}}""";

            ToolRequest req = ToolRequest.parse("diff-analyst", json);

            assertThat(req.tool()).isEqualTo("read_file");
            assertThat(req.ref()).isEqualTo("src/main/java/Foo.java");
            assertThat(req.revision()).isEqualTo("branch");
        }

        @Test
        void parsesWithCodeFences() {
            String json =
                    """
                    ```json
                    {"tool_request":{"tool":"read_file","ref":"PlCache.java","revision":"target"}}
                    ```""";

            ToolRequest req = ToolRequest.parse("diff-analyst", json);

            assertThat(req.tool()).isEqualTo("read_file");
            assertThat(req.ref()).isEqualTo("PlCache.java");
            assertThat(req.revision()).isEqualTo("target");
        }

        @Test
        void stripsWhitespaceFromRefAndRevision() {
            String json =
                    """
                    {"tool_request":{"tool":"read_file","ref":"  Foo.java  ","revision":" branch "}}""";

            ToolRequest req = ToolRequest.parse("diff-analyst", json);

            assertThat(req.ref()).isEqualTo("Foo.java");
            assertThat(req.revision()).isEqualTo("branch");
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsUnknownTool() {
            String json =
                    """
                    {"tool_request":{"tool":"execute_command","ref":"rm -rf /","revision":"branch"}}""";

            assertThatThrownBy(() -> ToolRequest.parse("diff-analyst", json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown tool")
                    .hasMessageContaining("execute_command");
        }

        @Test
        void rejectsInvalidRevision() {
            String json =
                    """
                    {"tool_request":{"tool":"read_file","ref":"Foo.java","revision":"HEAD~5"}}""";

            assertThatThrownBy(() -> ToolRequest.parse("diff-analyst", json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid revision")
                    .hasMessageContaining("HEAD~5");
        }

        @Test
        void rejectsMissingTool() {
            String json = """
                    {"tool_request":{"ref":"Foo.java","revision":"branch"}}""";

            assertThatThrownBy(() -> ToolRequest.parse("diff-analyst", json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Missing field 'tool'");
        }

        @Test
        void rejectsMissingRef() {
            String json = """
                    {"tool_request":{"tool":"read_file","revision":"branch"}}""";

            assertThatThrownBy(() -> ToolRequest.parse("diff-analyst", json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Missing field 'ref'");
        }

        @Test
        void rejectsMissingRevision() {
            String json = """
                    {"tool_request":{"tool":"read_file","ref":"Foo.java"}}""";

            assertThatThrownBy(() -> ToolRequest.parse("diff-analyst", json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Missing field 'revision'");
        }

        @Test
        void rejectsBlankRef() {
            String json =
                    """
                    {"tool_request":{"tool":"read_file","ref":"  ","revision":"branch"}}""";

            assertThatThrownBy(() -> ToolRequest.parse("diff-analyst", json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        void rejectsTooLongRef() {
            String longRef = "A".repeat(ToolRequest.MAX_REF_LENGTH + 1);
            String json =
                    """
                    {"tool_request":{"tool":"read_file","ref":"%s","revision":"branch"}}"""
                            .formatted(longRef);

            assertThatThrownBy(() -> ToolRequest.parse("diff-analyst", json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Ref too long");
        }

        @Test
        void rejectsEmptyResponse() {
            assertThatThrownBy(() -> ToolRequest.parse("diff-analyst", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Empty");
        }

        @Test
        void rejectsNullResponse() {
            assertThatThrownBy(() -> ToolRequest.parse("diff-analyst", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Empty");
        }

        @Test
        void rejectsMalformedJson() {
            assertThatThrownBy(() -> ToolRequest.parse("diff-analyst", "not json"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid JSON");
        }

        @Test
        void rejectsToolRequestNotAnObject() {
            String json = """
                    {"tool_request":"read_file"}""";

            assertThatThrownBy(() -> ToolRequest.parse("diff-analyst", json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Missing or invalid 'tool_request'");
        }
    }
}
