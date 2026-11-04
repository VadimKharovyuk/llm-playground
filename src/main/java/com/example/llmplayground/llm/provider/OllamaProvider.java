package com.example.llmplayground.llm.provider;

import com.example.llmplayground.llm.LlmParams;
import com.example.llmplayground.llm.LlmProvider;
import com.example.llmplayground.llm.ProviderResponse;
import com.example.llmplayground.llm.ProviderType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import java.math.BigDecimal;
import java.time.Duration;

/** Local models through Ollama. Cost is always 0. */
public class OllamaProvider implements LlmProvider {

    private final ChatModel chatModel;
    private final Duration streamIdleTimeout;

    public OllamaProvider(ChatModel chatModel, Duration streamIdleTimeout) {
        this.chatModel = chatModel;
        this.streamIdleTimeout = streamIdleTimeout;
    }

    @Override
    public ProviderType type() {
        return ProviderType.OLLAMA;
    }

    @Override
    public ProviderResponse call(String model, String prompt, LlmParams params) {
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(model)
                .temperature(params.temperature())
                .numPredict(params.maxTokens())
                .build();
        return ChatModelExecutor.execute(chatModel, prompt, options, params.streaming(), streamIdleTimeout)
                .withCost(BigDecimal.ZERO);
    }
}
