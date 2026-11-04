package com.example.llmplayground.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One benchmark run. */
@Entity
@Table(name = "run")
@Getter
@Setter
@NoArgsConstructor
public class Run {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** Comma-separated {@code provider:model} list. */
    @Column(name = "models", nullable = false, columnDefinition = "text")
    private String models;

    @Column(name = "prompts", columnDefinition = "text")
    private String prompts;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "runs_per_prompt", nullable = false)
    private int runsPerPrompt;

    @Column(name = "streaming", nullable = false)
    private boolean streaming;

    @Column(name = "app_version", length = 64)
    private String appVersion;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;
}
