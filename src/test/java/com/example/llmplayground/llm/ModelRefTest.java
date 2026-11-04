package com.example.llmplayground.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelRefTest {

    @Test
    void parsesOpenRouterModelWithSlash() {
        ModelRef ref = ModelRef.parse("openrouter:anthropic/claude-haiku-5.5");
        assertThat(ref.provider()).isEqualTo(ProviderType.OPENROUTER);
        assertThat(ref.model()).isEqualTo("anthropic/claude-haiku-5.5");
    }

    @Test
    void splitsOnFirstColonOnlySoOllamaTagsSurvive() {
        ModelRef ref = ModelRef.parse("ollama:llama3.2:3b");
        assertThat(ref.provider()).isEqualTo(ProviderType.OLLAMA);
        assertThat(ref.model()).isEqualTo("llama3.2:3b");
    }

    @Test
    void providerPrefixIsCaseInsensitiveAndTrimmed() {
        assertThat(ModelRef.parse("  OLLAMA:qwen3 ")).isEqualTo(new ModelRef(ProviderType.OLLAMA, "qwen3"));
    }

    @Test
    void roundTripsThroughToString() {
        assertThat(ModelRef.parse("ollama:qwen3:8b").toString()).isEqualTo("ollama:qwen3:8b");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "llama3.2", ":llama3.2", "ollama:", "ollama:   "})
    void rejectsMalformedReferences(String value) {
        assertThatThrownBy(() -> ModelRef.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownProvider() {
        assertThatThrownBy(() -> ModelRef.parse("openai:gpt-5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown provider 'openai'");
    }
}
