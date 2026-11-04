package com.example.llmplayground.llm;

import java.math.BigDecimal;

/**
 * One LLM backend. Implementations only talk to the model: they may throw,
 * timing and error handling are done by {@link MeteredLlmGateway}.
 */
public interface LlmProvider {

    ProviderType type();

    ProviderResponse call(String model, String prompt, LlmParams params) throws Exception;

    /**
     * Cost of a finished call. Called outside the measured time, so a provider may do
     * an extra lookup here. Must not throw; returns null when the cost is unknown.
     */
    default BigDecimal resolveCost(ProviderResponse response) {
        return response.costUsd();
    }
}
