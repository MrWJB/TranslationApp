package com.translationapp.translator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.translationapp.config.TranslationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
public class TencentTranslator extends AbstractHtmlDocumentTranslator {

    private final TranslationProperties.Tencent config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public TencentTranslator(ObjectMapper objectMapper, WebClient.Builder builder, TranslationProperties properties) {
        this.objectMapper = objectMapper;
        this.config = properties.getTencent();
        String endpoint = config.getEndpoint() == null || config.getEndpoint().isBlank()
                ? "https://tmt.tencentcloudapi.com"
                : config.getEndpoint().trim();
        this.webClient = builder.baseUrl(endpoint).build();
        if (isConfigured()) {
            log.info("Tencent TMT configured (region={})", config.getRegion());
        }
    }

    @Override
    public String getProviderId() {
        return TranslationProvider.TENCENT.id();
    }

    @Override
    protected String providerDisplayName() {
        return "Tencent TMT";
    }

    @Override
    public boolean isConfigured() {
        return config.getSecretId() != null && !config.getSecretId().isBlank()
                && config.getSecretKey() != null && !config.getSecretKey().isBlank();
    }

    @Override
    protected String translateChunk(String text, boolean htmlMode) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("SourceText", text);
            body.put("Source", "en");
            body.put("Target", "zh");
            body.put("ProjectId", parseProjectId(config.getProjectId()));
            String payload = objectMapper.writeValueAsString(body);

            String host = extractHost(config.getEndpoint());
            TencentCloudSigner.SignedRequest signed = TencentCloudSigner.signTextTranslate(
                    config.getSecretId(),
                    config.getSecretKey(),
                    config.getRegion(),
                    host,
                    payload);

            String response = webClient.post()
                    .header(HttpHeaders.AUTHORIZATION, signed.authorization())
                    .header("X-TC-Action", signed.action())
                    .header("X-TC-Version", signed.version())
                    .header("X-TC-Timestamp", String.valueOf(signed.timestamp()))
                    .header("X-TC-Region", signed.region())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.createException())
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                throw new TranslationFailedException(getProviderId(), "Empty Tencent TMT response", null);
            }
            JsonNode root = objectMapper.readTree(response);
            JsonNode responseNode = root.get("Response");
            if (responseNode != null && responseNode.has("Error")) {
                JsonNode error = responseNode.get("Error");
                String code = error.path("Code").asText("");
                String message = error.path("Message").asText("Tencent TMT error");
                boolean retryable = code.contains("RequestLimit") || code.contains("Quota");
                throw new TranslationFailedException(getProviderId(), "Tencent TMT: " + message, null, retryable);
            }
            if (responseNode != null && responseNode.has("TargetText")) {
                return responseNode.get("TargetText").asText();
            }
            throw new TranslationFailedException(getProviderId(), "Invalid Tencent TMT response format", null);
        } catch (TranslationFailedException e) {
            throw e;
        } catch (WebClientResponseException e) {
            throw new TranslationFailedException(
                    getProviderId(),
                    "Tencent TMT returned " + e.getStatusCode().value(),
                    e,
                    e.getStatusCode().value() == 429);
        } catch (Exception e) {
            throw new TranslationFailedException(getProviderId(), "Tencent TMT request failed", e);
        }
    }

    private static int parseProjectId(String projectId) {
        try {
            return Integer.parseInt(projectId == null || projectId.isBlank() ? "0" : projectId.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String extractHost(String endpoint) {
        String value = endpoint == null || endpoint.isBlank() ? "https://tmt.tencentcloudapi.com" : endpoint.trim();
        return value.replace("https://", "").replace("http://", "").replaceAll("/.*", "");
    }
}
