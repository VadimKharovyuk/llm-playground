package com.example.llmplayground.llm;

/**
 * A model together with its provider, written as {@code provider:model}.
 * Only the first colon separates the provider, so Ollama tags work: {@code ollama:llama3.2:3b}.
 */
public record ModelRef(ProviderType provider, String model) {

    public ModelRef {
        if (provider == null) {
            throw new IllegalArgumentException("provider is required");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
    }

    public static ModelRef parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Model reference is empty, expected 'provider:model'");
        }
        String trimmed = value.trim();
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || colon == trimmed.length() - 1) {
            throw new IllegalArgumentException("Model reference '" + value + "' must look like 'provider:model', "
                    + "e.g. 'openrouter:anthropic/claude-haiku-5.5' or 'ollama:llama3.2:3b'");
        }
        return new ModelRef(ProviderType.fromPrefix(trimmed.substring(0, colon)), trimmed.substring(colon + 1).trim());
    }

    @Override
    public String toString() {
        return provider.prefix() + ":" + model;
    }
}
