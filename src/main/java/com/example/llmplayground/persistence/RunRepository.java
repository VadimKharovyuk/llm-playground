package com.example.llmplayground.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RunRepository extends JpaRepository<Run, Long> {

    Optional<Run> findTopByOrderByIdDesc();
}
