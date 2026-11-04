package com.example.llmplayground.llm;

public enum CallStatus {
    /** Non-empty answer. */
    OK,
    /** The call failed: exception, timeout, HTTP error. */
    ERROR,
    /** The call succeeded but the answer is empty (typically finish_reason=length on a reasoning model). */
    EMPTY
}
