package com.example.llmplayground;

import com.example.llmplayground.llm.LlmGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** Full context on a real PostgreSQL (Flyway + schema validation). No LLM is called. */
@SpringBootTest(properties = "playground.mode=none")
@Import(PostgresContainerConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class LlmPlaygroundApplicationTests {

    @Autowired
    LlmGateway gateway;

    @Test
    void contextLoads() {
        assertThat(gateway).isNotNull();
    }
}
