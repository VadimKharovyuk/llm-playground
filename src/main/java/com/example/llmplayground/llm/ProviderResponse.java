package com.example.llmplayground.llm;

import java.math.BigDecimal;

/** Raw outcome of a provider call, before timing and status classification. */
public record ProviderResponse(
        String text,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String finishReason,
        String responseId,
        Long ttftMs,
        BigDecimal costUsd,
        OllamaMetrics ollamaMetrics) {

    public ProviderResponse withCost(BigDecimal cost) {
        return new ProviderResponse(text, promptTokens, completionTokens, totalTokens, finishReason,
                responseId, ttftMs, cost, ollamaMetrics);
    }
}
