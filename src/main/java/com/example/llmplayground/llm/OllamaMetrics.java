package com.example.llmplayground.llm;

/** Ollama's own timing counters (eval_count, eval_duration, load_duration, ...), durations in nanoseconds. */
public record OllamaMetrics(
        Integer evalCount,
        Long evalDurationNs,
        Integer promptEvalCount,
        Long promptEvalDurationNs,
        Long loadDurationNs) {

    public boolean isEmpty() {
        return evalCount == null && evalDurationNs == null && promptEvalCount == null
                && promptEvalDurationNs == null && loadDurationNs == null;
    }
}
