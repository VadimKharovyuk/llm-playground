package com.example.llmplayground.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TokensPerSecondTest {

    @Test
    void usesOllamaCountersWhenPresent() {
        // 120 tokens in 2 s of pure generation -> 60 tok/s, regardless of wall time
        OllamaMetrics ollama = new OllamaMetrics(120, 2_000_000_000L, 30, 100_000_000L, 5_000_000L);
        assertThat(TokensPerSecond.compute(999, ollama, 10_000, 500L)).isCloseTo(60.0, within(1e-9));
    }

    @Test
    void excludesTimeToFirstTokenWhenStreaming() {
        // 100 tokens, 3000 ms total, first token after 1000 ms -> 100 / 2 s = 50 tok/s
        assertThat(TokensPerSecond.compute(100, null, 3000, 1000L)).isCloseTo(50.0, within(1e-9));
    }

    @Test
    void usesWallTimeWithoutStreaming() {
        assertThat(TokensPerSecond.compute(100, null, 4000, null)).isCloseTo(25.0, within(1e-9));
    }

    @Test
    void fallsBackToWallTimeWhenOllamaCountersAreIncomplete() {
        OllamaMetrics partial = new OllamaMetrics(null, null, null, null, 1L);
        assertThat(TokensPerSecond.compute(50, partial, 1000, null)).isCloseTo(50.0, within(1e-9));
    }

    @Test
    void returnsNullWhenNotComputable() {
        assertThat(TokensPerSecond.compute(null, null, 1000, null)).isNull();
        assertThat(TokensPerSecond.compute(0, null, 1000, null)).isNull();
        assertThat(TokensPerSecond.compute(10, null, 0, null)).isNull();
    }
}
