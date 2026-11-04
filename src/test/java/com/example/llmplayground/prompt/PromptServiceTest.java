package com.example.llmplayground.prompt;

import com.example.llmplayground.persistence.LlmCallRepository;
import com.example.llmplayground.persistence.Prompt;
import com.example.llmplayground.persistence.PromptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptServiceTest {

    private final PromptRepository prompts = mock(PromptRepository.class);
    private final LlmCallRepository calls = mock(LlmCallRepository.class);
    private PromptService service;

    @BeforeEach
    void setUp() {
        service = new PromptService(prompts, calls,
                Clock.fixed(Instant.parse("2026-11-04T10:15:30Z"), ZoneOffset.UTC));
        when(prompts.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Prompt existing(String name, String text) {
        Prompt p = new Prompt(name, text, Instant.now());
        p.setId(1L);
        when(prompts.findById(1L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void blankNameGetsTimestampName() {
        assertThat(service.create("  ", " Write code ").getName()).isEqualTo("task 2026-11-04 10:15:30");
    }

    @Test
    void takenNameGetsSuffixAndTextIsStripped() {
        when(prompts.existsByName("book-api")).thenReturn(true);
        Prompt created = service.create("book-api", "  Write code \n");
        assertThat(created.getName()).isEqualTo("book-api (2)");
        assertThat(created.getText()).isEqualTo("Write code");
    }

    @Test
    void emptyTextIsRejected() {
        assertThatThrownBy(() -> service.create("x", "  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unusedPromptIsEditedInPlace() {
        Prompt p = existing("book-api", "old");
        Prompt saved = service.update(1L, "book-api", "new text");
        assertThat(saved).isSameAs(p);
        assertThat(p.getText()).isEqualTo("new text");
    }

    @Test
    void usedPromptIsCopiedSoOldAnswersKeepTheirText() {
        Prompt p = existing("book-api", "old");
        when(calls.existsByPromptId(1L)).thenReturn(true);
        when(prompts.existsByName("book-api")).thenReturn(true);

        Prompt saved = service.update(1L, "book-api", "new text");

        assertThat(saved).isNotSameAs(p);
        assertThat(saved.getName()).isEqualTo("book-api (2)");
        assertThat(saved.getText()).isEqualTo("new text");
        assertThat(p.getText()).isEqualTo("old");
    }

    @Test
    void usedPromptCannotBeDeleted() {
        existing("book-api", "old");
        when(calls.existsByPromptId(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class);
        verify(prompts, never()).delete(any());
    }
}
