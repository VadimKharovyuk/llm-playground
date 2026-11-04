package com.example.llmplayground.runner;

import com.example.llmplayground.llm.CallContext;
import com.example.llmplayground.llm.ErrorDescriber;
import com.example.llmplayground.llm.LlmGateway;
import com.example.llmplayground.llm.LlmRequest;
import com.example.llmplayground.llm.ProviderType;
import com.example.llmplayground.persistence.Run;
import com.example.llmplayground.persistence.RunRepository;
import com.example.llmplayground.persistence.RunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Executes one run: for every model (one after another, so a local model stays loaded)
 * an optional warm-up call, then every prompt {@code repeats} times.
 */
@Service
public class BenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);

    private final LlmGateway gateway;
    private final RunRepository runRepository;
    private final Clock clock;

    @Autowired
    public BenchmarkService(LlmGateway gateway, RunRepository runRepository) {
        this(gateway, runRepository, Clock.systemUTC());
    }

    BenchmarkService(LlmGateway gateway, RunRepository runRepository, Clock clock) {
        this.gateway = gateway;
        this.runRepository = runRepository;
        this.clock = clock;
    }

    public void execute(RunPlan plan) {
        Run run = runRepository.findById(plan.runId())
                .orElseThrow(() -> new IllegalStateException("Run #" + plan.runId() + " not found"));
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(clock.instant());
        run = runRepository.save(run);
        log.info("Run #{}: {} model(s) x {} prompt(s) x {} repeat(s)",
                run.getId(), plan.models().size(), plan.prompts().size(), plan.repeats());
        try {
            for (RunPlan.ModelPlan model : plan.models()) {
                if (plan.warmup() && model.model().provider() == ProviderType.OLLAMA) {
                    RunPlan.PlannedPrompt first = plan.prompts().get(0);
                    gateway.call(new LlmRequest(model.model(), first.name(), first.text(), model.params()),
                            new CallContext(run.getId(), first.id(), 0, true));
                }
                for (RunPlan.PlannedPrompt prompt : plan.prompts()) {
                    for (int repeat = 1; repeat <= plan.repeats(); repeat++) {
                        gateway.call(new LlmRequest(model.model(), prompt.name(), prompt.text(), model.params()),
                                new CallContext(run.getId(), prompt.id(), repeat, false));
                    }
                }
            }
            run.setStatus(RunStatus.DONE);
        } catch (RuntimeException e) {
            // the gateway never throws; this is the database or a bug
            log.error("Run #{} failed", run.getId(), e);
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(ErrorDescriber.describe(e));
        }
        run.setFinishedAt(clock.instant());
        runRepository.save(run);
        log.info("Run #{} {}", run.getId(), run.getStatus());
    }
}
