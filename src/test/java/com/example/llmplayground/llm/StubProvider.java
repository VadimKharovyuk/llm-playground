package com.example.llmplayground.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Network-free provider for tests: returns a canned response or throws. */
public class StubProvider implements LlmProvider {

    private final ProviderType type;
    private final Supplier<ProviderResponse> behaviour;
    public final List<String> calledModels = new ArrayList<>();

    public StubProvider(ProviderType type, Supplier<ProviderResponse> behaviour) {
        this.type = type;
        this.behaviour = behaviour;
    }

    public static ProviderResponse answer(String text, Integer completionTokens, String finishReason) {
        return new ProviderResponse(text, 10, completionTokens, completionTokens == null ? null : 10 + completionTokens,
                finishReason, "gen-1", null, null, null);
    }

    @Override
    public ProviderType type() {
        return type;
    }

    @Override
    public ProviderResponse call(String model, String prompt, LlmParams params) {
        calledModels.add(model);
        return behaviour.get();
    }
}
