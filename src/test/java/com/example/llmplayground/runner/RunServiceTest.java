package com.example.llmplayground.runner;

import com.example.llmplayground.config.PlaygroundProperties;
import com.example.llmplayground.persistence.LlmCallRepository;
import com.example.llmplayground.persistence.Prompt;
import com.example.llmplayground.persistence.PromptRepository;
import com.example.llmplayground.persistence.Run;
import com.example.llmplayground.persistence.RunRepository;
import com.example.llmplayground.persistence.RunStatus;
import com.example.llmplayground.prompt.PromptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RunServiceTest {

    private final RunRepository runs = mock(RunRepository.class);
    private final PromptRepository prompts = mock(PromptRepository.class);
    private final PromptService promptService = mock(PromptService.class);
    private final BenchmarkService benchmark = mock(BenchmarkService.class);
    private RunService service;

    @BeforeEach
    void setUp() {
        PlaygroundProperties properties = new PlaygroundProperties(
                List.of("openrouter:anthropic/claude-haiku-5.5", "ollama:llama3.2:3b"),
                0.2, 1024, 1, true, true, "1.0",
                List.of(new PlaygroundProperties.ModelOverride("ollama:qwen3:8b", 8192, null)), null, null);
        // synchronous executor: the run executes inside start()
        service = new RunService(runs, mock(LlmCallRepository.class), prompts, promptService, benchmark,
                properties, Runnable::run);
        when(runs.save(any())).thenAnswer(inv -> {
            Run run = inv.getArgument(0);
            run.setId(9L);
            return run;
        });
    }

    private static Prompt prompt(long id, String name) {
        Prompt p = new Prompt(name, "text of " + name, Instant.now());
        p.setId(id);
        return p;
    }

    @Test
    void defaultFormComesFromProperties() {
        RunForm form = service.defaultForm();
        assertThat(form.getModels()).containsExactly("openrouter:anthropic/claude-haiku-5.5", "ollama:llama3.2:3b");
        assertThat(form.getMaxTokens()).isEqualTo(1024);
        assertThat(form.isStreaming()).isTrue();
        assertThat(form.isWarmup()).isTrue();
    }

    @Test
    void newTaskIsSavedAndRunIsQueuedAndExecuted() {
        when(promptService.create(anyString(), anyString())).thenReturn(prompt(3, "book-api"));
        RunForm form = new RunForm();
        form.setModels(List.of("ollama:llama3.2:3b"));
        form.setExtraModels("ollama:qwen3:8b,\n openrouter:openai/gpt-5-mini");
        form.setNewPromptName("book-api");
        form.setNewPromptText("Write a Spring controller and repository");
        form.setRunsPerPrompt(2);
        form.setMaxTokens(1000);
        form.setWarmup(true);

        Long id = service.start(form);

        assertThat(id).isEqualTo(9L);
        ArgumentCaptor<Run> saved = ArgumentCaptor.forClass(Run.class);
        verify(runs).save(saved.capture());
        Run run = saved.getValue();
        assertThat(run.getStatus()).isEqualTo(RunStatus.QUEUED);
        assertThat(run.getModels()).isEqualTo("ollama:llama3.2:3b,ollama:qwen3:8b,openrouter:openai/gpt-5-mini");
        assertThat(run.getPrompts()).isEqualTo("book-api");
        assertThat(run.getExpectedCalls()).isEqualTo(3 * 1 * 2 + 2);   // + warm-up per Ollama model

        ArgumentCaptor<RunPlan> plan = ArgumentCaptor.forClass(RunPlan.class);
        verify(benchmark).execute(plan.capture());
        assertThat(plan.getValue().runId()).isEqualTo(9L);
        assertThat(plan.getValue().prompts()).extracting(RunPlan.PlannedPrompt::id).containsExactly(3L);
        assertThat(plan.getValue().models())
                .extracting(m -> m.params().maxTokens())
                .containsExactly(1000, 8192, 1000);                    // qwen3 override from properties
    }

    @Test
    void savedPromptsKeepTheChosenOrder() {
        when(prompts.findAllById(any())).thenReturn(List.of(prompt(1, "a"), prompt(2, "b")));
        RunForm form = new RunForm();
        form.setModels(List.of("ollama:llama3.2:3b"));
        form.setPromptIds(List.of(2L, 1L, 2L));

        service.start(form);

        ArgumentCaptor<RunPlan> plan = ArgumentCaptor.forClass(RunPlan.class);
        verify(benchmark).execute(plan.capture());
        assertThat(plan.getValue().prompts()).extracting(RunPlan.PlannedPrompt::name).containsExactly("b", "a");
        verify(promptService, never()).create(any(), any());
    }

    @Test
    void rejectsFormWithoutModels() {
        RunForm form = new RunForm();
        form.setNewPromptText("task");
        assertThatThrownBy(() -> service.start(form)).hasMessageContaining("модель");
        verifyNoInteractions(benchmark);
    }

    @Test
    void rejectsMalformedModel() {
        RunForm form = new RunForm();
        form.setExtraModels("gpt-5");
        form.setNewPromptText("task");
        assertThatThrownBy(() -> service.start(form)).hasMessageContaining("gpt-5");
    }

    @Test
    void rejectsFormWithoutPrompt() {
        RunForm form = new RunForm();
        form.setModels(List.of("ollama:llama3.2:3b"));
        assertThatThrownBy(() -> service.start(form)).hasMessageContaining("задание");
        verify(promptService, never()).create(any(), any());
    }

    @Test
    void rejectsOutOfRangeParameters() {
        RunForm form = new RunForm();
        form.setModels(List.of("ollama:llama3.2:3b"));
        form.setNewPromptText("task");
        form.setRunsPerPrompt(100);
        assertThatThrownBy(() -> service.start(form)).isInstanceOf(IllegalArgumentException.class);
        form.setRunsPerPrompt(1);
        form.setTemperature(3.0);
        assertThatThrownBy(() -> service.start(form)).isInstanceOf(IllegalArgumentException.class);
        verify(promptService, never()).create(any(), any());
    }
}
