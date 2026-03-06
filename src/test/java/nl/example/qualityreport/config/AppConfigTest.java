package nl.example.qualityreport.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AppConfigTest {

    @Nested
    class Precedence {

        @Test
        void envVarOverridesPropertiesFile() {
            Map<String, String> env = Map.of("CONSENSUS_AGENT_COUNT", "1");
            Properties props = propsWithCount("3");

            AppConfig config = new AppConfig(env, props);

            assertThat(config.agentCount()).isEqualTo(1);
        }

        @Test
        void propertiesFileOverridesDefault() {
            Properties props = propsWithCount("2");

            AppConfig config = new AppConfig(Map.of(), props);

            assertThat(config.agentCount()).isEqualTo(2);
        }

        @Test
        void defaultUsedWhenNeitherEnvNorProperties() {
            AppConfig config = new AppConfig(Map.of(), new Properties());

            assertThat(config.agentCount()).isEqualTo(AppConfig.DEFAULT_AGENT_COUNT);
        }

        @Test
        void blankEnvVarFallsToProperties() {
            Map<String, String> env = Map.of("CONSENSUS_AGENT_COUNT", "  ");
            Properties props = propsWithCount("2");

            AppConfig config = new AppConfig(env, props);

            assertThat(config.agentCount()).isEqualTo(2);
        }

        @Test
        void blankPropertyFallsToDefault() {
            Properties props = propsWithCount("  ");

            AppConfig config = new AppConfig(Map.of(), props);

            assertThat(config.agentCount()).isEqualTo(AppConfig.DEFAULT_AGENT_COUNT);
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsZeroFromEnv() {
            assertThatThrownBy(() -> new AppConfig(Map.of("CONSENSUS_AGENT_COUNT", "0"), new Properties()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CONSENSUS_AGENT_COUNT")
                    .hasMessageContaining("between 1 and 3");
        }

        @Test
        void rejectsFourFromProperties() {
            assertThatThrownBy(() -> new AppConfig(Map.of(), propsWithCount("4")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("consensus.agent.count")
                    .hasMessageContaining("between 1 and 3");
        }

        @Test
        void rejectsNonNumericFromEnv() {
            assertThatThrownBy(() -> new AppConfig(Map.of("CONSENSUS_AGENT_COUNT", "abc"), new Properties()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CONSENSUS_AGENT_COUNT")
                    .hasMessageContaining("'abc'");
        }

        @Test
        void rejectsNegativeFromProperties() {
            assertThatThrownBy(() -> new AppConfig(Map.of(), propsWithCount("-1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("consensus.agent.count");
        }

        @Test
        void acceptsOne() {
            assertThat(new AppConfig(Map.of("CONSENSUS_AGENT_COUNT", "1"), new Properties()).agentCount())
                    .isEqualTo(1);
        }

        @Test
        void acceptsTwo() {
            assertThat(new AppConfig(Map.of("CONSENSUS_AGENT_COUNT", "2"), new Properties()).agentCount())
                    .isEqualTo(2);
        }

        @Test
        void acceptsThree() {
            assertThat(new AppConfig(Map.of("CONSENSUS_AGENT_COUNT", "3"), new Properties()).agentCount())
                    .isEqualTo(3);
        }
    }

    @Nested
    class ToolLoopFlag {

        @Test
        void defaultsToDisabled() {
            AppConfig config = new AppConfig(Map.of(), new Properties());
            assertThat(config.toolLoopEnabled()).isFalse();
        }

        @Test
        void enabledViaEnvVar() {
            AppConfig config = new AppConfig(Map.of("TOOL_LOOP_ENABLED", "true"), new Properties());
            assertThat(config.toolLoopEnabled()).isTrue();
        }

        @Test
        void enabledViaProperty() {
            Properties props = new Properties();
            props.setProperty("tool.loop.enabled", "true");
            AppConfig config = new AppConfig(Map.of(), props);
            assertThat(config.toolLoopEnabled()).isTrue();
        }

        @Test
        void envOverridesProperty() {
            Properties props = new Properties();
            props.setProperty("tool.loop.enabled", "true");
            AppConfig config = new AppConfig(Map.of("TOOL_LOOP_ENABLED", "false"), props);
            assertThat(config.toolLoopEnabled()).isFalse();
        }

        @Test
        void blankEnvFallsToProperty() {
            Properties props = new Properties();
            props.setProperty("tool.loop.enabled", "true");
            AppConfig config = new AppConfig(Map.of("TOOL_LOOP_ENABLED", "  "), props);
            assertThat(config.toolLoopEnabled()).isTrue();
        }
    }

    @Nested
    class EvidenceFirstFlag {

        @Test
        void defaultsToEnabled() {
            AppConfig config = new AppConfig(Map.of(), new Properties());
            assertThat(config.evidenceFirstEnabled()).isTrue();
        }

        @Test
        void enabledViaEnvVar() {
            AppConfig config = new AppConfig(Map.of("EVIDENCE_FIRST_ENABLED", "true"), new Properties());
            assertThat(config.evidenceFirstEnabled()).isTrue();
        }

        @Test
        void enabledViaProperty() {
            Properties props = new Properties();
            props.setProperty("evidence.first.enabled", "true");
            AppConfig config = new AppConfig(Map.of(), props);
            assertThat(config.evidenceFirstEnabled()).isTrue();
        }

        @Test
        void envOverridesProperty() {
            Properties props = new Properties();
            props.setProperty("evidence.first.enabled", "false");
            AppConfig config = new AppConfig(Map.of("EVIDENCE_FIRST_ENABLED", "true"), props);
            assertThat(config.evidenceFirstEnabled()).isTrue();
        }
    }

    @Nested
    class NarrativeVerifyFlag {

        @Test
        void defaultsToEnabled() {
            AppConfig config = new AppConfig(Map.of(), new Properties());
            assertThat(config.narrativeVerifyEnabled()).isTrue();
        }

        @Test
        void enabledViaEnvVar() {
            AppConfig config = new AppConfig(Map.of("NARRATIVE_VERIFY_ENABLED", "true"), new Properties());
            assertThat(config.narrativeVerifyEnabled()).isTrue();
        }

        @Test
        void envOverridesProperty() {
            Properties props = new Properties();
            props.setProperty("narrative.verify.enabled", "false");
            AppConfig config = new AppConfig(Map.of("NARRATIVE_VERIFY_ENABLED", "true"), props);
            assertThat(config.narrativeVerifyEnabled()).isTrue();
        }
    }

    @Nested
    class RepairMaxRetries {

        @Test
        void defaultsToOne() {
            AppConfig config = new AppConfig(Map.of(), new Properties());
            assertThat(config.repairMaxRetries()).isEqualTo(1);
        }

        @Test
        void setViaEnv() {
            AppConfig config = new AppConfig(Map.of("NARRATIVE_REPAIR_MAX_RETRIES", "2"), new Properties());
            assertThat(config.repairMaxRetries()).isEqualTo(2);
        }

        @Test
        void setViaProperty() {
            Properties props = new Properties();
            props.setProperty("narrative.repair.max-retries", "0");
            AppConfig config = new AppConfig(Map.of(), props);
            assertThat(config.repairMaxRetries()).isEqualTo(0);
        }

        @Test
        void envOverridesProperty() {
            Properties props = new Properties();
            props.setProperty("narrative.repair.max-retries", "0");
            AppConfig config = new AppConfig(Map.of("NARRATIVE_REPAIR_MAX_RETRIES", "3"), props);
            assertThat(config.repairMaxRetries()).isEqualTo(3);
        }

        @Test
        void rejectsFourFromEnv() {
            assertThatThrownBy(() -> new AppConfig(Map.of("NARRATIVE_REPAIR_MAX_RETRIES", "4"), new Properties()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 0 and 3");
        }

        @Test
        void rejectsNegative() {
            assertThatThrownBy(() -> new AppConfig(Map.of("NARRATIVE_REPAIR_MAX_RETRIES", "-1"), new Properties()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 0 and 3");
        }
    }

    @Nested
    class DebugArtifactsFlag {

        @Test
        void defaultsToDisabled() {
            AppConfig config = new AppConfig(Map.of(), new Properties());
            assertThat(config.debugArtifactsEnabled()).isFalse();
        }

        @Test
        void enabledViaEnvVar() {
            AppConfig config = new AppConfig(Map.of("NARRATIVE_DEBUG_ARTIFACTS_ENABLED", "true"), new Properties());
            assertThat(config.debugArtifactsEnabled()).isTrue();
        }

        @Test
        void enabledViaProperty() {
            Properties props = new Properties();
            props.setProperty("narrative.debug.artifacts.enabled", "true");
            AppConfig config = new AppConfig(Map.of(), props);
            assertThat(config.debugArtifactsEnabled()).isTrue();
        }
    }

    @Nested
    class DomainKeywordsPath {

        @Test
        void defaultsToNull() {
            AppConfig config = new AppConfig(Map.of(), new Properties());
            assertThat(config.domainKeywordsPath()).isNull();
        }

        @Test
        void setViaEnvVar() {
            AppConfig config = new AppConfig(Map.of("DOMAIN_KEYWORDS_PATH", "/opt/keywords.json"), new Properties());
            assertThat(config.domainKeywordsPath()).isEqualTo("/opt/keywords.json");
        }

        @Test
        void setViaProperty() {
            Properties props = new Properties();
            props.setProperty("domain.keywords.path", "my-keywords.json");
            AppConfig config = new AppConfig(Map.of(), props);
            assertThat(config.domainKeywordsPath()).isEqualTo("my-keywords.json");
        }

        @Test
        void envOverridesProperty() {
            Properties props = new Properties();
            props.setProperty("domain.keywords.path", "from-props.json");
            AppConfig config = new AppConfig(Map.of("DOMAIN_KEYWORDS_PATH", "from-env.json"), props);
            assertThat(config.domainKeywordsPath()).isEqualTo("from-env.json");
        }

        @Test
        void blankEnvFallsToProperty() {
            Properties props = new Properties();
            props.setProperty("domain.keywords.path", "fallback.json");
            AppConfig config = new AppConfig(Map.of("DOMAIN_KEYWORDS_PATH", "  "), props);
            assertThat(config.domainKeywordsPath()).isEqualTo("fallback.json");
        }
    }

    @Nested
    class DefaultConstructor {

        @Test
        void loadsFromClasspathAndReturnsValidCount() {
            AppConfig config = new AppConfig();
            assertThat(config.agentCount()).isBetween(AppConfig.MIN_AGENT_COUNT, AppConfig.MAX_AGENT_COUNT);
        }

        @Test
        void toolLoopDefaultsToDisabled() {
            AppConfig config = new AppConfig();
            assertThat(config.toolLoopEnabled()).isFalse();
        }

        @Test
        void allNewFlagsDefaultToDisabled() {
            AppConfig config = new AppConfig();
            assertThat(config.evidenceFirstEnabled()).isTrue();
            assertThat(config.narrativeVerifyEnabled()).isTrue();
            assertThat(config.debugArtifactsEnabled()).isFalse();
            assertThat(config.repairMaxRetries()).isEqualTo(1);
            assertThat(config.domainKeywordsPath()).isNull();
        }
    }

    @Nested
    class CliOverrides {

        @Test
        void overridesEvidenceFirst() {
            AppConfig base = new AppConfig(Map.of(), new Properties());
            assertThat(base.evidenceFirstEnabled()).isTrue();

            AppConfig overridden = base.withCliOverrides(false, null, null);
            assertThat(overridden.evidenceFirstEnabled()).isFalse();
            assertThat(overridden.narrativeVerifyEnabled()).isTrue();
            assertThat(overridden.debugArtifactsEnabled()).isFalse();
        }

        @Test
        void overridesNarrativeVerify() {
            AppConfig base = new AppConfig(Map.of(), new Properties());
            AppConfig overridden = base.withCliOverrides(null, false, null);
            assertThat(overridden.narrativeVerifyEnabled()).isFalse();
            assertThat(overridden.evidenceFirstEnabled()).isTrue();
        }

        @Test
        void overridesDebugArtifacts() {
            AppConfig base = new AppConfig(Map.of(), new Properties());
            AppConfig overridden = base.withCliOverrides(null, null, true);
            assertThat(overridden.debugArtifactsEnabled()).isTrue();
        }

        @Test
        void allNullsPreservesOriginal() {
            Properties props = new Properties();
            props.setProperty("evidence.first.enabled", "false");
            AppConfig base = new AppConfig(Map.of(), props);

            AppConfig overridden = base.withCliOverrides(null, null, null);
            assertThat(overridden.evidenceFirstEnabled()).isFalse();
            assertThat(overridden.narrativeVerifyEnabled()).isTrue();
            assertThat(overridden.debugArtifactsEnabled()).isFalse();
            assertThat(overridden.agentCount()).isEqualTo(base.agentCount());
            assertThat(overridden.repairMaxRetries()).isEqualTo(base.repairMaxRetries());
        }

        @Test
        void cliOverridesEnvVar() {
            AppConfig base = new AppConfig(Map.of("EVIDENCE_FIRST_ENABLED", "true"), new Properties());
            assertThat(base.evidenceFirstEnabled()).isTrue();

            AppConfig overridden = base.withCliOverrides(false, null, null);
            assertThat(overridden.evidenceFirstEnabled()).isFalse();
        }
    }

    private static Properties propsWithCount(String value) {
        Properties props = new Properties();
        props.setProperty("consensus.agent.count", value);
        return props;
    }
}
