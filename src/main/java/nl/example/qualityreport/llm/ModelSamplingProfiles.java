package nl.example.qualityreport.llm;

import java.util.Map;

/**
 * Resolves sampling parameters for a given Ollama model name.
 *
 * <p>Precedence (highest wins):
 * <ol>
 *   <li>Explicit runtime overrides (env vars / CLI flags)</li>
 *   <li>Model-specific profile from this registry</li>
 *   <li>{@link OllamaSamplingConfig#GLOBAL_DEFAULTS}</li>
 * </ol>
 */
public final class ModelSamplingProfiles {

    private ModelSamplingProfiles() {}

    /**
     * Model-specific profiles. Keys are matched against the model name using
     * prefix matching (e.g. "deepcoder" matches "deepcoder:1.5b").
     * More specific keys (with tag) are checked first.
     */
    static final Map<String, OllamaSamplingConfig> PROFILES = Map.of(
            "deepcoder:1.5b", new OllamaSamplingConfig(16384, 1.3, 128, 0.3, 30, 0.85),
            "deepcoder", new OllamaSamplingConfig(16384, 1.3, 128, 0.3, 30, 0.85),
            "qwen2.5-coder:3b", new OllamaSamplingConfig(16384, 1.15, 80, 0.65, 40, 0.92),
            "qwen3.5:0.8b", new OllamaSamplingConfig(16384, 1.2, 96, 0.4, 35, 0.88),
            "qwen3.5:2b", new OllamaSamplingConfig(16384, 1.15, 80, 0.5, 38, 0.9));

    /**
     * Resolve the effective sampling config for the given model.
     *
     * @param model the Ollama model name (e.g. "qwen2.5-coder:3b")
     * @return the matching profile or {@link OllamaSamplingConfig#GLOBAL_DEFAULTS}
     */
    public static OllamaSamplingConfig forModel(String model) {
        if (model == null) return OllamaSamplingConfig.GLOBAL_DEFAULTS;

        String normalized = model.strip().toLowerCase();

        // Exact match first
        OllamaSamplingConfig exact = PROFILES.get(normalized);
        if (exact != null) return exact;

        // Prefix match (e.g. "deepcoder" matches "deepcoder:3b")
        for (var entry : PROFILES.entrySet()) {
            if (normalized.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        return OllamaSamplingConfig.GLOBAL_DEFAULTS;
    }
}
