package com.example.llmplayground;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LlmPlaygroundApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmPlaygroundApplication.class, args);
    }
}
