package com.example.llmplayground.config;

import com.example.llmplayground.llm.ModelRef;
import com.example.llmplayground.llm.ProviderType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
        OpenRouter openrouter,
        List<String> providers) {

    public PlaygroundProperties {
        providers = providers == null ? List.of() : providers.stream()
                .map(String::strip).filter(s -> !s.isEmpty()).toList();
        // fail fast on a typo like CHAT_PROVIDERS=olama
        providers.forEach(ProviderType::fromPrefix);
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

    /** Providers switched on by {@code playground.providers} (CHAT_PROVIDERS); empty means all. */
    public Set<ProviderType> enabledProviders() {
        if (providers.isEmpty()) {
            return EnumSet.allOf(ProviderType.class);
        }
        return providers.stream().map(ProviderType::fromPrefix)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ProviderType.class)));
    }

    public boolean isEnabled(ProviderType provider) {
        return enabledProviders().contains(provider);
    }

    /** True for a well-formed {@code provider:model} of an enabled provider. */
    public boolean isEnabled(String modelRef) {
        try {
            return isEnabled(ModelRef.parse(modelRef).provider());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Configured models of the enabled providers. */
    public List<String> enabledModels() {
        return models.stream().filter(this::isEnabled).toList();
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
