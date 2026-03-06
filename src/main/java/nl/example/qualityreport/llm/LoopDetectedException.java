package nl.example.qualityreport.llm;

/**
 * Thrown when a repetition loop is detected in streaming LLM output.
 * Contains diagnostic metadata for logging and failure policy decisions.
 */
public class LoopDetectedException extends LlmProviderException {

    private final int tokensGenerated;
    private final String repeatedFragment;

    public LoopDetectedException(String message, int tokensGenerated, String repeatedFragment) {
        super(message);
        this.tokensGenerated = tokensGenerated;
        this.repeatedFragment = repeatedFragment;
    }

    public int tokensGenerated() {
        return tokensGenerated;
    }

    public String repeatedFragment() {
        return repeatedFragment;
    }
}
