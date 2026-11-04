package com.example.llmplayground.prompt;

import com.example.llmplayground.persistence.Prompt;
import com.example.llmplayground.persistence.PromptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Adds the example prompts from {@code classpath:prompts/*.txt} to the table, once per name. */
@Component
public class PromptSeeder {

    private static final Logger log = LoggerFactory.getLogger(PromptSeeder.class);

    private final PromptRepository prompts;

    public PromptSeeder(PromptRepository prompts) {
        this.prompts = prompts;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:prompts/*.txt");
        int added = 0;
        for (Resource file : files) {
            String fileName = file.getFilename();
            if (fileName == null) {
                continue;
            }
            String name = fileName.substring(0, fileName.lastIndexOf('.'));
            String text = file.getContentAsString(StandardCharsets.UTF_8).strip();
            if (!text.isEmpty() && !prompts.existsByName(name)) {
                prompts.save(new Prompt(name, text, Instant.now()));
                added++;
            }
        }
        if (added > 0) {
            log.info("Added {} example prompt(s) from classpath:prompts/", added);
        }
    }
}
