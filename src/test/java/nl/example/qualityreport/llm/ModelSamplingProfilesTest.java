package nl.example.qualityreport.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelSamplingProfilesTest {

    @Test
    void exactMatchReturnsModelProfile() {
        OllamaSamplingConfig config = ModelSamplingProfiles.forModel("deepcoder:1.5b");
        assertThat(config.temperature()).isEqualTo(0.3);
        assertThat(config.repeatPenalty()).isEqualTo(1.3);
        assertThat(config.repeatLastN()).isEqualTo(128);
    }

    @Test
    void prefixMatchReturnsModelProfile() {
        OllamaSamplingConfig config = ModelSamplingProfiles.forModel("deepcoder:3b");
        assertThat(config.temperature()).isEqualTo(0.3);
        assertThat(config.repeatPenalty()).isEqualTo(1.3);
    }

    @Test
    void unknownModelReturnsGlobalDefaults() {
        OllamaSamplingConfig config = ModelSamplingProfiles.forModel("qwen2.5-coder:7b");
        assertThat(config).isEqualTo(OllamaSamplingConfig.GLOBAL_DEFAULTS);
    }

    @Test
    void nullModelReturnsGlobalDefaults() {
        assertThat(ModelSamplingProfiles.forModel(null)).isEqualTo(OllamaSamplingConfig.GLOBAL_DEFAULTS);
    }

    @Test
    void caseInsensitiveMatch() {
        OllamaSamplingConfig config = ModelSamplingProfiles.forModel("DeepCoder:1.5B");
        assertThat(config.temperature()).isEqualTo(0.3);
    }

    @Test
    void qwen35_08bHasDistinctProfile() {
        OllamaSamplingConfig config = ModelSamplingProfiles.forModel("qwen3.5:0.8b");
        assertThat(config.temperature()).isEqualTo(0.4);
        assertThat(config.repeatPenalty()).isEqualTo(1.2);
        assertThat(config.repeatLastN()).isEqualTo(96);
    }

    @Test
    void qwen35_2bHasDistinctProfile() {
        OllamaSamplingConfig config = ModelSamplingProfiles.forModel("qwen3.5:2b");
        assertThat(config.temperature()).isEqualTo(0.5);
        assertThat(config.repeatPenalty()).isEqualTo(1.15);
    }

    @Test
    void qwen25Coder3bHasOptimizedProfile() {
        OllamaSamplingConfig config = ModelSamplingProfiles.forModel("qwen2.5-coder:3b");
        assertThat(config.temperature()).isEqualTo(0.65);
        assertThat(config.repeatPenalty()).isEqualTo(1.15);
        assertThat(config.repeatLastN()).isEqualTo(80);
        assertThat(config.topK()).isEqualTo(40);
        assertThat(config.topP()).isEqualTo(0.92);
    }
}
