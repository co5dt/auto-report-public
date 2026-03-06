package nl.example.qualityreport.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class StubLlmProviderTest {

    @Test
    void returnsCannedResponsesInFifoOrder() {
        var stub = new StubLlmProvider("first", "second", "third");

        assertThat(stub.chat("sys", "msg1")).isEqualTo("first");
        assertThat(stub.chat("sys", "msg2")).isEqualTo("second");
        assertThat(stub.chat("sys", "msg3")).isEqualTo("third");
    }

    @Test
    void throwsWhenExhausted() {
        var stub = new StubLlmProvider("only-one");
        stub.chat("sys", "msg");

        assertThatThrownBy(() -> stub.chat("sys", "msg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exhausted")
                .hasMessageContaining("call #2");
    }

    @Test
    void tracksCallCount() {
        var stub = new StubLlmProvider("a", "b");

        assertThat(stub.getCallCount()).isZero();
        stub.chat("sys", "msg");
        assertThat(stub.getCallCount()).isEqualTo(1);
        stub.chat("sys", "msg");
        assertThat(stub.getCallCount()).isEqualTo(2);
    }

    @Test
    void reportsExhaustedState() {
        var stub = new StubLlmProvider("a");

        assertThat(stub.isExhausted()).isFalse();
        stub.chat("sys", "msg");
        assertThat(stub.isExhausted()).isTrue();
    }

    @Test
    void acceptsListConstructor() {
        var stub = new StubLlmProvider(List.of("x", "y"));

        assertThat(stub.chat("sys", "msg")).isEqualTo("x");
        assertThat(stub.chat("sys", "msg")).isEqualTo("y");
        assertThat(stub.isExhausted()).isTrue();
    }

    @Test
    void implementsLlmProviderContract() {
        LlmProvider provider = new StubLlmProvider("response");

        assertThat(provider.chat("system prompt", "user message")).isEqualTo("response");
    }

    @Test
    void ignoresPromptContent() {
        var stub = new StubLlmProvider("fixed");

        assertThat(stub.chat("any system prompt", "any user message")).isEqualTo("fixed");
    }
}
