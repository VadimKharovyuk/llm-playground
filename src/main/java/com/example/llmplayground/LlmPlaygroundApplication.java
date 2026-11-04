package com.example.llmplayground;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LlmPlaygroundApplication {

    public static void main(String[] args) {
        // Console app: close the context and exit when the runner is done.
        System.exit(SpringApplication.exit(SpringApplication.run(LlmPlaygroundApplication.class, args)));
    }
}
