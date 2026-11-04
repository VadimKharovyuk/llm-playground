package com.example.llmplayground.llm.provider;

import com.example.llmplayground.llm.OllamaMetrics;
import com.example.llmplayground.llm.ProviderResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared call logic for Spring AI {@link ChatModel}s: blocking or streaming call,
 * and extraction of text, usage, finish reason and Ollama counters from the response.
 */
public final class ChatModelExecutor {

    private ChatModelExecutor() {
    }

    public static ProviderResponse execute(ChatModel chatModel, String promptText, ChatOptions options,
                                           boolean streaming, Duration streamIdleTimeout) {
        Prompt prompt = new Prompt(new UserMessage(promptText), options);
        return streaming
                ? stream(chatModel, prompt, streamIdleTimeout)
                : fromSingle(chatModel.call(prompt));
    }

    private static ProviderResponse fromSingle(ChatResponse response) {
        if (response == null) {
            return new ProviderResponse(null, null, null, null, null, null, null, null, null);
        }
        StringBuilder text = new StringBuilder();
        String finishReason = null;
        for (Generation generation : response.getResults()) {
            appendText(text, generation);
            String reason = finishReasonOf(generation);
            if (reason != null) {
                finishReason = reason;
            }
        }
        return build(text.toString(), finishReason, response.getMetadata(), response.getMetadata(), null);
    }

    private static ProviderResponse stream(ChatModel chatModel, Prompt prompt, Duration idleTimeout) {
        long start = System.nanoTime();
        AtomicLong firstTokenNanos = new AtomicLong(-1);

        List<ChatResponse> chunks = chatModel.stream(prompt)
                .doOnNext(chunk -> {
                    if (firstTokenNanos.get() < 0 && hasText(chunk)) {
                        firstTokenNanos.compareAndSet(-1, System.nanoTime());
                    }
                })
                .timeout(idleTimeout)
                .collectList()
                .block();

        StringBuilder text = new StringBuilder();
        String finishReason = null;
        ChatResponseMetadata usageMetadata = null;
        ChatResponseMetadata lastMetadata = null;
        String responseId = null;
        for (ChatResponse chunk : chunks == null ? List.<ChatResponse>of() : chunks) {
            for (Generation generation : chunk.getResults()) {
                appendText(text, generation);
                String reason = finishReasonOf(generation);
                if (reason != null) {
                    finishReason = reason;
                }
            }
            ChatResponseMetadata metadata = chunk.getMetadata();
            if (metadata != null) {
                lastMetadata = metadata;
                if (responseId == null && hasText(metadata.getId())) {
                    responseId = metadata.getId();
                }
                if (hasUsage(metadata.getUsage())) {
                    usageMetadata = metadata;
                }
            }
        }
        Long ttftMs = firstTokenNanos.get() < 0 ? null : (firstTokenNanos.get() - start) / 1_000_000L;
        ProviderResponse response = build(text.toString(), finishReason, usageMetadata, lastMetadata, ttftMs);
        if (response.responseId() == null && responseId != null) {
            return new ProviderResponse(response.text(), response.promptTokens(), response.completionTokens(),
                    response.totalTokens(), response.finishReason(), responseId, response.ttftMs(),
                    response.costUsd(), response.ollamaMetrics());
        }
        return response;
    }

    private static ProviderResponse build(String text, String finishReason, ChatResponseMetadata usageMetadata,
                                          ChatResponseMetadata lastMetadata, Long ttftMs) {
        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer totalTokens = null;
        if (usageMetadata != null && hasUsage(usageMetadata.getUsage())) {
            Usage usage = usageMetadata.getUsage();
            promptTokens = usage.getPromptTokens();
            completionTokens = usage.getCompletionTokens();
            totalTokens = usage.getTotalTokens();
        }
        String id = lastMetadata != null && hasText(lastMetadata.getId()) ? lastMetadata.getId() : null;
        OllamaMetrics ollama = lastMetadata == null ? null : ollamaMetrics(lastMetadata);
        return new ProviderResponse(text, promptTokens, completionTokens, totalTokens, finishReason,
                id, ttftMs, null, ollama);
    }

    /** Ollama puts its counters into the response metadata under these keys. */
    static OllamaMetrics ollamaMetrics(ChatResponseMetadata metadata) {
        OllamaMetrics metrics = new OllamaMetrics(
                toInteger(metadata.get("eval-count")),
                toNanos(metadata.get("eval-duration")),
                toInteger(metadata.get("prompt-eval-count")),
                toNanos(metadata.get("prompt-eval-duration")),
                toNanos(metadata.get("load-duration")));
        return metrics.isEmpty() ? null : metrics;
    }

    private static Integer toInteger(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static Long toNanos(Object value) {
        if (value instanceof Duration d) {
            return d.toNanos();
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    private static void appendText(StringBuilder sb, Generation generation) {
        if (generation != null && generation.getOutput() != null && generation.getOutput().getText() != null) {
            sb.append(generation.getOutput().getText());
        }
    }

    private static String finishReasonOf(Generation generation) {
        if (generation == null || generation.getMetadata() == null) {
            return null;
        }
        String reason = generation.getMetadata().getFinishReason();
        return hasText(reason) ? reason : null;
    }

    private static boolean hasText(ChatResponse chunk) {
        for (Generation generation : chunk.getResults()) {
            if (generation != null && generation.getOutput() != null && hasText(generation.getOutput().getText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUsage(Usage usage) {
        if (usage == null) {
            return false;
        }
        return positive(usage.getTotalTokens()) || positive(usage.getPromptTokens())
                || positive(usage.getCompletionTokens());
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }
}
