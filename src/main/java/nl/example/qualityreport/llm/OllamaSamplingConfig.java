package nl.example.qualityreport.llm;

/**
 * Immutable holder for Ollama sampling parameters.
 * Used by {@link ModelSamplingProfiles} to provide per-model defaults.
 */
public record OllamaSamplingConfig(
        int numCtx, double repeatPenalty, int repeatLastN, double temperature, int topK, double topP) {

    /** Global defaults — used when no model-specific profile matches. */
    public static final OllamaSamplingConfig GLOBAL_DEFAULTS = new OllamaSamplingConfig(32768, 1.1, 64, 0.7, 40, 0.9);

    /**
     * Returns a new config with any non-null/non-zero overrides applied on top of this one.
     * Zero/negative numeric values mean "keep existing".
     */
    public OllamaSamplingConfig withOverrides(
            int numCtxOverride,
            double repeatPenaltyOverride,
            int repeatLastNOverride,
            double temperatureOverride,
            int topKOverride,
            double topPOverride) {
        return new OllamaSamplingConfig(
                numCtxOverride > 0 ? numCtxOverride : this.numCtx,
                repeatPenaltyOverride > 0 ? repeatPenaltyOverride : this.repeatPenalty,
                repeatLastNOverride > 0 ? repeatLastNOverride : this.repeatLastN,
                temperatureOverride > 0 ? temperatureOverride : this.temperature,
                topKOverride > 0 ? topKOverride : this.topK,
                topPOverride > 0 ? topPOverride : this.topP);
    }
}
