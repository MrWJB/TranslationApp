package com.translationapp.translator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeVisitor;
import org.jsoup.select.NodeTraversor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DeepLTranslator {

    private final String apiKey;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // Tags whose content should not be translated
    private static final String[] UNTRANSLATABLE_TAGS = {
        "code", "pre", "script", "style", "img", "br", "hr", "svg", "path"
    };

    // Pattern to match placeholder tokens
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("__TRANSLATE_BLOCK_[a-zA-Z0-9-]+__");

    public DeepLTranslator(
            ObjectMapper objectMapper,
            WebClient.Builder webClientBuilder,
            @Value("${translation.deepl.api-key}") String apiKey,
            @Value("${translation.deepl.api-url}") String apiUrl) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.webClient = webClientBuilder
                .baseUrl(apiUrl)
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"your-api-key-here".equals(apiKey);
    }

    /**
     * Translate HTML content from English to Chinese, preserving HTML structure
     */
    public String translateHtmlToChinese(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            return "";
        }

        // Check if API key is configured
        if (!isConfigured()) {
            log.warn("Translation API key not configured, returning original text");
            return htmlContent;
        }

        try {
            // Parse HTML
            Document doc = Jsoup.parseBodyFragment(htmlContent);
            doc.outputSettings().syntax(Document.OutputSettings.Syntax.html);
            
            // Extract untranslatable blocks and replace with placeholders
            Map<String, String> placeholderMap = new HashMap<>();
            extractUntranslatableBlocks(doc.body(), placeholderMap);
            
            // Get all translatable text nodes
            List<org.jsoup.nodes.Node> textNodes = new ArrayList<>();
            collectTextNodes(doc.body(), textNodes);
            
            if (textNodes.isEmpty()) {
                return htmlContent;
            }
            
            // Group text into chunks for translation
            List<List<org.jsoup.nodes.Node>> chunks = groupIntoChunks(textNodes, 3000);
            
            // Translate each chunk
            for (List<org.jsoup.nodes.Node> chunk : chunks) {
                StringBuilder textToTranslate = new StringBuilder();
                for (int i = 0; i < chunk.size(); i++) {
                    org.jsoup.nodes.Node node = chunk.get(i);
                    String nodeText = getNodeText(node);
                    textToTranslate.append(nodeText);
                    if (i < chunk.size() - 1) {
                        textToTranslate.append("\n__SENTENCE_BREAK__\n");
                    }
                }
                
                String translatedText = translateChunk(textToTranslate.toString(), "EN", "ZH");
                
                // Split translated text and update nodes
                String[] translatedSentences = translatedText.split("\n__SENTENCE_BREAK__\n");
                for (int i = 0; i < chunk.size() && i < translatedSentences.length; i++) {
                    org.jsoup.nodes.Node node = chunk.get(i);
                    String translatedSentence = translatedSentences[i].trim();
                    
                    // Restore placeholders in translated text
                    translatedSentence = restorePlaceholders(translatedSentence, placeholderMap);
                    
                    // Update the node text
                    if (node instanceof TextNode) {
                        ((TextNode) node).text(translatedSentence);
                    } else if (node instanceof Element) {
                        // For elements, preserve child structure but replace text
                        replaceTextContent((Element) node, translatedSentence);
                    }
                }
            }
            
            // Restore untranslatable blocks
            restoreUntranslatableBlocks(doc.body(), placeholderMap);
            
            return doc.body().html();
        } catch (Exception e) {
            log.error("HTML translation failed: {}", e.getMessage(), e);
            return "[Translation failed: " + e.getMessage() + "]\n\nOriginal: " + htmlContent;
        }
    }

    /**
     * Translate plain text from English to Chinese
     */
    public String translateToChinese(String text) {
        return translate(text, "EN", "ZH");
    }

    /**
     * Translate text between languages
     */
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        // Check if API key is configured
        if (!isConfigured()) {
            log.warn("Translation API key not configured, returning original text");
            return text;
        }

        try {
            // Split long text into chunks
            List<String> chunks = splitIntoChunks(text, 5000);
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                log.debug("Translating chunk {}/{}", i + 1, chunks.size());

                String translatedChunk = translateChunk(chunk, sourceLang, targetLang);
                result.append(translatedChunk);
                
                if (i < chunks.size() - 1) {
                    result.append("\n\n");
                }
            }

            return result.toString();
        } catch (Exception e) {
            log.error("Translation failed: {}", e.getMessage(), e);
            return "[Translation failed: " + e.getMessage() + "]\n\nOriginal: " + text;
        }
    }

    /**
     * Extract untranslatable blocks (code, pre, etc.) and replace with placeholders
     */
    private void extractUntranslatableBlocks(Element element, Map<String, String> placeholderMap) {
        for (String tag : UNTRANSLATABLE_TAGS) {
            Elements elements = element.getElementsByTag(tag);
            // Process in reverse order to avoid index issues
            for (int i = elements.size() - 1; i >= 0; i--) {
                Element el = elements.get(i);
                String placeholder = "__TRANSLATE_BLOCK_" + UUID.randomUUID().toString().substring(0, 8) + "__";
                placeholderMap.put(placeholder, el.outerHtml());
                
                // Replace with text node containing placeholder
                TextNode placeholderNode = new TextNode(placeholder);
                el.replaceWith(placeholderNode);
            }
        }
    }

    /**
     * Restore untranslatable blocks from placeholders
     */
    private void restoreUntranslatableBlocks(Element element, Map<String, String> placeholderMap) {
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(org.jsoup.nodes.Node node, int depth) {
                if (node instanceof TextNode) {
                    TextNode textNode = (TextNode) node;
                    String text = textNode.text();
                    Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
                    
                    if (matcher.find()) {
                        // Parse the placeholder back to HTML
                        String html = placeholderMap.get(matcher.group());
                        if (html != null) {
                            Document tempDoc = Jsoup.parseBodyFragment(html);
                            Element newElement = tempDoc.body().firstElementChild();
                            if (newElement != null) {
                                textNode.replaceWith(newElement);
                            }
                        }
                    }
                }
            }

            @Override
            public void tail(org.jsoup.nodes.Node node, int depth) {
                // Not needed
            }
        }, element);
    }

    /**
     * Collect all translatable text nodes
     */
    private void collectTextNodes(Element element, List<org.jsoup.nodes.Node> nodes) {
        for (org.jsoup.nodes.Node node : element.childNodes()) {
            if (node instanceof TextNode) {
                TextNode textNode = (TextNode) node;
                String text = textNode.text().trim();
                // Only collect non-empty, non-whitespace text
                if (!text.isEmpty() && !text.matches("\\s+")) {
                    nodes.add(textNode);
                }
            } else if (node instanceof Element) {
                Element el = (Element) node;
                // Skip untranslatable tags
                String tagName = el.tagName().toLowerCase();
                boolean skip = false;
                for (String untranslatable : UNTRANSLATABLE_TAGS) {
                    if (tagName.equals(untranslatable)) {
                        skip = true;
                        break;
                    }
                }
                if (!skip) {
                    collectTextNodes(el, nodes);
                }
            }
        }
    }

    /**
     * Group text nodes into chunks respecting size limit
     */
    private List<List<org.jsoup.nodes.Node>> groupIntoChunks(List<org.jsoup.nodes.Node> nodes, int maxChunkSize) {
        List<List<org.jsoup.nodes.Node>> chunks = new ArrayList<>();
        List<org.jsoup.nodes.Node> currentChunk = new ArrayList<>();
        int currentSize = 0;

        for (org.jsoup.nodes.Node node : nodes) {
            int nodeSize = getNodeTextLength(node);
            
            if (currentSize + nodeSize > maxChunkSize && !currentChunk.isEmpty()) {
                chunks.add(new ArrayList<>(currentChunk));
                currentChunk.clear();
                currentSize = 0;
            }
            
            currentChunk.add(node);
            currentSize += nodeSize;
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk);
        }

        return chunks;
    }

    /**
     * Get text length from a Node (handles both TextNode and Element)
     */
    private int getNodeTextLength(org.jsoup.nodes.Node node) {
        if (node instanceof TextNode) {
            return ((TextNode) node).text().length();
        } else if (node instanceof Element) {
            return ((Element) node).text().length();
        }
        return 0;
    }

    /**
     * Replace text content of an element while preserving structure where possible
     */
    private void replaceTextContent(Element element, String newText) {
        // Clear all child nodes
        element.empty();
        // Add new text
        element.appendChild(new TextNode(newText));
    }

    /**
     * Get text from a Node (handles both TextNode and Element)
     */
    private String getNodeText(org.jsoup.nodes.Node node) {
        if (node instanceof TextNode) {
            return ((TextNode) node).text();
        } else if (node instanceof Element) {
            return ((Element) node).text();
        }
        return "";
    }

    /**
     * Restore placeholders in translated text
     */
    private String restorePlaceholders(String text, Map<String, String> placeholderMap) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholderMap.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String translateChunk(String text, String sourceLang, String targetLang) {
        String response = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("auth_key", apiKey)
                        .queryParam("text", text)
                        .queryParam("source_lang", sourceLang)
                        .queryParam("target_lang", targetLang)
                        .queryParam("tag_handling", "html")
                        .queryParam("ignore_tags", "code,pre")
                        .queryParam("split_sentences", "1")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Empty response from translation API");
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode translations = jsonNode.get("translations");
            
            if (translations != null && translations.isArray() && translations.size() > 0) {
                return translations.get(0).get("text").asText();
            }
            
            throw new RuntimeException("Invalid translation response format");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse translation response: " + e.getMessage(), e);
        }
    }

    /**
     * Split text into chunks respecting sentence boundaries
     */
    private List<String> splitIntoChunks(String text, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        
        if (text.length() <= maxChunkSize) {
            chunks.add(text);
            return chunks;
        }

        StringBuilder currentChunk = new StringBuilder();
        String[] sentences = text.split("(?<=[.!?])\\s+");

        for (String sentence : sentences) {
            if (currentChunk.length() + sentence.length() > maxChunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }
            currentChunk.append(sentence).append(" ");
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }
}
