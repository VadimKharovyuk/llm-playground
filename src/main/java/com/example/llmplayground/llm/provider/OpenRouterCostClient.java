package com.example.llmplayground.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Looks up the exact cost of an OpenRouter call via {@code GET /api/v1/generation?id=...}.
 * Spring AI's OpenAI usage model drops OpenRouter's {@code usage.cost}, so the cost is fetched separately,
 * after the call and outside its measured time. The generation record may appear with a short delay.
 */
public class OpenRouterCostClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterCostClient.class);
    private static final int ATTEMPTS = 4;
    private static final long DELAY_MS = 750;

    private final RestClient restClient;

    public OpenRouterCostClient(RestClient.Builder builder, String baseUrl, String apiKey) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /** @return cost in USD, or null when OpenRouter did not report it */
    public BigDecimal fetchCost(String generationId) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                JsonNode body = restClient.get()
                        .uri(uri -> uri.path("/v1/generation").queryParam("id", generationId).build())
                        .retrieve()
                        .body(JsonNode.class);
                JsonNode cost = body == null ? null : body.path("data").path("total_cost");
                if (cost != null && cost.isNumber()) {
                    return cost.decimalValue();
                }
                return null;
            } catch (HttpClientErrorException e) {
                if (!e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(404)) || attempt == ATTEMPTS) {
                    log.debug("Cost lookup for {} failed: {}", generationId, e.getStatusCode());
                    return null;
                }
            } catch (RuntimeException e) {
                log.debug("Cost lookup for {} failed: {}", generationId, e.toString());
                return null;
            }
            sleep();
        }
        return null;
    }

    private static void sleep() {
        try {
            Thread.sleep(DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
