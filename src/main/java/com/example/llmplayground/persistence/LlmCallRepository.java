package com.example.llmplayground.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmCallRepository extends JpaRepository<LlmCall, Long> {

    long countByRunId(Long runId);
}
