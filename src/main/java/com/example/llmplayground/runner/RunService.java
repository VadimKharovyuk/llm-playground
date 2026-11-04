package com.example.llmplayground.runner;

import com.example.llmplayground.config.PlaygroundProperties;
import com.example.llmplayground.llm.LlmParams;
import com.example.llmplayground.llm.ModelRef;
import com.example.llmplayground.persistence.LlmCallRepository;
import com.example.llmplayground.persistence.Prompt;
import com.example.llmplayground.persistence.PromptRepository;
import com.example.llmplayground.persistence.Run;
import com.example.llmplayground.persistence.RunRepository;
import com.example.llmplayground.persistence.RunStatus;
import com.example.llmplayground.prompt.PromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Starts runs from the web form. Runs execute in the background on a single thread,
 * one after another: parallel runs would compete for the machine and spoil local timings.
 */
@Service
public class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);
    static final int MAX_REPEATS = 20;

    private final RunRepository runRepository;
    private final LlmCallRepository callRepository;
    private final PromptRepository promptRepository;
    private final PromptService promptService;
    private final BenchmarkService benchmarkService;
    private final PlaygroundProperties properties;
    private final TaskExecutor executor;
    private final Clock clock;

    public RunService(RunRepository runRepository, LlmCallRepository callRepository,
                      PromptRepository promptRepository, PromptService promptService,
                      BenchmarkService benchmarkService, PlaygroundProperties properties,
                      @Qualifier("runExecutor") TaskExecutor executor) {
        this.runRepository = runRepository;
        this.callRepository = callRepository;
        this.promptRepository = promptRepository;
        this.promptService = promptService;
        this.benchmarkService = benchmarkService;
        this.properties = properties;
        this.executor = executor;
        this.clock = Clock.systemUTC();
    }

    /** A form pre-filled with the defaults from application.properties. */
    public RunForm defaultForm() {
        RunForm form = new RunForm();
        form.setModels(new ArrayList<>(properties.models()));
        form.setTemperature(properties.temperature());
        form.setMaxTokens(properties.maxTokens());
        form.setRunsPerPrompt(properties.runsPerPrompt());
        form.setStreaming(properties.streaming());
        form.setWarmup(properties.warmup());
        return form;
    }

    /**
     * Validates the form, stores the run as QUEUED and schedules it.
     *
     * @return id of the new run
     * @throws IllegalArgumentException with a user-facing message when the form is invalid
     */
    public Long start(RunForm form) {
        List<ModelRef> models = parseModels(form);
        if (models.isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы одну модель");
        }
        Double temperature = form.getTemperature();
        if (temperature != null && (temperature < 0 || temperature > 2)) {
            throw new IllegalArgumentException("temperature должна быть от 0 до 2");
        }
        Integer maxTokens = form.getMaxTokens();
        if (maxTokens != null && (maxTokens < 1 || maxTokens > 200_000)) {
            throw new IllegalArgumentException("max tokens должно быть от 1 до 200000");
        }
        int repeats = form.getRunsPerPrompt() == null ? 1 : form.getRunsPerPrompt();
        if (repeats < 1 || repeats > MAX_REPEATS) {
            throw new IllegalArgumentException("Повторов должно быть от 1 до " + MAX_REPEATS);
        }

        List<Prompt> prompts = selectedPrompts(form.getPromptIds());
        boolean hasNewPrompt = form.getNewPromptText() != null && !form.getNewPromptText().isBlank();
        if (prompts.isEmpty() && !hasNewPrompt) {
            throw new IllegalArgumentException("Напишите задание или выберите сохранённый промпт");
        }
        if (hasNewPrompt) {
            prompts.add(0, promptService.create(form.getNewPromptName(), form.getNewPromptText()));
        }

        List<RunPlan.ModelPlan> modelPlans = models.stream()
                .map(m -> new RunPlan.ModelPlan(m, new LlmParams(
                        properties.temperatureFor(m, temperature),
                        properties.maxTokensFor(m, maxTokens),
                        form.isStreaming())))
                .toList();
        List<RunPlan.PlannedPrompt> plannedPrompts = prompts.stream()
                .map(p -> new RunPlan.PlannedPrompt(p.getId(), p.getName(), p.getText()))
                .toList();

        Run run = new Run();
        run.setStartedAt(clock.instant());
        run.setStatus(RunStatus.QUEUED);
        run.setModels(models.stream().map(ModelRef::toString).collect(Collectors.joining(",")));
        run.setPrompts(prompts.stream().map(Prompt::getName).collect(Collectors.joining(",")));
        run.setTemperature(temperature);
        run.setMaxTokens(maxTokens);
        run.setRunsPerPrompt(repeats);
        run.setStreaming(form.isStreaming());
        run.setAppVersion(properties.appVersion());
        run.setComment(form.getComment() == null || form.getComment().isBlank() ? null : form.getComment().strip());
        RunPlan draft = new RunPlan(null, modelPlans, plannedPrompts, repeats, form.isWarmup());
        run.setExpectedCalls(draft.expectedCalls());
        run = runRepository.save(run);

        RunPlan plan = new RunPlan(run.getId(), modelPlans, plannedPrompts, repeats, form.isWarmup());
        executor.execute(() -> benchmarkService.execute(plan));
        log.info("Run #{} queued: {} call(s)", run.getId(), plan.expectedCalls());
        return run.getId();
    }

    public Progress progress(Run run) {
        long done = callRepository.countByRunId(run.getId());
        int expected = run.getExpectedCalls() == null ? (int) done : run.getExpectedCalls();
        return new Progress(done, expected);
    }

    /** Runs left QUEUED/RUNNING by a previous process were lost with it. */
    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedRuns() {
        int failed = runRepository.failActive(EnumSet.of(RunStatus.QUEUED, RunStatus.RUNNING), RunStatus.FAILED,
                "Прервано перезапуском приложения", clock.instant());
        if (failed > 0) {
            log.warn("Marked {} interrupted run(s) as FAILED", failed);
        }
    }

    static List<ModelRef> parseModels(RunForm form) {
        Set<ModelRef> result = new LinkedHashSet<>();
        for (String value : form.getModels()) {
            if (value != null && !value.isBlank()) {
                result.add(parseModel(value));
            }
        }
        if (form.getExtraModels() != null) {
            Arrays.stream(form.getExtraModels().split("[,\\n\\r]+"))
                    .map(String::strip)
                    .filter(s -> !s.isEmpty())
                    .forEach(s -> result.add(parseModel(s)));
        }
        return List.copyOf(result);
    }

    private static ModelRef parseModel(String value) {
        try {
            return ModelRef.parse(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Неверная модель «" + value.strip() + "»: нужен формат "
                    + "openrouter:vendor/model или ollama:model");
        }
    }

    private List<Prompt> selectedPrompts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> distinct = ids.stream().distinct().toList();
        Map<Long, Prompt> byId = promptRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(Prompt::getId, Function.identity()));
        List<Prompt> ordered = new ArrayList<>();
        for (Long id : distinct) {
            Prompt prompt = byId.get(id);
            if (prompt == null) {
                throw new IllegalArgumentException("Промпт #" + id + " не найден");
            }
            ordered.add(prompt);
        }
        return ordered;
    }

    public record Progress(long done, int expected) {
        public int percent() {
            return expected <= 0 ? 0 : (int) Math.min(100, done * 100 / expected);
        }
    }
}
