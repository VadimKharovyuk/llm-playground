package com.example.llmplayground.persistence;

import com.example.llmplayground.llm.CallStatus;
import com.example.llmplayground.llm.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One LLM call with its metrics. The answer is stored in {@code response_text};
 * reports read metrics through plain SQL and never touch that column.
 */
@Entity
@Table(name = "llm_call")
@Getter
@Setter
@NoArgsConstructor
public class LlmCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private ProviderType provider;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "prompt_name", nullable = false)
    private String promptName;

    /** 1..N for measured calls, 0 for a warm-up call. */
    @Column(name = "repeat_no", nullable = false)
    private int repeatNo;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "ttft_ms")
    private Long ttftMs;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "tokens_per_second")
    private Double tokensPerSecond;

    @Column(name = "finish_reason", length = 64)
    private String finishReason;

    @Column(name = "cost_usd", precision = 18, scale = 10)
    private BigDecimal costUsd;

    @Column(name = "cold_start", nullable = false)
    private boolean coldStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CallStatus status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "response_id")
    private String responseId;

    @Column(name = "eval_count")
    private Integer evalCount;

    @Column(name = "eval_duration_ns")
    private Long evalDurationNs;

    @Column(name = "prompt_eval_count")
    private Integer promptEvalCount;

    @Column(name = "prompt_eval_duration_ns")
    private Long promptEvalDurationNs;

    @Column(name = "load_duration_ns")
    private Long loadDurationNs;

    @Column(name = "response_text", columnDefinition = "text")
    private String responseText;
}
