package com.example.llmplayground.runner;

import com.example.llmplayground.config.PlaygroundProperties;
import com.example.llmplayground.llm.CallContext;
import com.example.llmplayground.llm.LlmRequest;
import com.example.llmplayground.llm.ProviderType;
import com.example.llmplayground.persistence.Run;
import com.example.llmplayground.persistence.RunRepository;
import com.example.llmplayground.prompt.PromptLibrary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BenchmarkServiceTest {

    record Call(LlmRequest request, CallContext context) {
    }

    @Test
    void warmsUpOllamaOnlyAndRepeatsEveryPrompt() {
        PlaygroundProperties properties = new PlaygroundProperties(PlaygroundProperties.Mode.NONE,
                List.of("openrouter:anthropic/claude-haiku-5.5", "ollama:qwen3:8b"),
                List.of(), 0.3, 512, 2, false, true, "test", "1.0",
                List.of(new PlaygroundProperties.ModelOverride("ollama:qwen3:8b", 4096, null)),
                null, null, null);
        RunRepository runRepository = mock(RunRepository.class);
        when(runRepository.save(any())).thenAnswer(inv -> {
            Run run = inv.getArgument(0);
            run.setId(42L);
            return run;
        });
        List<Call> calls = new ArrayList<>();
        BenchmarkService service = new BenchmarkService((request, context) -> {
            calls.add(new Call(request, context));
            return null;
        }, runRepository, new PromptLibrary(), properties);

        Long runId = service.execute();

        int prompts = new PromptLibrary().all().size();
        assertThat(runId).isEqualTo(42L);
        assertThat(calls).hasSize(1 + 2 * prompts * 2);   // one warm-up + 2 models x prompts x 2 repeats

        List<Call> warmups = calls.stream().filter(c -> c.context().coldStart()).toList();
        assertThat(warmups).hasSize(1);
        assertThat(warmups.get(0).request().model().provider()).isEqualTo(ProviderType.OLLAMA);
        assertThat(warmups.get(0).context().repeatNo()).isZero();

        assertThat(calls).allSatisfy(c -> assertThat(c.context().runId()).isEqualTo(42L));
        assertThat(calls.stream().filter(c -> c.request().model().provider() == ProviderType.OLLAMA))
                .allSatisfy(c -> assertThat(c.request().params().maxTokens()).isEqualTo(4096));
        assertThat(calls.stream().filter(c -> c.request().model().provider() == ProviderType.OPENROUTER))
                .allSatisfy(c -> assertThat(c.request().params().maxTokens()).isEqualTo(512));
    }
}
