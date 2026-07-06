package com.translationapp.translator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.translationapp.config.TranslationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AzureTranslator extends AbstractHtmlDocumentTranslator {

    private final TranslationProperties.Azure config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public AzureTranslator(ObjectMapper objectMapper, WebClient.Builder builder, TranslationProperties properties) {
        this.objectMapper = objectMapper;
        this.config = properties.getAzure();
        String baseUrl = config.getEndpoint() == null || config.getEndpoint().isBlank()
                ? "https://api.cognitive.microsofttranslator.com"
                : config.getEndpoint().trim();
        this.webClient = builder.baseUrl(baseUrl).build();
        if (isConfigured()) {
            log.info("Azure Translator configured (region={})", config.getRegion());
        }
    }

    @Override
    public String getProviderId() {
        return TranslationProvider.AZURE.id();
    }

    @Override
    protected String providerDisplayName() {
        return "Azure Translator";
    }

    @Override
    public boolean isConfigured() {
        return config.getSubscriptionKey() != null && !config.getSubscriptionKey().isBlank();
    }

    @Override
    protected String translateChunk(String text, boolean htmlMode) {
        ArrayNode body = objectMapper.createArrayNode();
        ObjectNode item = objectMapper.createObjectNode();
        item.put("Text", text);
        body.add(item);

        String textType = htmlMode ? "html" : "plain";
        try {
            String response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/translate")
                            .queryParam("api-version", "3.0")
                            .queryParam("from", "en")
                            .queryParam("to", "zh-Hans")
                            .queryParam("textType", textType)
                            .build())
                    .header("Ocp-Apim-Subscription-Key", config.getSubscriptionKey())
                    .header("Ocp-Apim-Subscription-Region", config.getRegion())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, errorResponse -> errorResponse.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(err -> Mono.error(new TranslationFailedException(
                                    getProviderId(),
                                    "Azure Translator returned " + errorResponse.statusCode().value() + ": " + err,
                                    null,
                                    errorResponse.statusCode().value() == 429))))
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                throw new TranslationFailedException(getProviderId(), "Empty Azure Translator response", null);
            }
            JsonNode root = objectMapper.readTree(response);
            if (root.isArray() && !root.isEmpty()) {
                JsonNode translations = root.get(0).get("translations");
                if (translations != null && translations.isArray() && !translations.isEmpty()) {
                    return translations.get(0).get("text").asText();
                }
            }
            throw new TranslationFailedException(getProviderId(), "Invalid Azure Translator response format", null);
        } catch (TranslationFailedException e) {
            throw e;
        } catch (WebClientResponseException e) {
            throw new TranslationFailedException(
                    getProviderId(),
                    "Azure Translator returned " + e.getStatusCode().value(),
                    e,
                    e.getStatusCode().value() == 429);
        } catch (Exception e) {
            throw new TranslationFailedException(getProviderId(), "Azure Translator request failed", e);
        }
    }
}
