package com.example.llmplayground.llm;

import java.math.BigDecimal;
import java.time.Instant;

/** Measured result of one call. Produced for failed calls too (status ERROR). */
public record LlmResult(
        ModelRef model,
        String promptName,
        Instant startedAt,
        long durationMs,
        Long ttftMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Double tokensPerSecond,
        String finishReason,
        BigDecimal costUsd,
        CallStatus status,
        String errorMessage,
        String responseId,
        OllamaMetrics ollamaMetrics,
        String text) {

    /** Ollama loaded the model from disk during this call (more than a second of load_duration). */
    public boolean modelWasLoaded() {
        return ollamaMetrics != null && ollamaMetrics.loadDurationNs() != null
                && ollamaMetrics.loadDurationNs() > 1_000_000_000L;
    }
}
