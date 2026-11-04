package com.example.llmplayground.config;

import com.example.llmplayground.llm.LlmGateway;
import com.example.llmplayground.llm.LlmProvider;
import com.example.llmplayground.llm.MeteredLlmGateway;
import com.example.llmplayground.llm.RecordingLlmGateway;
import com.example.llmplayground.llm.provider.OllamaProvider;
import com.example.llmplayground.llm.provider.OpenRouterCostClient;
import com.example.llmplayground.llm.provider.OpenRouterProvider;
import com.example.llmplayground.persistence.LlmCallRepository;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Wires the providers and the gateway chain: Recording(Metered(providers)).
 * Spring AI creates both chat models; which one is used is decided per call by the model prefix.
 */
@Configuration
public class LlmConfig {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LlmConfig.class);

    static final String MISSING_KEY = "missing";

    @Bean
    OpenRouterProvider openRouterProvider(OpenAiChatModel openAiChatModel,
                                          PlaygroundProperties properties,
                                          RestClient.Builder restClientBuilder,
                                          @Value("${spring.ai.openai.base-url}") String baseUrl,
                                          @Value("${spring.ai.openai.api-key:" + MISSING_KEY + "}") String apiKey) {
        boolean keyPresent = apiKey != null && !apiKey.isBlank() && !MISSING_KEY.equals(apiKey);
        OpenRouterCostClient costClient = keyPresent && properties.openrouter().fetchCost()
                ? new OpenRouterCostClient(restClientBuilder, baseUrl, apiKey)
                : null;
        return new OpenRouterProvider(openAiChatModel, keyPresent, costClient, properties.http().readTimeout());
    }

    @Bean
    OllamaProvider ollamaProvider(OllamaChatModel ollamaChatModel, PlaygroundProperties properties) {
        return new OllamaProvider(ollamaChatModel, properties.http().readTimeout());
    }

    /** Only providers enabled by CHAT_PROVIDERS are routable; a call to another one ends as ERROR. */
    @Bean
    LlmGateway llmGateway(List<LlmProvider> providers, LlmCallRepository repository,
                          PlaygroundProperties properties) {
        List<LlmProvider> enabled = providers.stream().filter(p -> properties.isEnabled(p.type())).toList();
        log.info("Enabled LLM providers: {}", enabled.stream().map(p -> p.type().prefix()).toList());
        return new RecordingLlmGateway(new MeteredLlmGateway(enabled), repository);
    }
}
