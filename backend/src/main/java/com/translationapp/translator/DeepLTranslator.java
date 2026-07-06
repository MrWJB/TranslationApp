package com.translationapp.translator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.translationapp.config.TranslationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class DeepLTranslator extends AbstractHtmlDocumentTranslator {

    private static final String FREE_API_URL = "https://api-free.deepl.com/v2/translate";
    private static final String PRO_API_URL = "https://api.deepl.com/v2/translate";

    private final String apiKey;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final boolean freeApiKey;
    private final String resolvedApiUrl;

    public DeepLTranslator(
            ObjectMapper objectMapper,
            WebClient.Builder webClientBuilder,
            TranslationProperties properties) {
        this.objectMapper = objectMapper;
        TranslationProperties.Deepl deepl = properties.getDeepl();
        this.apiKey = deepl.getApiKey();
        this.freeApiKey = isFreeApiKey(apiKey);
        this.resolvedApiUrl = resolveApiUrl(apiKey, deepl.getApiUrl());
        log.info("DeepL translator using {} endpoint ({})", freeApiKey ? "Free" : "Pro", resolvedApiUrl);
        this.webClient = webClientBuilder.baseUrl(resolvedApiUrl).build();
    }

    @Override
    public String getProviderId() {
        return TranslationProvider.DEEPL.id();
    }

    @Override
    protected String providerDisplayName() {
        return "DeepL";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"your-api-key-here".equals(apiKey);
    }

    @Override
    protected String translateChunk(String text, boolean htmlMode) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("text", text);
        formData.add("source_lang", "EN");
        formData.add("target_lang", "ZH");
        if (htmlMode) {
            formData.add("tag_handling", "html");
            formData.add("ignore_tags", "code,pre");
        }
        formData.add("split_sentences", "0");

        String response;
        try {
            response = webClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::toDeepLApiException)
                    .bodyToMono(String.class)
                    .block();
        } catch (DeepLApiException e) {
            throw toTranslationFailed(e);
        } catch (WebClientResponseException e) {
            throw toTranslationFailed(
                    DeepLApiException.fromStatus(e.getStatusCode().value(), e.getResponseBodyAsString(), freeApiKey, resolvedApiUrl));
        }

        if (response == null) {
            throw new TranslationFailedException(getProviderId(), "Empty response from DeepL API", null);
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode translations = jsonNode.get("translations");
            if (translations != null && translations.isArray() && !translations.isEmpty()) {
                return translations.get(0).get("text").asText();
            }
            throw new TranslationFailedException(getProviderId(), "Invalid DeepL response format", null);
        } catch (TranslationFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new TranslationFailedException(getProviderId(), "Failed to parse DeepL response", e);
        }
    }

    static String resolveApiUrl(String apiKey, String configuredApiUrl) {
        boolean freeKey = isFreeApiKey(apiKey);
        String normalizedUrl = configuredApiUrl == null || configuredApiUrl.isBlank() ? null : configuredApiUrl.trim();

        if (freeKey) {
            if (normalizedUrl != null && !isFreeApiUrl(normalizedUrl)) {
                log.warn("DeepL Free key (:fx) but URL is {}; forcing {}", configuredApiUrl, FREE_API_URL);
            }
            return FREE_API_URL;
        }
        if (isFreeApiUrl(normalizedUrl)) {
            log.warn("DeepL Pro key but Free URL {}; switching to {}", configuredApiUrl, PRO_API_URL);
            return PRO_API_URL;
        }
        return normalizedUrl != null ? normalizedUrl : PRO_API_URL;
    }

    static boolean isFreeApiKey(String apiKey) {
        return apiKey != null && apiKey.endsWith(":fx");
    }

    private static boolean isFreeApiUrl(String apiUrl) {
        return apiUrl != null && apiUrl.contains("api-free.deepl.com");
    }

    private Mono<? extends Throwable> toDeepLApiException(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> DeepLApiException.fromStatus(response.statusCode().value(), body, freeApiKey, resolvedApiUrl));
    }

    private TranslationFailedException toTranslationFailed(DeepLApiException e) {
        return new TranslationFailedException(getProviderId(), e.getMessage(), e, e.isRetryable());
    }

    private static final class DeepLApiException extends RuntimeException {
        private final boolean retryable;

        private DeepLApiException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        boolean isRetryable() {
            return retryable;
        }

        static DeepLApiException fromStatus(int statusCode, String responseBody, boolean freeKey, String apiUrl) {
            String deeplMessage = extractDeepLMessage(responseBody);
            String hint = buildHint(statusCode, deeplMessage, freeKey, apiUrl);
            boolean retryable = statusCode == 429 || statusCode == 456;
            String msg = deeplMessage != null && !deeplMessage.isBlank()
                    ? "DeepL API returned " + statusCode + ": " + deeplMessage + hint
                    : "DeepL API returned " + statusCode + hint;
            return new DeepLApiException(msg, retryable);
        }

        private static String extractDeepLMessage(String responseBody) {
            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }
            try {
                JsonNode root = new ObjectMapper().readTree(responseBody);
                JsonNode message = root.get("message");
                if (message != null && message.isTextual()) {
                    return message.asText();
                }
            } catch (Exception ignored) {
            }
            return null;
        }

        private static String buildHint(int statusCode, String deeplMessage, boolean freeKey, String apiUrl) {
            String lower = deeplMessage != null ? deeplMessage.toLowerCase() : "";
            if (statusCode == 403 && lower.contains("wrong endpoint")) {
                return freeKey
                        ? " Hint: Free keys (:fx) must use api-free.deepl.com."
                        : " Hint: Pro keys must use api.deepl.com.";
            }
            if (statusCode == 403 && lower.contains("legacy authentication")) {
                return " Hint: Use Authorization: DeepL-Auth-Key header.";
            }
            if (statusCode == 456) {
                return " Hint: DeepL character quota exceeded.";
            }
            if (statusCode == 429) {
                return " Hint: DeepL rate limit exceeded.";
            }
            if (statusCode == 401) {
                return " Hint: Invalid DEEPL_API_KEY.";
            }
            if (apiUrl != null && apiUrl.contains("api-free") && !freeKey) {
                return " Hint: Pro key configured but Free endpoint in use.";
            }
            return "";
        }
    }
}
