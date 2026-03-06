package nl.example.qualityreport.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import java.util.stream.Collectors;

public class AnthropicProvider implements LlmProvider {

    static final String DEFAULT_MODEL = "claude-sonnet-4-5-20250929";
    static final long MAX_TOKENS = 4096L;

    private final AnthropicClient client;
    private final String model;

    public AnthropicProvider() {
        this(AnthropicOkHttpClient.fromEnv(), resolveModel());
    }

    public AnthropicProvider(String model) {
        this(AnthropicOkHttpClient.fromEnv(), model);
    }

    AnthropicProvider(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(systemPrompt)
                .addUserMessage(userMessage)
                .build();

        Message response = client.messages().create(params);

        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());

        if (text.isEmpty()) {
            throw new LlmProviderException(
                    "Anthropic returned no text content (stop_reason: " + response.stopReason() + ")");
        }

        return text;
    }

    private static String resolveModel() {
        String env = System.getenv("ANTHROPIC_MODEL");
        return (env != null && !env.isBlank()) ? env : DEFAULT_MODEL;
    }
}
