package com.example.llmplayground.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * The OpenAI starter refuses to start with an empty API key. An Ollama-only run must work
 * without an OpenRouter key (an empty {@code OPENROUTER_API_KEY=} line in .env included),
 * so a blank key is replaced with a placeholder; OpenRouter calls then fail with a clear ERROR.
 * Runs after .env has been imported.
 */
public class OpenRouterKeyPlaceholder implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY = "spring.ai.openai.api-key";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String key = environment.getProperty(PROPERTY);
        if (key == null || key.isBlank()) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource("openrouterKeyPlaceholder", Map.of(PROPERTY, LlmConfig.MISSING_KEY)));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
