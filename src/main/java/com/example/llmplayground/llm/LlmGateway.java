package com.example.llmplayground.llm;

/** The single entry point for calling an LLM. Never throws: failures come back as {@link CallStatus#ERROR}. */
public interface LlmGateway {

    LlmResult call(LlmRequest request, CallContext context);
}
