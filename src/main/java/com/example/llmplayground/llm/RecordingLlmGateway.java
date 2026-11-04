package com.example.llmplayground.llm;

import com.example.llmplayground.persistence.LlmCall;
import com.example.llmplayground.persistence.LlmCallRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Decorator that stores every call (successful or not) in {@code llm_call}.
 * Logs metrics only — never prompts, answers or keys.
 */
public class RecordingLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(RecordingLlmGateway.class);

    private final LlmGateway delegate;
    private final LlmCallRepository repository;

    public RecordingLlmGateway(LlmGateway delegate, LlmCallRepository repository) {
        this.delegate = delegate;
        this.repository = repository;
    }

    @Override
    public LlmResult call(LlmRequest request, CallContext context) {
        LlmResult result = delegate.call(request, context);
        boolean coldStart = context.coldStart() || result.modelWasLoaded();
        log.info(String.format(Locale.ROOT, "%-45s %-24s #%d %-5s %6d ms ttft=%s tokens=%s tps=%s%s",
                request.model(), request.promptName(), context.repeatNo(), result.status(), result.durationMs(),
                result.ttftMs() == null ? "-" : result.ttftMs() + "ms",
                result.completionTokens() == null ? "-" : result.completionTokens(),
                result.tokensPerSecond() == null ? "-" : String.format(Locale.ROOT, "%.1f", result.tokensPerSecond()),
                coldStart ? " [cold]" : ""));
        if (result.status() == CallStatus.ERROR) {
            log.warn("  {} failed: {}", request.model(), result.errorMessage());
        }
        if (context.runId() == null) {
            return result;
        }
        try {
            repository.save(toEntity(result, context, coldStart));
        } catch (RuntimeException e) {
            log.error("Could not store call of {} ({}): {}", request.model(), request.promptName(),
                    ErrorDescriber.describe(e));
        }
        return result;
    }

    static LlmCall toEntity(LlmResult result, CallContext context, boolean coldStart) {
        LlmCall call = new LlmCall();
        call.setRunId(context.runId());
        call.setProvider(result.model().provider());
        call.setModel(result.model().model());
        call.setPromptName(result.promptName());
        call.setRepeatNo(context.repeatNo());
        call.setStartedAt(result.startedAt());
        call.setDurationMs(result.durationMs());
        call.setTtftMs(result.ttftMs());
        call.setPromptTokens(result.promptTokens());
        call.setCompletionTokens(result.completionTokens());
        call.setTotalTokens(result.totalTokens());
        call.setTokensPerSecond(result.tokensPerSecond());
        call.setFinishReason(result.finishReason());
        call.setCostUsd(result.costUsd());
        call.setColdStart(coldStart);
        call.setStatus(result.status());
        call.setErrorMessage(result.errorMessage());
        call.setResponseId(result.responseId());
        OllamaMetrics ollama = result.ollamaMetrics();
        if (ollama != null) {
            call.setEvalCount(ollama.evalCount());
            call.setEvalDurationNs(ollama.evalDurationNs());
            call.setPromptEvalCount(ollama.promptEvalCount());
            call.setPromptEvalDurationNs(ollama.promptEvalDurationNs());
            call.setLoadDurationNs(ollama.loadDurationNs());
        }
        call.setResponseText(result.text());
        return call;
    }
}
