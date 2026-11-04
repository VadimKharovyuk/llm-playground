package com.example.llmplayground.runner;

import com.example.llmplayground.llm.CallContext;
import com.example.llmplayground.llm.LlmParams;
import com.example.llmplayground.llm.LlmRequest;
import com.example.llmplayground.llm.ModelRef;
import com.example.llmplayground.llm.ProviderType;
import com.example.llmplayground.persistence.Run;
import com.example.llmplayground.persistence.RunRepository;
import com.example.llmplayground.persistence.RunStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BenchmarkServiceTest {

    record Call(LlmRequest request, CallContext context) {
    }

    private static RunPlan plan(boolean warmup) {
        return new RunPlan(42L,
                List.of(new RunPlan.ModelPlan(ModelRef.parse("openrouter:anthropic/claude-haiku-5.5"),
                                new LlmParams(0.3, 512, true)),
                        new RunPlan.ModelPlan(ModelRef.parse("ollama:qwen3:8b"), new LlmParams(0.3, 4096, true))),
                List.of(new RunPlan.PlannedPrompt(1L, "book-api", "Write a controller"),
                        new RunPlan.PlannedPrompt(2L, "puzzle", "Solve")),
                2, warmup);
    }

    private static RunRepository repository(Run run) {
        RunRepository runRepository = mock(RunRepository.class);
        when(runRepository.findById(42L)).thenReturn(Optional.of(run));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return runRepository;
    }

    @Test
    void warmsUpOllamaOnlyAndRepeatsEveryPrompt() {
        Run run = new Run();
        run.setId(42L);
        List<Call> calls = new ArrayList<>();
        BenchmarkService service = new BenchmarkService((request, context) -> {
            calls.add(new Call(request, context));
            return null;
        }, repository(run));

        RunPlan plan = plan(true);
        service.execute(plan);

        assertThat(calls).hasSize(plan.expectedCalls()).hasSize(1 + 2 * 2 * 2);
        List<Call> warmups = calls.stream().filter(c -> c.context().coldStart()).toList();
        assertThat(warmups).hasSize(1);
        assertThat(warmups.get(0).request().model().provider()).isEqualTo(ProviderType.OLLAMA);
        assertThat(warmups.get(0).context().repeatNo()).isZero();
        assertThat(calls).allSatisfy(c -> assertThat(c.context().runId()).isEqualTo(42L));
        assertThat(calls.stream().filter(c -> c.request().promptName().equals("puzzle")))
                .allSatisfy(c -> assertThat(c.context().promptId()).isEqualTo(2L));
        assertThat(calls.stream().filter(c -> c.request().model().provider() == ProviderType.OLLAMA))
                .allSatisfy(c -> assertThat(c.request().params().maxTokens()).isEqualTo(4096));
        assertThat(run.getStatus()).isEqualTo(RunStatus.DONE);
        assertThat(run.getFinishedAt()).isNotNull();
    }

    @Test
    void storageFailureMarksRunFailed() {
        Run run = new Run();
        run.setId(42L);
        BenchmarkService service = new BenchmarkService((request, context) -> {
            throw new IllegalStateException("db down");
        }, repository(run));

        service.execute(plan(false));

        assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(run.getErrorMessage()).contains("db down");
    }
}
