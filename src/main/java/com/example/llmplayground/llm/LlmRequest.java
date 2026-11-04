package com.example.llmplayground.llm;

/** What to call: model, prompt and parameters. */
public record LlmRequest(ModelRef model, String promptName, String prompt, LlmParams params) {
}
