package com.example.llmplayground.llm;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum ProviderType {
    OPENROUTER("openrouter"),
    OLLAMA("ollama");

    private final String prefix;

    ProviderType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    public static ProviderType fromPrefix(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (ProviderType type : values()) {
            if (type.prefix.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown provider '" + value + "', expected one of: "
                + Arrays.stream(values()).map(ProviderType::prefix).collect(Collectors.joining(", ")));
    }
}
