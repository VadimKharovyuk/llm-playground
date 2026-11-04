package com.example.llmplayground.web;

import com.example.llmplayground.config.PlaygroundProperties;
import com.example.llmplayground.llm.CallStatus;
import com.example.llmplayground.llm.ProviderType;
import com.example.llmplayground.llm.provider.OpenRouterProvider;
import com.example.llmplayground.persistence.LlmCall;
import com.example.llmplayground.persistence.LlmCallRepository;
import com.example.llmplayground.persistence.Prompt;
import com.example.llmplayground.persistence.PromptRepository;
import com.example.llmplayground.persistence.Run;
import com.example.llmplayground.persistence.RunRepository;
import com.example.llmplayground.persistence.RunStatus;
import com.example.llmplayground.prompt.PromptService;
import com.example.llmplayground.report.ModelStats;
import com.example.llmplayground.report.ReportRepository;
import com.example.llmplayground.runner.ModelCatalog;
import com.example.llmplayground.runner.RunForm;
import com.example.llmplayground.runner.RunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Renders the real Thymeleaf pages with stubbed services: no database, no LLM. */
@WebMvcTest({RunController.class, PromptController.class, ModelController.class})
@Import({ViewFormat.class, WebPagesTest.Config.class})
class WebPagesTest {

    @TestConfiguration
    static class Config {
        @Bean
        PlaygroundProperties playgroundProperties() {
            return new PlaygroundProperties(List.of("openrouter:anthropic/claude-haiku-5.5", "ollama:llama3.2:3b"),
                    0.2, 1024, 1, true, true, "1.0",
                    List.of(new PlaygroundProperties.ModelOverride("ollama:qwen3:8b", 8192, null)), null, null, List.of("ollama", "openrouter"));
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean RunService runService;
    @MockitoBean RunRepository runRepository;
    @MockitoBean LlmCallRepository callRepository;
    @MockitoBean PromptRepository promptRepository;
    @MockitoBean PromptService promptService;
    @MockitoBean ReportRepository reportRepository;
    @MockitoBean ModelCatalog modelCatalog;
    @MockitoBean OpenRouterProvider openRouterProvider;

    private final Run run = new Run();
    private final Prompt prompt = new Prompt("book-api", "Write a Spring controller and repository", Instant.now());

    @BeforeEach
    void setUp() {
        run.setId(7L);
        run.setStartedAt(Instant.parse("2026-11-04T10:00:00Z"));
        run.setModels("ollama:llama3.2:3b,openrouter:anthropic/claude-haiku-5.5");
        run.setPrompts("book-api");
        run.setRunsPerPrompt(1);
        run.setStatus(RunStatus.DONE);
        prompt.setId(3L);

        RunForm form = new RunForm();
        form.setModels(List.of("ollama:llama3.2:3b"));
        when(runService.defaultForm()).thenReturn(form);
        when(runService.progress(any())).thenReturn(new RunService.Progress(2, 2));
        when(modelCatalog.ollamaModels()).thenReturn(List.of("ollama:qwen3:8b"));
        when(modelCatalog.openRouterModels()).thenReturn(List.of("openrouter:openai/gpt-5-mini"));
        when(promptService.all()).thenReturn(List.of(prompt));
        when(promptService.get(3L)).thenReturn(prompt);
        when(runRepository.findById(7L)).thenReturn(Optional.of(run));
        when(runRepository.findTop200ByOrderByIdDesc()).thenReturn(List.of(run));
        when(reportRepository.runTotals()).thenReturn(Map.of(7L,
                new ReportRepository.RunTotals(2, 1, 0, new BigDecimal("0.0021"))));
        when(promptRepository.findAllById(any())).thenReturn(List.of(prompt));
        List<ModelStats> stats = List.of(new ModelStats("OLLAMA", "llama3.2:3b", 1, 1, 0, 0, 842.0, 842L, 842L,
                842.0, 222.0, 90.0, 35.0, 56.04, BigDecimal.ZERO, 6293L, 4.0, 1, 1, Instant.now()));
        when(reportRepository.modelStats(7L)).thenReturn(stats);
        when(reportRepository.promptStats(3L)).thenReturn(stats);
        when(reportRepository.leaderboard()).thenReturn(stats);
        List<LlmCall> calls = List.of(call(1L, ProviderType.OLLAMA, "llama3.2:3b", CallStatus.OK,
                        "```java\n@RestController\nclass BookController {}\n```", null),
                call(2L, ProviderType.OPENROUTER, "anthropic/claude-haiku-5.5", CallStatus.ERROR, null,
                        "TIMEOUT: request timed out"));
        when(callRepository.findByRunIdOrderByIdAsc(7L)).thenReturn(calls);
        when(callRepository.findTop100ByPromptIdAndRepeatNoGreaterThanOrderByIdDesc(3L, 0)).thenReturn(calls);
    }

    private static LlmCall call(long id, ProviderType provider, String model, CallStatus status, String text,
                                String error) {
        LlmCall c = new LlmCall();
        c.setId(id);
        c.setRunId(7L);
        c.setPromptId(3L);
        c.setProvider(provider);
        c.setModel(model);
        c.setPromptName("book-api");
        c.setRepeatNo(1);
        c.setStartedAt(Instant.now());
        c.setDurationMs(842);
        c.setTtftMs(222L);
        c.setPromptTokens(90);
        c.setCompletionTokens(35);
        c.setTokensPerSecond(56.04);
        c.setStatus(status);
        c.setResponseText(text);
        c.setErrorMessage(error);
        c.setFinishReason(status == CallStatus.OK ? "stop" : null);
        return c;
    }

    @Test
    void newComparisonFormListsConfiguredAndInstalledModels() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Новое сравнение")))
                .andExpect(content().string(containsString("value=\"openrouter:anthropic/claude-haiku-5.5\"")))
                .andExpect(content().string(containsString("value=\"ollama:qwen3:8b\"")))
                .andExpect(content().string(containsString("openrouter:openai/gpt-5-mini")))
                .andExpect(content().string(containsString("book-api")))
                .andExpect(content().string(containsString("Ключ OpenRouter не задан")));
    }

    @Test
    void invalidFormIsShownAgainWithError() throws Exception {
        when(runService.start(any())).thenThrow(new IllegalArgumentException("Выберите хотя бы одну модель"));
        mvc.perform(post("/runs").param("newPromptText", "task"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Выберите хотя бы одну модель")))
                .andExpect(content().string(containsString(">task</textarea>")));
    }

    @Test
    void validFormRedirectsToTheRun() throws Exception {
        when(runService.start(any())).thenReturn(7L);
        mvc.perform(post("/runs").param("models", "ollama:llama3.2:3b").param("newPromptText", "task"))
                .andExpect(redirectedUrl("/runs/7"));
    }

    @Test
    void runPageShowsAnswersSideBySideWithMetrics() throws Exception {
        mvc.perform(get("/runs/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Прогон #7")))
                .andExpect(content().string(containsString("@RestController")))
                .andExpect(content().string(containsString("TIMEOUT: request timed out")))
                .andExpect(content().string(containsString("56.0")))
                .andExpect(content().string(containsString("4.0 ★")))
                .andExpect(content().string(containsString("/calls/1/rating")))
                .andExpect(content().string(not(containsString("56,0"))));
    }

    @Test
    void liveFragmentIsOnlyTheChangingPart() throws Exception {
        mvc.perform(get("/runs/7/live"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"live\"")))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void runListAndModelsAndPromptsRender() throws Exception {
        mvc.perform(get("/runs")).andExpect(status().isOk())
                .andExpect(content().string(containsString("$0.002100")));
        mvc.perform(get("/models")).andExpect(status().isOk())
                .andExpect(content().string(containsString("llama3.2:3b")));
        mvc.perform(get("/prompts")).andExpect(status().isOk())
                .andExpect(content().string(containsString("book-api")));
        mvc.perform(get("/prompts/3")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Write a Spring controller and repository")))
                .andExpect(content().string(containsString("@RestController")));
    }

    @Test
    void ratingIsStoredAndRedirectsBackToTheAnswer() throws Exception {
        LlmCall c = call(1L, ProviderType.OLLAMA, "llama3.2:3b", CallStatus.OK, "x", null);
        when(callRepository.findById(1L)).thenReturn(Optional.of(c));
        mvc.perform(post("/calls/1/rating").param("rating", "4").param("note", " clean code ")
                        .param("back", "/prompts/3"))
                .andExpect(redirectedUrl("/prompts/3#call-1"));
        verify(callRepository).save(c);
        org.assertj.core.api.Assertions.assertThat(c.getRating()).isEqualTo((short) 4);
        org.assertj.core.api.Assertions.assertThat(c.getRatingNote()).isEqualTo("clean code");
    }

    @Test
    void ratingIgnoresForeignRedirectTargets() throws Exception {
        LlmCall c = call(1L, ProviderType.OLLAMA, "llama3.2:3b", CallStatus.OK, "x", null);
        when(callRepository.findById(1L)).thenReturn(Optional.of(c));
        mvc.perform(post("/calls/1/rating").param("rating", "5").param("back", "//evil.example"))
                .andExpect(redirectedUrl("/runs/7#call-1"));
    }

    @Test
    void markdownExportIsADownload() throws Exception {
        mvc.perform(get("/runs/7/report.md"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("run-7.md")))
                .andExpect(content().string(containsString("| OLLAMA | llama3.2:3b |")));
    }

    @Test
    void runListHasDeleteControls() throws Exception {
        mvc.perform(get("/runs"))
                .andExpect(content().string(containsString("action=\"/runs/delete\"")))
                .andExpect(content().string(containsString("name=\"ids\"")))
                .andExpect(content().string(containsString("formaction=\"/runs/7/delete?back=list\"")));
    }

    @Test
    void deletingOneRunRedirectsToTheList() throws Exception {
        when(runService.delete(List.of(7L))).thenReturn(new RunService.DeleteResult(List.of(7L), List.of()));
        mvc.perform(post("/runs/7/delete").param("back", "list"))
                .andExpect(redirectedUrl("/runs"))
                .andExpect(flash().attribute("message", containsString("#7")));
    }

    @Test
    void runningRunIsNotDeleted() throws Exception {
        when(runService.delete(List.of(7L))).thenReturn(new RunService.DeleteResult(List.of(), List.of(7L)));
        mvc.perform(post("/runs/7/delete"))
                .andExpect(redirectedUrl("/runs/7"))
                .andExpect(flash().attribute("error", containsString("выполняется")));
    }

    @Test
    void bulkDeleteReportsDeletedAndSkipped() throws Exception {
        when(runService.delete(List.of(1L, 2L, 3L)))
                .thenReturn(new RunService.DeleteResult(List.of(1L, 3L), List.of(2L)));
        mvc.perform(post("/runs/delete").param("ids", "1", "2", "3"))
                .andExpect(redirectedUrl("/runs"))
                .andExpect(flash().attribute("message", "Удалено прогонов: 2 (#1, #3)"))
                .andExpect(flash().attribute("error", "Ещё выполняются, не удалены: #2"));
    }

    @Test
    void bulkDeleteWithoutSelectionAsksToSelect() throws Exception {
        mvc.perform(post("/runs/delete"))
                .andExpect(redirectedUrl("/runs"))
                .andExpect(flash().attribute("error", containsString("Отметьте")));
        verify(runService, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void unknownRunIs404() throws Exception {
        when(runRepository.findById(anyLong())).thenReturn(Optional.empty());
        mvc.perform(get("/runs/999")).andExpect(status().isNotFound());
    }
}
