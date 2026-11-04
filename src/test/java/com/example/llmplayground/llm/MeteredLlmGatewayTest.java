package com.example.llmplayground.llm;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MeteredLlmGatewayTest {

    private static final LlmParams PARAMS = new LlmParams(0.2, 256, false);
    private static final CallContext CTX = new CallContext(1L, 5L, 1, false);

    private static LlmRequest request(String model) {
        return new LlmRequest(ModelRef.parse(model), "greeting", "Say hi", PARAMS);
    }

    @Test
    void okCallCarriesMetrics() {
        StubProvider ollama = new StubProvider(ProviderType.OLLAMA, () -> new ProviderResponse(
                "Hi!", 12, 40, 52, "STOP", "id-1", 150L, BigDecimal.ZERO,
                new OllamaMetrics(40, 500_000_000L, 12, 20_000_000L, 3_000_000L)));
        MeteredLlmGateway gateway = new MeteredLlmGateway(List.of(ollama));

        LlmResult result = gateway.call(request("ollama:llama3.2:3b"), CTX);

        assertThat(ollama.calledModels).containsExactly("llama3.2:3b");
        assertThat(result.status()).isEqualTo(CallStatus.OK);
        assertThat(result.text()).isEqualTo("Hi!");
        assertThat(result.promptTokens()).isEqualTo(12);
        assertThat(result.completionTokens()).isEqualTo(40);
        assertThat(result.totalTokens()).isEqualTo(52);
        assertThat(result.finishReason()).isEqualTo("stop");
        assertThat(result.ttftMs()).isEqualTo(150L);
        assertThat(result.tokensPerSecond()).isEqualTo(80.0);   // 40 tokens / 0.5 s
        assertThat(result.costUsd()).isEqualByComparingTo("0");
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.startedAt()).isNotNull();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void emptyAnswerWithLengthIsEmptyNotOk() {
        StubProvider openrouter = new StubProvider(ProviderType.OPENROUTER,
                () -> StubProvider.answer("", 1024, "LENGTH"));
        LlmResult result = new MeteredLlmGateway(List.of(openrouter)).call(request("openrouter:deepseek/deepseek-r1"), CTX);

        assertThat(result.status()).isEqualTo(CallStatus.EMPTY);
        assertThat(result.finishReason()).isEqualTo("length");
        assertThat(result.errorMessage()).contains("finish_reason=length");
        assertThat(result.completionTokens()).isEqualTo(1024);
    }

    @Test
    void blankAnswerIsEmpty() {
        StubProvider ollama = new StubProvider(ProviderType.OLLAMA, () -> StubProvider.answer("  \n", 3, "stop"));
        assertThat(new MeteredLlmGateway(List.of(ollama)).call(request("ollama:qwen3"), CTX).status())
                .isEqualTo(CallStatus.EMPTY);
    }

    @Test
    void exceptionBecomesErrorAndIsNeverThrown() {
        StubProvider openrouter = new StubProvider(ProviderType.OPENROUTER, () -> {
            throw new RestClientException("Error while extracting response for type [ChatCompletion]",
                    new ResourceAccessException("I/O error", new HttpTimeoutException("request timed out")));
        });
        LlmResult result = new MeteredLlmGateway(List.of(openrouter)).call(request("openrouter:openai/gpt-5-mini"), CTX);

        assertThat(result.status()).isEqualTo(CallStatus.ERROR);
        assertThat(result.errorMessage()).startsWith("TIMEOUT");
        assertThat(result.text()).isNull();
        assertThat(result.model()).isEqualTo(ModelRef.parse("openrouter:openai/gpt-5-mini"));
    }

    @Test
    void missingProviderIsError() {
        LlmResult result = new MeteredLlmGateway(List.of()).call(request("ollama:llama3.2"), CTX);
        assertThat(result.status()).isEqualTo(CallStatus.ERROR);
        assertThat(result.errorMessage()).contains("not configured");
    }

    @Test
    void costIsResolvedAfterTheCall() {
        LlmProvider withCost = new StubProvider(ProviderType.OPENROUTER, () -> StubProvider.answer("ok", 5, "stop")) {
            @Override
            public BigDecimal resolveCost(ProviderResponse response) {
                return new BigDecimal("0.000123");
            }
        };
        LlmResult result = new MeteredLlmGateway(List.of(withCost)).call(request("openrouter:x/y"), CTX);
        assertThat(result.costUsd()).isEqualByComparingTo("0.000123");
    }

    @Test
    void failingCostLookupDoesNotFailTheCall() {
        LlmProvider brokenCost = new StubProvider(ProviderType.OPENROUTER, () -> StubProvider.answer("ok", 5, "stop")) {
            @Override
            public BigDecimal resolveCost(ProviderResponse response) {
                throw new IllegalStateException("boom");
            }
        };
        LlmResult result = new MeteredLlmGateway(List.of(brokenCost)).call(request("openrouter:x/y"), CTX);
        assertThat(result.status()).isEqualTo(CallStatus.OK);
        assertThat(result.costUsd()).isNull();
    }
}
