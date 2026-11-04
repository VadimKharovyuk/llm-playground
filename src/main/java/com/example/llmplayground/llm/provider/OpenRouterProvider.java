package com.example.llmplayground.llm.provider;

import com.example.llmplayground.llm.LlmParams;
import com.example.llmplayground.llm.LlmProvider;
import com.example.llmplayground.llm.ProviderResponse;
import com.example.llmplayground.llm.ProviderType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.math.BigDecimal;
import java.time.Duration;

/** Cloud models through OpenRouter's OpenAI-compatible API. */
public class OpenRouterProvider implements LlmProvider {

    private final ChatModel chatModel;
    private final boolean apiKeyPresent;
    private final OpenRouterCostClient costClient;
    private final Duration streamIdleTimeout;

    public OpenRouterProvider(ChatModel chatModel, boolean apiKeyPresent, OpenRouterCostClient costClient,
                              Duration streamIdleTimeout) {
        this.chatModel = chatModel;
        this.apiKeyPresent = apiKeyPresent;
        this.costClient = costClient;
        this.streamIdleTimeout = streamIdleTimeout;
    }

    @Override
    public ProviderType type() {
        return ProviderType.OPENROUTER;
    }

    @Override
    public ProviderResponse call(String model, String prompt, LlmParams params) {
        if (!apiKeyPresent) {
            throw new IllegalStateException("OPENROUTER_API_KEY is not set (environment variable or .env)");
        }
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(params.temperature())
                .maxTokens(params.maxTokens())
                .streamUsage(true)   // ask for token usage in the last stream chunk
                .build();
        return ChatModelExecutor.execute(chatModel, prompt, options, params.streaming(), streamIdleTimeout);
    }

    @Override
    public BigDecimal resolveCost(ProviderResponse response) {
        if (costClient == null || response.responseId() == null) {
            return response.costUsd();
        }
        BigDecimal cost = costClient.fetchCost(response.responseId());
        return cost != null ? cost : response.costUsd();
    }
}
