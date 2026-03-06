package nl.example.qualityreport.llm;

/**
 * Thrown when an LLM provider encounters an error that the caller should handle.
 * Wraps provider-specific failures (API errors, timeouts, malformed responses)
 * into a single actionable exception type.
 */
public class LlmProviderException extends RuntimeException {

    public LlmProviderException(String message) {
        super(message);
    }

    public LlmProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
