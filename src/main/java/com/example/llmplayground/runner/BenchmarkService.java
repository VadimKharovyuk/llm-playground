package com.example.llmplayground.runner;

import com.example.llmplayground.config.PlaygroundProperties;
import com.example.llmplayground.llm.CallContext;
import com.example.llmplayground.llm.LlmGateway;
import com.example.llmplayground.llm.LlmParams;
import com.example.llmplayground.llm.LlmRequest;
import com.example.llmplayground.llm.ModelRef;
import com.example.llmplayground.llm.ProviderType;
import com.example.llmplayground.persistence.Run;
import com.example.llmplayground.persistence.RunRepository;
import com.example.llmplayground.prompt.PromptLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Executes one run: for every model (one after another, so a local model stays loaded)
 * an optional warm-up call, then every prompt {@code runs-per-prompt} times.
 */
@Service
public class BenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);

    private final LlmGateway gateway;
    private final RunRepository runRepository;
    private final PromptLibrary promptLibrary;
    private final PlaygroundProperties properties;
    private final Clock clock;

    @Autowired
    public BenchmarkService(LlmGateway gateway, RunRepository runRepository, PromptLibrary promptLibrary,
                            PlaygroundProperties properties) {
        this(gateway, runRepository, promptLibrary, properties, Clock.systemUTC());
    }

    BenchmarkService(LlmGateway gateway, RunRepository runRepository, PromptLibrary promptLibrary,
                     PlaygroundProperties properties, Clock clock) {
        this.gateway = gateway;
        this.runRepository = runRepository;
        this.promptLibrary = promptLibrary;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return id of the stored run */
    public Long execute() {
        List<ModelRef> models = properties.modelRefs();
        List<PromptLibrary.Prompt> prompts = promptLibrary.select(properties.prompts());
        if (models.isEmpty()) {
            throw new IllegalStateException("No models configured (playground.models)");
        }
        if (prompts.isEmpty()) {
            throw new IllegalStateException("No prompts found in classpath:prompts/");
        }

        Run run = new Run();
        run.setStartedAt(clock.instant());
        run.setModels(models.stream().map(ModelRef::toString).collect(Collectors.joining(",")));
        run.setPrompts(prompts.stream().map(PromptLibrary.Prompt::name).collect(Collectors.joining(",")));
        run.setTemperature(properties.temperature());
        run.setMaxTokens(properties.maxTokens());
        run.setRunsPerPrompt(properties.runsPerPrompt());
        run.setStreaming(properties.streaming());
        run.setAppVersion(properties.appVersion());
        run.setComment(properties.comment() == null || properties.comment().isBlank() ? null : properties.comment());
        run = runRepository.save(run);
        Long runId = run.getId();
        log.info("Run #{}: {} model(s) x {} prompt(s) x {} repeat(s), streaming={}",
                runId, models.size(), prompts.size(), properties.runsPerPrompt(), properties.streaming());

        for (ModelRef model : models) {
            LlmParams params = new LlmParams(properties.temperatureFor(model), properties.maxTokensFor(model),
                    properties.streaming());
            if (properties.warmup() && model.provider() == ProviderType.OLLAMA) {
                PromptLibrary.Prompt first = prompts.get(0);
                gateway.call(new LlmRequest(model, first.name(), first.text(), params),
                        new CallContext(runId, 0, true));
            }
            for (PromptLibrary.Prompt prompt : prompts) {
                for (int repeat = 1; repeat <= properties.runsPerPrompt(); repeat++) {
                    gateway.call(new LlmRequest(model, prompt.name(), prompt.text(), params),
                            new CallContext(runId, repeat, false));
                }
            }
        }

        run.setFinishedAt(clock.instant());
        runRepository.save(run);
        return runId;
    }
}
