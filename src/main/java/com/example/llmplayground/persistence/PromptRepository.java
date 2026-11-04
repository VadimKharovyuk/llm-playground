package com.example.llmplayground.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromptRepository extends JpaRepository<Prompt, Long> {

    boolean existsByName(String name);

    List<Prompt> findAllByOrderByIdDesc();
}
