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

/** A task for the models, e.g. "write a Spring controller and repository". */
@Entity
@Table(name = "prompt")
@Getter
@Setter
@NoArgsConstructor
public class Prompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "text", nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Prompt(String name, String text, Instant createdAt) {
        this.name = name;
        this.text = text;
        this.createdAt = createdAt;
    }
}
