package com.translationapp.translator;

import com.translationapp.config.TranslationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Primary {@link DocumentTranslator} that routes to configured provider with optional fallback chain.
 */
@Slf4j
@Primary
@Component
public class FallbackDocumentTranslator implements DocumentTranslator {

    private final TranslationProperties properties;
    private final Map<TranslationProvider, DocumentTranslator> translatorsByProvider;
    private final List<DocumentTranslator> orderedChain;

    public FallbackDocumentTranslator(
            TranslationProperties properties,
            DeepLTranslator deepLTranslator,
            AzureTranslator azureTranslator,
            TencentTranslator tencentTranslator,
            OllamaTranslator ollamaTranslator) {
        this.properties = properties;
        this.translatorsByProvider = new LinkedHashMap<>();
        register(deepLTranslator);
        register(azureTranslator);
        register(tencentTranslator);
        register(ollamaTranslator);
        this.orderedChain = buildChain();
        log.info(
                "Translation chain: primary={}, fallbacks={}, configured=[{}]",
                properties.primaryProvider().id(),
                properties.fallbackProviderList().stream().map(TranslationProvider::id).toList(),
                orderedChain.stream().map(DocumentTranslator::getProviderId).toList());
    }

    private void register(DocumentTranslator translator) {
        translatorsByProvider.put(TranslationProvider.fromConfig(translator.getProviderId()), translator);
    }

    private List<DocumentTranslator> buildChain() {
        List<DocumentTranslator> chain = new ArrayList<>();
        addProvider(chain, properties.primaryProvider());
        for (TranslationProvider fallback : properties.fallbackProviderList()) {
            if (fallback == properties.primaryProvider()) {
                continue;
            }
            addProvider(chain, fallback);
        }
        return chain;
    }

    private void addProvider(List<DocumentTranslator> chain, TranslationProvider provider) {
        DocumentTranslator translator = translatorsByProvider.get(provider);
        if (translator == null) {
            log.warn("Unknown translation provider: {}", provider);
            return;
        }
        if (chain.stream().noneMatch(t -> t.getProviderId().equals(translator.getProviderId()))) {
            chain.add(translator);
        }
    }

    @Override
    public String getProviderId() {
        return properties.primaryProvider().id();
    }

    @Override
    public boolean isConfigured() {
        return orderedChain.stream().anyMatch(DocumentTranslator::isConfigured);
    }

    @Override
    public String translateHtmlToChinese(String htmlContent) {
        return translateWithFallback(htmlContent, true);
    }

    @Override
    public String translateToChinese(String text) {
        return translateWithFallback(text, false);
    }

    private String translateWithFallback(String content, boolean html) {
        if (orderedChain.isEmpty()) {
            throw new TranslationFailedException("none", "No translation providers registered", null);
        }
        TranslationFailedException lastFailure = null;
        for (DocumentTranslator translator : orderedChain) {
            if (!translator.isConfigured()) {
                log.debug("Skipping unconfigured provider {}", translator.getProviderId());
                continue;
            }
            try {
                return html ? translator.translateHtmlToChinese(content) : translator.translateToChinese(content);
            } catch (TranslationFailedException e) {
                lastFailure = e;
                if (e.isRetryable()) {
                    log.warn(
                            "Provider {} failed (retryable): {}; trying next in chain",
                            translator.getProviderId(),
                            e.getMessage());
                    continue;
                }
                throw e;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new TranslationFailedException(
                getProviderId(),
                "No translation provider configured; set credentials for translation." + properties.primaryProvider().id(),
                null);
    }
}
