package nl.example.qualityreport.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OllamaSamplingConfigTest {

    @Test
    void withOverridesAppliesPositiveValues() {
        OllamaSamplingConfig base = OllamaSamplingConfig.GLOBAL_DEFAULTS;
        OllamaSamplingConfig overridden = base.withOverrides(16384, 1.5, 128, 0.2, 20, 0.8);

        assertThat(overridden.numCtx()).isEqualTo(16384);
        assertThat(overridden.repeatPenalty()).isEqualTo(1.5);
        assertThat(overridden.repeatLastN()).isEqualTo(128);
        assertThat(overridden.temperature()).isEqualTo(0.2);
        assertThat(overridden.topK()).isEqualTo(20);
        assertThat(overridden.topP()).isEqualTo(0.8);
    }

    @Test
    void withOverridesKeepsBaseForZeroValues() {
        OllamaSamplingConfig base = new OllamaSamplingConfig(32768, 1.1, 64, 0.7, 40, 0.9);
        OllamaSamplingConfig overridden = base.withOverrides(0, 0, 0, 0, 0, 0);

        assertThat(overridden).isEqualTo(base);
    }

    @Test
    void withOverridesPartialOverride() {
        OllamaSamplingConfig base = OllamaSamplingConfig.GLOBAL_DEFAULTS;
        OllamaSamplingConfig overridden = base.withOverrides(0, 0, 0, 0.4, 0, 0);

        assertThat(overridden.temperature()).isEqualTo(0.4);
        assertThat(overridden.numCtx()).isEqualTo(base.numCtx());
        assertThat(overridden.repeatPenalty()).isEqualTo(base.repeatPenalty());
    }
}
