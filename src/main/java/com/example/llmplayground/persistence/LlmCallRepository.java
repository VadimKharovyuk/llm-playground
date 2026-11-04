package com.example.llmplayground.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LlmCallRepository extends JpaRepository<LlmCall, Long> {

    long countByRunId(Long runId);

    boolean existsByPromptId(Long promptId);

    List<LlmCall> findByRunIdOrderByIdAsc(Long runId);

    /** Latest measured answers to a prompt across all runs (warm-up calls excluded). */
    List<LlmCall> findTop100ByPromptIdAndRepeatNoGreaterThanOrderByIdDesc(Long promptId, int repeatNo);
}
