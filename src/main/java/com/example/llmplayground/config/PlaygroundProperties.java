package com.example.llmplayground.config;

import com.example.llmplayground.llm.ModelRef;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Everything under {@code playground.*} in application.properties. */
@ConfigurationProperties("playground")
public record PlaygroundProperties(
        List<String> models,
        Double temperature,
        Integer maxTokens,
        Integer runsPerPrompt,
        Boolean streaming,
        Boolean warmup,
        String appVersion,
        List<ModelOverride> modelOverrides,
        Http http,
        OpenRouter openrouter) {

    public PlaygroundProperties {
        models = models == null ? List.of() : List.copyOf(models);
        runsPerPrompt = runsPerPrompt == null ? 1 : runsPerPrompt;
        streaming = streaming == null || streaming;
        warmup = warmup == null || warmup;
        modelOverrides = modelOverrides == null ? List.of() : List.copyOf(modelOverrides);
        http = http == null ? new Http(null, null) : http;
        openrouter = openrouter == null ? new OpenRouter(true) : openrouter;
    }

    /** Configured models; fails fast on a malformed entry. */
    public List<ModelRef> modelRefs() {
        return models.stream().map(ModelRef::parse).toList();
    }

    /** Per-model max-tokens from model-overrides, otherwise the given common value. */
    public Integer maxTokensFor(ModelRef model, Integer common) {
        return findOverride(model).map(ModelOverride::maxTokens).orElse(common);
    }

    public Double temperatureFor(ModelRef model, Double common) {
        return findOverride(model).map(ModelOverride::temperature).orElse(common);
    }

    private Optional<ModelOverride> findOverride(ModelRef model) {
        return modelOverrides.stream()
                .filter(o -> o.model() != null && ModelRef.parse(o.model()).equals(model))
                .findFirst();
    }

    /** Per-model override; a null field means "use the common value". */
    public record ModelOverride(String model, Integer maxTokens, Double temperature) {
    }

    public record Http(Duration connectTimeout, Duration readTimeout) {
        public Http {
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(180) : readTimeout;
        }
    }

    public record OpenRouter(boolean fetchCost) {
    }
}
