package com.example.llmplayground.config;

import com.example.llmplayground.llm.ModelRef;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** Everything under {@code playground.*}; every value can be overridden from the command line. */
@ConfigurationProperties("playground")
public record PlaygroundProperties(
        Mode mode,
        List<String> models,
        List<String> prompts,
        Double temperature,
        Integer maxTokens,
        Integer runsPerPrompt,
        Boolean streaming,
        Boolean warmup,
        String comment,
        String appVersion,
        List<ModelOverride> modelOverrides,
        Http http,
        OpenRouter openrouter,
        Report report) {

    public enum Mode { RUN, REPORT, NONE }

    public PlaygroundProperties {
        mode = mode == null ? Mode.RUN : mode;
        models = models == null ? List.of() : List.copyOf(models);
        prompts = prompts == null ? List.of() : List.copyOf(prompts);
        runsPerPrompt = runsPerPrompt == null ? 1 : runsPerPrompt;
        streaming = streaming == null || streaming;
        warmup = warmup == null || warmup;
        modelOverrides = modelOverrides == null ? List.of() : List.copyOf(modelOverrides);
        http = http == null ? new Http(null, null) : http;
        openrouter = openrouter == null ? new OpenRouter(true) : openrouter;
        report = report == null ? new Report(null, null) : report;
        if (runsPerPrompt < 1) {
            throw new IllegalArgumentException("playground.runs-per-prompt must be >= 1");
        }
    }

    public List<ModelRef> modelRefs() {
        return models.stream().map(ModelRef::parse).toList();
    }

    public Integer maxTokensFor(ModelRef model) {
        return findOverride(model).map(ModelOverride::maxTokens).orElse(maxTokens);
    }

    public Double temperatureFor(ModelRef model) {
        return findOverride(model).map(ModelOverride::temperature).orElse(temperature);
    }

    private java.util.Optional<ModelOverride> findOverride(ModelRef model) {
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
            readTimeout = readTimeout == null ? Duration.ofSeconds(120) : readTimeout;
        }
    }

    public record OpenRouter(boolean fetchCost) {
    }

    public record Report(Long runId, String outputDir) {
        public Report {
            outputDir = outputDir == null || outputDir.isBlank() ? "reports" : outputDir;
        }
    }
}
