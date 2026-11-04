package com.example.llmplayground.llm;

/** Generation speed in completion tokens per second. */
public final class TokensPerSecond {

    private TokensPerSecond() {
    }

    /**
     * Prefers Ollama's own counters (eval_count / eval_duration): they measure pure generation.
     * Otherwise uses completion tokens over wall time; when streaming, the time before the first
     * token (queueing, prompt processing) is excluded, so the value is the generation rate.
     *
     * @return tokens per second, or null when it cannot be computed
     */
    public static Double compute(Integer completionTokens, OllamaMetrics ollama, long durationMs, Long ttftMs) {
        if (ollama != null && ollama.evalCount() != null && ollama.evalCount() > 0
                && ollama.evalDurationNs() != null && ollama.evalDurationNs() > 0) {
            return ollama.evalCount() / (ollama.evalDurationNs() / 1_000_000_000.0);
        }
        if (completionTokens == null || completionTokens <= 0) {
            return null;
        }
        long generationMs = durationMs;
        if (ttftMs != null && ttftMs >= 0 && durationMs > ttftMs) {
            generationMs = durationMs - ttftMs;
        }
        if (generationMs <= 0) {
            return null;
        }
        return completionTokens * 1000.0 / generationMs;
    }
}
