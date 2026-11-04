package com.example.llmplayground.runner;

import com.example.llmplayground.llm.LlmParams;
import com.example.llmplayground.llm.ModelRef;
import com.example.llmplayground.llm.ProviderType;

import java.util.List;

/** Everything a background run needs, fixed at the moment the run is started. */
public record RunPlan(Long runId, List<ModelPlan> models, List<PlannedPrompt> prompts, int repeats, boolean warmup) {

    public record ModelPlan(ModelRef model, LlmParams params) {
    }

    public record PlannedPrompt(Long id, String name, String text) {
    }

    /** Measured calls plus one warm-up per Ollama model. */
    public int expectedCalls() {
        long ollama = models.stream().filter(m -> m.model().provider() == ProviderType.OLLAMA).count();
        return models.size() * prompts.size() * repeats + (warmup ? (int) ollama : 0);
    }
}
