package com.example.llmplayground.llm;

/**
 * Where a call belongs: the run, the repeat number (0 = warm-up) and whether it is a cold start.
 * Not needed to make the call itself, only to record it.
 */
public record CallContext(Long runId, int repeatNo, boolean coldStart) {
}
