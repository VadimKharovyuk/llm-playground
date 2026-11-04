package com.example.llmplayground.llm;

/** Generation parameters of a single call. */
public record LlmParams(Double temperature, Integer maxTokens, boolean streaming) {
}
