package com.example.llmplayground.prompt;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test prompts from {@code classpath:prompts/*.txt|*.md}; the name is the file name without extension. */
@Component
public class PromptLibrary {

    private final Map<String, Prompt> prompts;

    public PromptLibrary() {
        this(new PathMatchingResourcePatternResolver());
    }

    PromptLibrary(ResourcePatternResolver resolver) {
        this.prompts = load(resolver);
    }

    public List<Prompt> all() {
        return List.copyOf(prompts.values());
    }

    /** Selected prompts in the given order; an empty selection means all prompts. */
    public List<Prompt> select(List<String> names) {
        if (names == null || names.isEmpty()) {
            return all();
        }
        List<Prompt> result = new ArrayList<>();
        for (String name : names) {
            Prompt prompt = prompts.get(name.trim());
            if (prompt == null) {
                throw new IllegalArgumentException("Unknown prompt '" + name + "', available: " + prompts.keySet());
            }
            result.add(prompt);
        }
        return result;
    }

    private static Map<String, Prompt> load(ResourcePatternResolver resolver) {
        try {
            List<Resource> resources = new ArrayList<>();
            resources.addAll(List.of(resolver.getResources("classpath*:prompts/*.txt")));
            resources.addAll(List.of(resolver.getResources("classpath*:prompts/*.md")));
            resources.sort(Comparator.comparing(r -> String.valueOf(r.getFilename())));
            Map<String, Prompt> map = new LinkedHashMap<>();
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                if (fileName == null) {
                    continue;
                }
                String name = fileName.substring(0, fileName.lastIndexOf('.'));
                String text = resource.getContentAsString(StandardCharsets.UTF_8).strip();
                if (!text.isEmpty()) {
                    map.put(name, new Prompt(name, text));
                }
            }
            return map;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read prompts", e);
        }
    }

    public record Prompt(String name, String text) {
    }
}
