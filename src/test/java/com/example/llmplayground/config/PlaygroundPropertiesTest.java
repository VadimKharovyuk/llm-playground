package com.example.llmplayground.config;

import com.example.llmplayground.llm.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaygroundPropertiesTest {

    private static final List<String> MODELS = List.of(
            "openrouter:anthropic/claude-haiku-5.5", "ollama:llama3.2:3b", "ollama:qwen3:8b");

    private static PlaygroundProperties withProviders(List<String> providers) {
        return new PlaygroundProperties(MODELS, null, null, null, null, null, null, null, null, null, providers);
    }

    @Test
    void ollamaOnlyKeepsOllamaModels() {
        PlaygroundProperties p = withProviders(List.of("ollama"));
        assertThat(p.enabledProviders()).containsExactly(ProviderType.OLLAMA);
        assertThat(p.enabledModels()).containsExactly("ollama:llama3.2:3b", "ollama:qwen3:8b");
        assertThat(p.isEnabled("openrouter:openai/gpt-5-mini")).isFalse();
    }

    @Test
    void openRouterOnlyKeepsCloudModels() {
        PlaygroundProperties p = withProviders(List.of(" OpenRouter "));
        assertThat(p.enabledProviders()).containsExactly(ProviderType.OPENROUTER);
        assertThat(p.enabledModels()).containsExactly("openrouter:anthropic/claude-haiku-5.5");
    }

    @Test
    void bothOrEmptyMeansAll() {
        assertThat(withProviders(List.of("ollama", "openrouter")).enabledModels()).isEqualTo(MODELS);
        assertThat(withProviders(List.of()).enabledProviders()).containsExactlyInAnyOrder(ProviderType.values());
        assertThat(withProviders(null).enabledModels()).isEqualTo(MODELS);
    }

    @Test
    void typoFailsAtStartup() {
        assertThatThrownBy(() -> withProviders(List.of("olama")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("olama");
    }

    @Test
    void malformedModelIsNotEnabled() {
        assertThat(withProviders(List.of("ollama")).isEnabled("llama3.2")).isFalse();
    }
}
