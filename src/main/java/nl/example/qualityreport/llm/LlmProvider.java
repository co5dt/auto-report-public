package nl.example.qualityreport.llm;

/**
 * Contract: send a system prompt + user message, get a text response.
 * Implementations handle authentication, SDK details, and model selection.
 */
public interface LlmProvider {

    String chat(String systemPrompt, String userMessage);

    /**
     * Returns the model name this provider is configured to use.
     * Used for diagnostic output.
     */
    default String modelName() {
        return "unknown";
    }
}
