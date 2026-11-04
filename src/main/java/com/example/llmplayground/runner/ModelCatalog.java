package com.example.llmplayground.runner;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Model names for the form: models installed in the local Ollama ({@code /api/tags})
 * and OpenRouter's public model list (for autocomplete). Cached, failures give an empty list.
 */
@Service
public class ModelCatalog {

    private static final Logger log = LoggerFactory.getLogger(ModelCatalog.class);
    private static final String OPENROUTER_MODELS = "https://openrouter.ai/api/v1/models";

    private final RestClient client;
    private final String ollamaBaseUrl;
    private final Cached<List<String>> ollama;
    private final Cached<List<String>> openRouter;

    public ModelCatalog(@Value("${spring.ai.ollama.base-url}") String ollamaBaseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.ollama = new Cached<>(Duration.ofSeconds(30), this::loadOllama);
        this.openRouter = new Cached<>(Duration.ofHours(1), this::loadOpenRouter);
    }

    /** Installed Ollama models as {@code ollama:name}. */
    public List<String> ollamaModels() {
        return ollama.get();
    }

    /** OpenRouter model ids as {@code openrouter:vendor/model}. */
    public List<String> openRouterModels() {
        return openRouter.get();
    }

    private List<String> loadOllama() {
        JsonNode body = client.get().uri(ollamaBaseUrl + "/api/tags").retrieve().body(JsonNode.class);
        List<String> names = new ArrayList<>();
        if (body != null) {
            body.path("models").forEach(m -> {
                String name = m.path("name").asText("");
                // embedding models cannot chat
                if (!name.isEmpty() && !name.contains("embed")) {
                    names.add("ollama:" + name);
                }
            });
        }
        names.sort(String::compareTo);
        return List.copyOf(names);
    }

    private List<String> loadOpenRouter() {
        JsonNode body = client.get().uri(OPENROUTER_MODELS).retrieve().body(JsonNode.class);
        List<String> ids = new ArrayList<>();
        if (body != null) {
            body.path("data").forEach(m -> {
                String id = m.path("id").asText("");
                if (!id.isEmpty()) {
                    ids.add("openrouter:" + id);
                }
            });
        }
        ids.sort(String::compareTo);
        return List.copyOf(ids);
    }

    /** Tiny time-based cache; an error is cached briefly as an empty list. */
    private static final class Cached<T> {
        private final Duration ttl;
        private final Supplier<List<String>> loader;
        private volatile List<String> value;
        private volatile Instant expires = Instant.EPOCH;

        Cached(Duration ttl, Supplier<List<String>> loader) {
            this.ttl = ttl;
            this.loader = loader;
        }

        synchronized List<String> get() {
            if (Instant.now().isBefore(expires) && value != null) {
                return value;
            }
            try {
                value = loader.get();
                expires = Instant.now().plus(ttl);
            } catch (RuntimeException e) {
                log.debug("Model list unavailable: {}", e.toString());
                value = List.of();
                expires = Instant.now().plusSeconds(15);
            }
            return value;
        }
    }
}
