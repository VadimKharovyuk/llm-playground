package com.example.llmplayground.prompt;

import com.example.llmplayground.NotFoundException;
import com.example.llmplayground.persistence.LlmCallRepository;
import com.example.llmplayground.persistence.Prompt;
import com.example.llmplayground.persistence.PromptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Prompts live in the {@code prompt} table. A prompt that has answers is never changed in place:
 * editing it creates a new prompt, so every stored answer keeps the exact text it was given.
 */
@Service
public class PromptService {

    static final int MAX_NAME = 255;
    private static final DateTimeFormatter AUTO_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PromptRepository prompts;
    private final LlmCallRepository calls;
    private final Clock clock;

    @Autowired
    public PromptService(PromptRepository prompts, LlmCallRepository calls) {
        this(prompts, calls, Clock.systemDefaultZone());
    }

    PromptService(PromptRepository prompts, LlmCallRepository calls, Clock clock) {
        this.prompts = prompts;
        this.calls = calls;
        this.clock = clock;
    }

    public List<Prompt> all() {
        return prompts.findAllByOrderByIdDesc();
    }

    public Prompt get(long id) {
        return prompts.findById(id).orElseThrow(() -> new NotFoundException("Prompt #" + id + " not found"));
    }

    public boolean isUsed(long id) {
        return calls.existsByPromptId(id);
    }

    /** Creates a prompt; a blank name becomes "task <date time>", a taken name gets a suffix. */
    @Transactional
    public Prompt create(String name, String text) {
        String body = requireText(text);
        String base = name == null || name.isBlank()
                ? "task " + LocalDateTime.now(clock).format(AUTO_NAME)
                : name.strip();
        return prompts.save(new Prompt(uniqueName(base), body, clock.instant()));
    }

    /**
     * Updates an unused prompt in place; for a used one creates a new prompt instead.
     *
     * @return the prompt that now holds the text (same or new)
     */
    @Transactional
    public Prompt update(long id, String name, String text) {
        Prompt prompt = get(id);
        String body = requireText(text);
        String newName = name == null || name.isBlank() ? prompt.getName() : name.strip();
        if (isUsed(id)) {
            if (body.equals(prompt.getText())) {
                return prompt;
            }
            return prompts.save(new Prompt(uniqueName(newName), body, clock.instant()));
        }
        if (!newName.equals(prompt.getName())) {
            prompt.setName(uniqueName(newName));
        }
        prompt.setText(body);
        return prompts.save(prompt);
    }

    @Transactional
    public void delete(long id) {
        if (isUsed(id)) {
            throw new IllegalStateException("The prompt already has answers and cannot be deleted");
        }
        prompts.delete(get(id));
    }

    String uniqueName(String base) {
        String trimmed = base.length() > MAX_NAME - 6 ? base.substring(0, MAX_NAME - 6) : base;
        if (!prompts.existsByName(trimmed)) {
            return trimmed;
        }
        for (int i = 2; ; i++) {
            String candidate = trimmed + " (" + i + ")";
            if (!prompts.existsByName(candidate)) {
                return candidate;
            }
        }
    }

    private static String requireText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Prompt text is empty");
        }
        return text.strip();
    }
}
