package com.example.llmplayground.web;

import com.example.llmplayground.config.PlaygroundProperties;
import com.example.llmplayground.llm.provider.OpenRouterProvider;
import com.example.llmplayground.persistence.LlmCallRepository;
import com.example.llmplayground.persistence.PromptRepository;
import com.example.llmplayground.persistence.RunRepository;
import com.example.llmplayground.prompt.PromptService;
import com.example.llmplayground.report.ReportRepository;
import com.example.llmplayground.runner.ModelCatalog;
import com.example.llmplayground.runner.RunForm;
import com.example.llmplayground.runner.RunService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CHAT_PROVIDERS=ollama: the form shows no OpenRouter model and does not even fetch the OpenRouter catalog. */
@WebMvcTest(RunController.class)
@Import({ViewFormat.class, OllamaOnlyFormTest.Config.class})
class OllamaOnlyFormTest {

    @TestConfiguration
    static class Config {
        @Bean
        PlaygroundProperties playgroundProperties() {
            return new PlaygroundProperties(List.of("openrouter:anthropic/claude-haiku-5.5", "ollama:llama3.2:3b"),
                    0.2, 1024, 1, true, true, "1.0",
                    List.of(new PlaygroundProperties.ModelOverride("openrouter:deepseek/deepseek-r1-0528", 8192, null)),
                    null, null, List.of("ollama"));
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

    @Test
    void formShowsOnlyOllamaModels() throws Exception {
        RunForm form = new RunForm();
        // even if something puts a cloud model into the form, it is not offered
        form.setModels(new ArrayList<>(List.of("ollama:llama3.2:3b", "openrouter:openai/gpt-5-mini")));
        when(runService.defaultForm()).thenReturn(form);
        when(runService.enabledPrefixes()).thenReturn("ollama");
        when(modelCatalog.ollamaModels()).thenReturn(List.of("ollama:qwen3:8b"));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"ollama:llama3.2:3b\"")))
                .andExpect(content().string(containsString("value=\"ollama:qwen3:8b\"")))
                .andExpect(content().string(not(containsString("value=\"openrouter:"))))
                .andExpect(content().string(not(containsString("deepseek-r1"))))
                .andExpect(content().string(not(containsString("Ключ OpenRouter не задан"))))
                .andExpect(content().string(containsString("<strong>ollama</strong>")));
        verify(modelCatalog, never()).openRouterModels();
    }
}
