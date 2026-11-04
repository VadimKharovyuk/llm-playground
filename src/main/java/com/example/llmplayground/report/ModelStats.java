package com.example.llmplayground.report;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Aggregated metrics of one model (in a run, for a prompt, or overall).
 * Counters cover measured calls (warm-up excluded); timing and token stats cover measured
 * OK calls with the model already loaded; the cost covers every call, warm-up included.
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
        Long coldStartMs,
        Double avgRating,
        long rated,
        long runs,
        Instant lastUsed) {

    public String ref() {
        return provider.toLowerCase(java.util.Locale.ROOT) + ":" + model;
    }
}
