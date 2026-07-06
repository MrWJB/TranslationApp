package com.translationapp.translator;

import com.translationapp.config.TranslationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepLTranslatorTest {

    private DeepLTranslator translator;

    @BeforeEach
    void setUp() {
        TranslationProperties properties = new TranslationProperties();
        properties.getDeepl().setApiKey("unit-test-key");
        properties.getDeepl().setApiUrl("https://api-free.deepl.com/v2/translate");
        translator = new DeepLTranslator(
                new ObjectMapper(),
                WebClient.builder(),
                properties);
    }

    @Test
    void resolveApiUrl_freeKeyAlwaysUsesFreeEndpoint() {
        assertEquals(
                "https://api-free.deepl.com/v2/translate",
                DeepLTranslator.resolveApiUrl("abc:fx", "https://api.deepl.com/v2/translate"));
    }

    @Test
    void resolveApiUrl_proKeyUsesProEndpointWhenMisconfigured() {
        assertEquals(
                "https://api.deepl.com/v2/translate",
                DeepLTranslator.resolveApiUrl("abc-pro-key", "https://api-free.deepl.com/v2/translate"));
    }

    @Test
    void isFreeApiKey_detectsFxSuffix() {
        assertTrue(DeepLTranslator.isFreeApiKey("20f0ff60-1917-433f-a327-5a060451660b:fx"));
        assertFalse(DeepLTranslator.isFreeApiKey("pro-key-without-suffix"));
    }

    @Test
    void containsFailureMarker_detectsLegacyEmbeddedErrors() {
        assertTrue(TranslationMarkers.containsFailureMarker("[Translation failed: DeepL API returned 403"));
        assertFalse(TranslationMarkers.containsFailureMarker("<p>正常翻译内容</p>"));
    }

    @Test
    void sanitizeInternalMarkers_stripsLegacyAndNodeDelimiters() {
        String dirty = "<p>第一段\n__SENTENCE_BREAK__\n第二段<<<NODE_0>>>尾部</p>";
        String cleaned = TranslationMarkers.sanitizeInternalMarkers(dirty);
        assertFalse(cleaned.contains("__SENTENCE_BREAK__"));
        assertFalse(cleaned.contains("<<<NODE_0>>>"));
        assertTrue(cleaned.contains("第一段"));
        assertTrue(cleaned.contains("第二段"));
        assertTrue(cleaned.contains("尾部"));
    }

    @Test
    void translateHtmlStructure_preservesInlineCodeAsDomElement() {
        String html = "<p>The <code>org.springframework.beans</code> package provides core IoC.</p>";
        Function<String, String> stubTranslator = text -> text.replace("The", "该").replace("package provides core IoC.", "包提供了核心 IoC。");

        String result = translator.translateHtmlStructure(html, stubTranslator);

        org.jsoup.nodes.Element code = Jsoup.parseBodyFragment(result).selectFirst("code");
        assertNotNull(code, "inline code must remain a DOM element");
        assertEquals("org.springframework.beans", code.text());
        assertTrue(result.contains("该"));
        assertTrue(result.contains("包提供了核心 IoC"));
        assertFalse(result.contains("&lt;code&gt;"), "code tags must not be HTML-escaped inside prose");
    }

    @Test
    void translateHtmlStructure_preservesPreBlocks() {
        String html = "<pre><code>BeanFactory factory = new DefaultListableBeanFactory();</code></pre><p>Example usage.</p>";
        Function<String, String> stubTranslator = text -> text.replace("Example usage.", "示例用法。");

        String result = translator.translateHtmlStructure(html, stubTranslator);

        assertNotNull(Jsoup.parseBodyFragment(result).selectFirst("pre code"));
        assertTrue(result.contains("DefaultListableBeanFactory"));
        assertTrue(result.contains("示例用法"));
    }

    @Test
    void translateHtmlStructure_splitsAndRejoinsMultipleTextNodes() {
        String html = "<p>First sentence.</p><p>Second sentence.</p>";
        Function<String, String> stubTranslator = text -> {
            if (text.contains("<<<NODE_0>>>")) {
                return "第一句。\n<<<NODE_0>>>\n第二句。";
            }
            return text;
        };

        String result = translator.translateHtmlStructure(html, stubTranslator);

        assertTrue(result.contains("第一句"));
        assertTrue(result.contains("第二句"));
        assertFalse(result.contains("<<<NODE_"));
        assertFalse(result.contains("First sentence"));
        assertFalse(result.contains("Second sentence"));
    }

    @Test
    void translateHtmlStructure_fallsBackToIndividualNodesWhenSplitMismatch() {
        String html = "<p>Alpha.</p><p>Beta.</p>";
        Function<String, String> stubTranslator = text -> {
            if (text.contains("<<<NODE_0>>>")) {
                return "Merged translation without delimiter";
            }
            if ("Alpha.".equals(text)) {
                return "阿尔法。";
            }
            if ("Beta.".equals(text)) {
                return "贝塔。";
            }
            return text;
        };

        String result = translator.translateHtmlStructure(html, stubTranslator);

        assertTrue(result.contains("阿尔法"));
        assertTrue(result.contains("贝塔"));
    }
}
