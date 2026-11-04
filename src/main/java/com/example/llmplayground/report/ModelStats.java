package com.example.llmplayground.report;

import java.math.BigDecimal;

/**
 * Aggregated metrics of one model in one run. Counters cover all measured calls (warm-up excluded);
 * timing and token stats cover measured OK calls with the model already loaded;
 * the cost covers every call, warm-up included.
 */
public record ModelStats(
        String provider,
        String model,
        long calls,
        long ok,
        long errors,
        long empty,
        Double avgMs,
        Long minMs,
        Long maxMs,
        Double p50Ms,
        Double avgTtftMs,
        Double avgPromptTokens,
        Double avgCompletionTokens,
        Double avgTokensPerSecond,
        BigDecimal totalCostUsd,
        Long coldStartMs) {
}
