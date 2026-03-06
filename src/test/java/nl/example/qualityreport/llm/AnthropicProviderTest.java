package nl.example.qualityreport.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.core.ClientOptions;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.services.blocking.BetaService;
import com.anthropic.services.blocking.CompletionService;
import com.anthropic.services.blocking.MessageService;
import com.anthropic.services.blocking.ModelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AnthropicProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void extractsTextFromSingleTextBlock() {
        var client = fakeClient(messageWithText("Hello from Claude"));
        var provider = new AnthropicProvider(client, "test-model");

        String result = provider.chat("You are helpful.", "Hi");

        assertThat(result).isEqualTo("Hello from Claude");
    }

    @Test
    void concatenatesMultipleTextBlocks() {
        var client = fakeClient(messageWithTexts("Part one. ", "Part two."));
        var provider = new AnthropicProvider(client, "test-model");

        String result = provider.chat("sys", "msg");

        assertThat(result).isEqualTo("Part one. Part two.");
    }

    @Test
    void throwsOnEmptyContent() {
        var client = fakeClient(messageWithTexts());
        var provider = new AnthropicProvider(client, "test-model");

        assertThatThrownBy(() -> provider.chat("sys", "msg"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("no text content");
    }

    @Test
    void throwsOnApiException() {
        AnthropicClient client = throwingClient(new RuntimeException("API down"));
        var provider = new AnthropicProvider(client, "test-model");

        assertThatThrownBy(() -> provider.chat("sys", "msg")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void usesDefaultModelWhenEnvNotSet() {
        assertThat(AnthropicProvider.DEFAULT_MODEL).isEqualTo("claude-sonnet-4-5-20250929");
    }

    @Test
    void capturesRequestParams() {
        var captured = new ArrayList<MessageCreateParams>();
        AnthropicClient client = capturingClient(captured, messageWithText("ok"));
        var provider = new AnthropicProvider(client, "my-model");

        provider.chat("system prompt text", "user message text");

        assertThat(captured).hasSize(1);
        MessageCreateParams params = captured.get(0);
        assertThat(params.model().asString()).isEqualTo("my-model");
        assertThat(params.maxTokens()).isEqualTo(AnthropicProvider.MAX_TOKENS);
    }

    // --- test helpers: build Message from JSON to bypass strict builder validation ---

    private static Message messageWithText(String text) {
        return messageWithTexts(text);
    }

    private static Message messageWithTexts(String... texts) {
        try {
            List<Map<String, String>> content = Arrays.stream(texts)
                    .map(t -> Map.of("type", "text", "text", t))
                    .collect(Collectors.toList());

            Map<String, Object> json = Map.of(
                    "id", "msg_test",
                    "type", "message",
                    "role", "assistant",
                    "model", "claude-sonnet-4-5-20250929",
                    "content", content,
                    "stop_reason", "end_turn",
                    "usage", Map.of("input_tokens", 10, "output_tokens", 20));

            String jsonStr = MAPPER.writeValueAsString(json);
            return MAPPER.readValue(jsonStr, Message.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build test Message", e);
        }
    }

    private static AnthropicClient fakeClient(Message response) {
        return new StubAnthropicClient(params -> response);
    }

    private static AnthropicClient throwingClient(RuntimeException ex) {
        return new StubAnthropicClient(params -> {
            throw ex;
        });
    }

    private static AnthropicClient capturingClient(List<MessageCreateParams> captured, Message response) {
        return new StubAnthropicClient(params -> {
            captured.add(params);
            return response;
        });
    }

    @FunctionalInterface
    private interface MessageHandler {
        Message handle(MessageCreateParams params);
    }

    private static class StubAnthropicClient implements AnthropicClient {
        private final MessageHandler handler;

        StubAnthropicClient(MessageHandler handler) {
            this.handler = handler;
        }

        @Override
        public MessageService messages() {
            return new StubMessageService(handler);
        }

        @Override
        public AnthropicClientAsync async() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnthropicClient.WithRawResponse withRawResponse() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnthropicClient withOptions(Consumer<ClientOptions.Builder> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionService completions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelService models() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BetaService beta() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {}
    }

    private static class StubMessageService implements MessageService {
        private final MessageHandler handler;

        StubMessageService(MessageHandler handler) {
            this.handler = handler;
        }

        @Override
        public Message create(MessageCreateParams params, com.anthropic.core.RequestOptions options) {
            return handler.handle(params);
        }

        @Override
        public com.anthropic.core.http.StreamResponse<com.anthropic.models.messages.RawMessageStreamEvent>
                createStreaming(MessageCreateParams p, com.anthropic.core.RequestOptions o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.anthropic.models.messages.MessageTokensCount countTokens(
                com.anthropic.models.messages.MessageCountTokensParams p, com.anthropic.core.RequestOptions o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageService.WithRawResponse withRawResponse() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageService withOptions(Consumer<ClientOptions.Builder> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.anthropic.services.blocking.messages.BatchService batches() {
            throw new UnsupportedOperationException();
        }
    }
}
