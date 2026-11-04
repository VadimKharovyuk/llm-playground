package com.example.llmplayground.llm;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Routes a request to its provider, measures the call and classifies the outcome.
 * Providers stay free of timing code; this is the only place a call is timed.
 */
public class MeteredLlmGateway implements LlmGateway {

    private final Map<ProviderType, LlmProvider> providers = new EnumMap<>(ProviderType.class);
    private final Clock clock;

    public MeteredLlmGateway(Collection<? extends LlmProvider> providers) {
        this(providers, Clock.systemUTC());
    }

    public MeteredLlmGateway(Collection<? extends LlmProvider> providers, Clock clock) {
        providers.forEach(p -> this.providers.put(p.type(), p));
        this.clock = clock;
    }

    @Override
    public LlmResult call(LlmRequest request, CallContext context) {
        Instant startedAt = clock.instant();
        long start = System.nanoTime();
        LlmProvider provider = providers.get(request.model().provider());
        if (provider == null) {
            return error(request, startedAt, 0, "Provider " + request.model().provider().prefix()
                    + " is disabled (CHAT_PROVIDERS)");
        }

        ProviderResponse response;
        try {
            response = provider.call(request.model().model(), request.prompt(), request.params());
        } catch (Exception | LinkageError | AssertionError e) {
            long durationMs = elapsedMs(start);
            return error(request, startedAt, durationMs, ErrorDescriber.describe(e));
        }
        long durationMs = elapsedMs(start);
        if (response == null) {
            return error(request, startedAt, durationMs, "Provider returned no response");
        }

        BigDecimal cost = safeCost(provider, response);
        String finishReason = normalizeFinishReason(response.finishReason());
        CallStatus status = response.text() == null || response.text().isBlank() ? CallStatus.EMPTY : CallStatus.OK;
        String note = status == CallStatus.EMPTY
                ? "Empty answer" + (finishReason != null ? " (finish_reason=" + finishReason + ")" : "")
                : null;
        Double tps = TokensPerSecond.compute(response.completionTokens(), response.ollamaMetrics(),
                durationMs, response.ttftMs());

        return new LlmResult(request.model(), request.promptName(), startedAt, durationMs, response.ttftMs(),
                response.promptTokens(), response.completionTokens(), response.totalTokens(), tps,
                finishReason, cost, status, note, response.responseId(), response.ollamaMetrics(), response.text());
    }

    private static BigDecimal safeCost(LlmProvider provider, ProviderResponse response) {
        try {
            return provider.resolveCost(response);
        } catch (RuntimeException e) {
            return response.costUsd();
        }
    }

    static String normalizeFinishReason(String finishReason) {
        if (finishReason == null || finishReason.isBlank()) {
            return null;
        }
        return finishReason.strip().toLowerCase(Locale.ROOT);
    }

    private static LlmResult error(LlmRequest request, Instant startedAt, long durationMs, String message) {
        return new LlmResult(request.model(), request.promptName(), startedAt, durationMs, null,
                null, null, null, null, null, null, CallStatus.ERROR, message, null, null, null);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
