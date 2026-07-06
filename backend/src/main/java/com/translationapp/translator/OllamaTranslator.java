package com.translationapp.translator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.translationapp.config.TranslationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
public class OllamaTranslator extends AbstractHtmlDocumentTranslator {

    private final TranslationProperties.Ollama config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OllamaTranslator(ObjectMapper objectMapper, WebClient.Builder builder, TranslationProperties properties) {
        this.objectMapper = objectMapper;
        this.config = properties.getOllama();
        String baseUrl = config.getBaseUrl() == null || config.getBaseUrl().isBlank()
                ? "http://localhost:11434"
                : config.getBaseUrl().trim();
        this.webClient = builder.baseUrl(baseUrl).build();
        if (isConfigured()) {
            log.info("Ollama translator configured (model={}, url={})", config.getModel(), baseUrl);
        }
    }

    @Override
    public String getProviderId() {
        return TranslationProvider.OLLAMA.id();
    }

    @Override
    protected String providerDisplayName() {
        return "Ollama";
    }

    @Override
    public boolean isConfigured() {
        return config.getModel() != null && !config.getModel().isBlank();
    }

    @Override
    protected String translateChunk(String text, boolean htmlMode) {
        String instruction = htmlMode
                ? "Translate the following English HTML fragment to Simplified Chinese. "
                        + "Preserve all HTML tags and attributes exactly; only translate visible text. "
                        + "Do not add explanations.\n\n"
                : "Translate the following English text to Simplified Chinese. Output only the translation.\n\n";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModel());
        body.put("prompt", instruction + text);
        body.put("stream", false);

        try {
            String response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                throw new TranslationFailedException(getProviderId(), "Empty Ollama response", null);
            }
            JsonNode root = objectMapper.readTree(response);
            if (root.has("response")) {
                return root.get("response").asText().trim();
            }
            throw new TranslationFailedException(getProviderId(), "Invalid Ollama response format", null);
        } catch (TranslationFailedException e) {
            throw e;
        } catch (WebClientResponseException e) {
            throw new TranslationFailedException(
                    getProviderId(),
                    "Ollama returned " + e.getStatusCode().value() + " (is Ollama running?)",
                    e,
                    true);
        } catch (Exception e) {
            throw new TranslationFailedException(getProviderId(), "Ollama request failed (is Ollama running?)", e, true);
        }
    }
}
