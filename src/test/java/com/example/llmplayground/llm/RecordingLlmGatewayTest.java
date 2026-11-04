package com.example.llmplayground.llm;

import com.example.llmplayground.persistence.LlmCall;
import com.example.llmplayground.persistence.LlmCallRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordingLlmGatewayTest {

    private static final ModelRef MODEL = ModelRef.parse("ollama:llama3.2:3b");
    private static final LlmRequest REQUEST =
            new LlmRequest(MODEL, "greeting", "Say hi", new LlmParams(0.2, 256, true));

    private static LlmResult result(CallStatus status, String error, OllamaMetrics ollama) {
        return new LlmResult(MODEL, "greeting", Instant.parse("2026-11-04T10:00:00Z"), 1234, 210L,
                11, 42, 53, 37.5, "stop", BigDecimal.ZERO, status, error, "resp-1", ollama,
                status == CallStatus.OK ? "Hi there" : null);
    }

    @Test
    void storesAllMetricsOfACall() {
        LlmCallRepository repository = mock(LlmCallRepository.class);
        OllamaMetrics ollama = new OllamaMetrics(42, 1_120_000_000L, 11, 30_000_000L, 4_000_000L);
        RecordingLlmGateway gateway = new RecordingLlmGateway((r, c) -> result(CallStatus.OK, null, ollama), repository);

        gateway.call(REQUEST, new CallContext(7L, 2, false));

        ArgumentCaptor<LlmCall> captor = ArgumentCaptor.forClass(LlmCall.class);
        verify(repository).save(captor.capture());
        LlmCall call = captor.getValue();
        assertThat(call.getRunId()).isEqualTo(7L);
        assertThat(call.getProvider()).isEqualTo(ProviderType.OLLAMA);
        assertThat(call.getModel()).isEqualTo("llama3.2:3b");
        assertThat(call.getPromptName()).isEqualTo("greeting");
        assertThat(call.getRepeatNo()).isEqualTo(2);
        assertThat(call.getStartedAt()).isEqualTo(Instant.parse("2026-11-04T10:00:00Z"));
        assertThat(call.getDurationMs()).isEqualTo(1234);
        assertThat(call.getTtftMs()).isEqualTo(210L);
        assertThat(call.getPromptTokens()).isEqualTo(11);
        assertThat(call.getCompletionTokens()).isEqualTo(42);
        assertThat(call.getTotalTokens()).isEqualTo(53);
        assertThat(call.getTokensPerSecond()).isEqualTo(37.5);
        assertThat(call.getFinishReason()).isEqualTo("stop");
        assertThat(call.getCostUsd()).isEqualByComparingTo("0");
        assertThat(call.isColdStart()).isFalse();
        assertThat(call.getStatus()).isEqualTo(CallStatus.OK);
        assertThat(call.getResponseId()).isEqualTo("resp-1");
        assertThat(call.getEvalCount()).isEqualTo(42);
        assertThat(call.getEvalDurationNs()).isEqualTo(1_120_000_000L);
        assertThat(call.getPromptEvalCount()).isEqualTo(11);
        assertThat(call.getPromptEvalDurationNs()).isEqualTo(30_000_000L);
        assertThat(call.getLoadDurationNs()).isEqualTo(4_000_000L);
        assertThat(call.getResponseText()).isEqualTo("Hi there");
    }

    @Test
    void storesErrorsWithReason() {
        LlmCallRepository repository = mock(LlmCallRepository.class);
        RecordingLlmGateway gateway = new RecordingLlmGateway(
                (r, c) -> result(CallStatus.ERROR, "TIMEOUT: request timed out", null), repository);

        LlmResult returned = gateway.call(REQUEST, new CallContext(7L, 1, false));

        ArgumentCaptor<LlmCall> captor = ArgumentCaptor.forClass(LlmCall.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CallStatus.ERROR);
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("TIMEOUT: request timed out");
        assertThat(returned.status()).isEqualTo(CallStatus.ERROR);
    }

    @Test
    void marksWarmUpAndModelLoadsAsColdStart() {
        LlmCallRepository repository = mock(LlmCallRepository.class);
        OllamaMetrics loaded = new OllamaMetrics(42, 1_000_000_000L, 11, 30_000_000L, 3_500_000_000L);
        RecordingLlmGateway gateway = new RecordingLlmGateway((r, c) -> result(CallStatus.OK, null, loaded), repository);

        gateway.call(REQUEST, new CallContext(7L, 3, false));   // model was evicted and reloaded

        ArgumentCaptor<LlmCall> captor = ArgumentCaptor.forClass(LlmCall.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isColdStart()).isTrue();
    }

    @Test
    void databaseFailureDoesNotEscape() {
        LlmCallRepository repository = mock(LlmCallRepository.class);
        when(repository.save(any())).thenThrow(new IllegalStateException("db down"));
        RecordingLlmGateway gateway = new RecordingLlmGateway((r, c) -> result(CallStatus.OK, null, null), repository);

        assertThat(gateway.call(REQUEST, new CallContext(7L, 1, false)).status()).isEqualTo(CallStatus.OK);
    }

    @Test
    void callsWithoutRunAreNotStored() {
        LlmCallRepository repository = mock(LlmCallRepository.class);
        new RecordingLlmGateway((r, c) -> result(CallStatus.OK, null, null), repository)
                .call(REQUEST, new CallContext(null, 1, false));
        verify(repository, never()).save(any());
    }
}
