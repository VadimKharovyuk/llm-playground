package com.example.llmplayground.report;

import java.math.BigDecimal;
import java.time.Instant;

/** One call's metrics for the per-call CSV (without the answer text). */
public record CallRow(
        long id,
        String provider,
        String model,
        String promptName,
        int repeatNo,
        Instant startedAt,
        long durationMs,
        Long ttftMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Double tokensPerSecond,
        String finishReason,
        BigDecimal costUsd,
        boolean coldStart,
        String status,
        String errorMessage) {
}
