package nl.example.qualityreport.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

/**
 * Resolves application settings with precedence:
 * environment variable > application.properties > code default.
 */
public class AppConfig {

    static final int DEFAULT_AGENT_COUNT = 3;
    static final int MIN_AGENT_COUNT = 1;
    static final int MAX_AGENT_COUNT = 3;
    static final int DEFAULT_REPAIR_MAX_RETRIES = 1;
    static final int MIN_REPAIR_RETRIES = 0;
    static final int MAX_REPAIR_RETRIES = 3;

    static final String ENV_AGENT_COUNT = "CONSENSUS_AGENT_COUNT";
    static final String PROP_AGENT_COUNT = "consensus.agent.count";

    static final String ENV_TOOL_LOOP = "TOOL_LOOP_ENABLED";
    static final String PROP_TOOL_LOOP = "tool.loop.enabled";

    static final String ENV_EVIDENCE_FIRST = "EVIDENCE_FIRST_ENABLED";
    static final String PROP_EVIDENCE_FIRST = "evidence.first.enabled";

    static final String ENV_NARRATIVE_VERIFY = "NARRATIVE_VERIFY_ENABLED";
    static final String PROP_NARRATIVE_VERIFY = "narrative.verify.enabled";

    static final String ENV_REPAIR_MAX_RETRIES = "NARRATIVE_REPAIR_MAX_RETRIES";
    static final String PROP_REPAIR_MAX_RETRIES = "narrative.repair.max-retries";

    static final String ENV_DEBUG_ARTIFACTS = "NARRATIVE_DEBUG_ARTIFACTS_ENABLED";
    static final String PROP_DEBUG_ARTIFACTS = "narrative.debug.artifacts.enabled";

    static final String ENV_DOMAIN_KEYWORDS_PATH = "DOMAIN_KEYWORDS_PATH";
    static final String PROP_DOMAIN_KEYWORDS_PATH = "domain.keywords.path";

    static final String ENV_E2E_RESULTS_PERSIST = "E2E_RESULTS_PERSIST";
    static final String PROP_E2E_RESULTS_PERSIST = "e2e.results.persist";

    private static final String PROPERTIES_RESOURCE = "/application.properties";

    private final int agentCount;
    private final boolean toolLoopEnabled;
    private final boolean evidenceFirstEnabled;
    private final boolean narrativeVerifyEnabled;
    private final int repairMaxRetries;
    private final boolean debugArtifactsEnabled;
    private final String domainKeywordsPath;
    private final boolean e2eResultsPersist;

    public AppConfig() {
        this(System.getenv(), loadProperties());
    }

    AppConfig(Map<String, String> env, Properties properties) {
        this.agentCount = resolveAgentCount(env, properties);
        this.toolLoopEnabled = resolveToolLoopEnabled(env, properties);
        this.evidenceFirstEnabled = resolveBool(env, properties, ENV_EVIDENCE_FIRST, PROP_EVIDENCE_FIRST, true);
        this.narrativeVerifyEnabled = resolveBool(env, properties, ENV_NARRATIVE_VERIFY, PROP_NARRATIVE_VERIFY, true);
        this.repairMaxRetries = resolveRepairMaxRetries(env, properties);
        this.debugArtifactsEnabled = resolveBool(env, properties, ENV_DEBUG_ARTIFACTS, PROP_DEBUG_ARTIFACTS, false);
        this.domainKeywordsPath =
                resolveString(env, properties, ENV_DOMAIN_KEYWORDS_PATH, PROP_DOMAIN_KEYWORDS_PATH, null);
        this.e2eResultsPersist = resolveBool(env, properties, ENV_E2E_RESULTS_PERSIST, PROP_E2E_RESULTS_PERSIST, true);
    }

    private AppConfig(
            int agentCount,
            boolean toolLoopEnabled,
            boolean evidenceFirstEnabled,
            boolean narrativeVerifyEnabled,
            int repairMaxRetries,
            boolean debugArtifactsEnabled,
            String domainKeywordsPath) {
        this.agentCount = agentCount;
        this.toolLoopEnabled = toolLoopEnabled;
        this.evidenceFirstEnabled = evidenceFirstEnabled;
        this.narrativeVerifyEnabled = narrativeVerifyEnabled;
        this.repairMaxRetries = repairMaxRetries;
        this.debugArtifactsEnabled = debugArtifactsEnabled;
        this.domainKeywordsPath = domainKeywordsPath;
        this.e2eResultsPersist = true;
    }

    /**
     * Returns a copy with CLI-level overrides applied (highest precedence).
     * Pass {@code null} for any parameter to keep the resolved value.
     */
    public AppConfig withCliOverrides(Boolean evidenceFirst, Boolean verifyNarrative, Boolean debugArtifacts) {
        return new AppConfig(
                this.agentCount,
                this.toolLoopEnabled,
                evidenceFirst != null ? evidenceFirst : this.evidenceFirstEnabled,
                verifyNarrative != null ? verifyNarrative : this.narrativeVerifyEnabled,
                this.repairMaxRetries,
                debugArtifacts != null ? debugArtifacts : this.debugArtifactsEnabled,
                this.domainKeywordsPath);
    }

    public int agentCount() {
        return agentCount;
    }

    public boolean toolLoopEnabled() {
        return toolLoopEnabled;
    }

    public boolean evidenceFirstEnabled() {
        return evidenceFirstEnabled;
    }

    public boolean narrativeVerifyEnabled() {
        return narrativeVerifyEnabled;
    }

    public int repairMaxRetries() {
        return repairMaxRetries;
    }

    public boolean debugArtifactsEnabled() {
        return debugArtifactsEnabled;
    }

    /**
     * Returns an optional filesystem path that overrides the default
     * classpath resource ({@code /domain-keywords.json}).
     * Returns null when no override is configured.
     */
    public String domainKeywordsPath() {
        return domainKeywordsPath;
    }

    public boolean e2eResultsPersist() {
        return e2eResultsPersist;
    }

    private static int resolveAgentCount(Map<String, String> env, Properties properties) {
        String envValue = env.get(ENV_AGENT_COUNT);
        if (envValue != null && !envValue.isBlank()) {
            return parseAndValidate(envValue, ENV_AGENT_COUNT);
        }

        String propValue = properties.getProperty(PROP_AGENT_COUNT);
        if (propValue != null && !propValue.isBlank()) {
            return parseAndValidate(propValue.strip(), PROP_AGENT_COUNT);
        }

        return DEFAULT_AGENT_COUNT;
    }

    private static int parseAndValidate(String raw, String source) {
        int value;
        try {
            value = Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(source + " must be a number between " + MIN_AGENT_COUNT + " and "
                    + MAX_AGENT_COUNT + ", got: '" + raw + "'");
        }
        if (value < MIN_AGENT_COUNT || value > MAX_AGENT_COUNT) {
            throw new IllegalArgumentException(
                    source + " must be between " + MIN_AGENT_COUNT + " and " + MAX_AGENT_COUNT + ", got: " + value);
        }
        return value;
    }

    private static boolean resolveToolLoopEnabled(Map<String, String> env, Properties properties) {
        return resolveBool(env, properties, ENV_TOOL_LOOP, PROP_TOOL_LOOP, false);
    }

    private static int resolveRepairMaxRetries(Map<String, String> env, Properties properties) {
        String envValue = env.get(ENV_REPAIR_MAX_RETRIES);
        if (envValue != null && !envValue.isBlank()) {
            return parseAndValidateRetries(envValue, ENV_REPAIR_MAX_RETRIES);
        }
        String propValue = properties.getProperty(PROP_REPAIR_MAX_RETRIES);
        if (propValue != null && !propValue.isBlank()) {
            return parseAndValidateRetries(propValue.strip(), PROP_REPAIR_MAX_RETRIES);
        }
        return DEFAULT_REPAIR_MAX_RETRIES;
    }

    private static int parseAndValidateRetries(String raw, String source) {
        int value;
        try {
            value = Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(source + " must be a number between " + MIN_REPAIR_RETRIES + " and "
                    + MAX_REPAIR_RETRIES + ", got: '" + raw + "'");
        }
        if (value < MIN_REPAIR_RETRIES || value > MAX_REPAIR_RETRIES) {
            throw new IllegalArgumentException(source + " must be between " + MIN_REPAIR_RETRIES + " and "
                    + MAX_REPAIR_RETRIES + ", got: " + value);
        }
        return value;
    }

    private static String resolveString(
            Map<String, String> env, Properties properties, String envKey, String propKey, String defaultValue) {
        String envValue = env.get(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.strip();
        }
        String propValue = properties.getProperty(propKey);
        if (propValue != null && !propValue.isBlank()) {
            return propValue.strip();
        }
        return defaultValue;
    }

    private static boolean resolveBool(
            Map<String, String> env, Properties properties, String envKey, String propKey, boolean defaultValue) {
        String envValue = env.get(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return Boolean.parseBoolean(envValue.strip());
        }
        String propValue = properties.getProperty(propKey);
        if (propValue != null && !propValue.isBlank()) {
            return Boolean.parseBoolean(propValue.strip());
        }
        return defaultValue;
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream(PROPERTIES_RESOURCE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            // Fall through to defaults if properties file is unreadable.
        }
        return props;
    }
}
