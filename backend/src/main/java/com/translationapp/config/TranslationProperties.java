package com.translationapp.config;

import com.translationapp.translator.TranslationProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "translation")
public class TranslationProperties {

    private boolean onCrawl = true;
    private long delayMs = 50;

    /** Primary engine: deepl | azure | tencent | ollama */
    private String provider = "deepl";

    /** Fallback engines when primary fails with quota/rate-limit (comma-separated in yaml). */
    private List<String> fallbackProviders = new ArrayList<>();

    private Deepl deepl = new Deepl();
    private Azure azure = new Azure();
    private Tencent tencent = new Tencent();
    private Ollama ollama = new Ollama();

    public TranslationProvider primaryProvider() {
        return TranslationProvider.fromConfig(provider);
    }

    public List<TranslationProvider> fallbackProviderList() {
        List<TranslationProvider> result = new ArrayList<>();
        if (fallbackProviders == null) {
            return result;
        }
        for (String item : fallbackProviders) {
            if (item == null || item.isBlank()) {
                continue;
            }
            result.add(TranslationProvider.fromConfig(item));
        }
        return result;
    }

    @Data
    public static class Deepl {
        private String apiKey = "";
        private String apiUrl = "https://api-free.deepl.com/v2/translate";
    }

    @Data
    public static class Azure {
        private String subscriptionKey = "";
        private String region = "global";
        private String endpoint = "https://api.cognitive.microsofttranslator.com";
    }

    @Data
    public static class Tencent {
        private String secretId = "";
        private String secretKey = "";
        private String region = "ap-guangzhou";
        private String endpoint = "https://tmt.tencentcloudapi.com";
        private String projectId = "0";
    }

    @Data
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen2.5:7b";
    }
}
