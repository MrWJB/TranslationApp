package com.translationapp.translator;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared Jsoup-based HTML translation pipeline; subclasses implement provider-specific API calls.
 */
@Slf4j
public abstract class AbstractHtmlDocumentTranslator implements DocumentTranslator {

    private static final String[] UNTRANSLATABLE_TAGS = {
            "code", "pre", "script", "style", "img", "br", "hr", "svg", "path"
    };

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("__TRANSLATE_BLOCK_[a-zA-Z0-9-]+__");
    private static final Pattern NODE_SPLIT_PATTERN = Pattern.compile("\\n?<<<NODE_(\\d+)>>>\\n?");

    protected abstract String translateChunk(String text, boolean htmlMode);

    protected abstract String providerDisplayName();

    @Override
    public String translateHtmlToChinese(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            return "";
        }
        if (!isConfigured()) {
            log.warn("{} not configured, returning original HTML", providerDisplayName());
            return htmlContent;
        }
        try {
            return translateHtmlStructure(htmlContent, text -> translateChunk(text, true));
        } catch (TranslationFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("{} HTML translation failed: {}", providerDisplayName(), e.getMessage(), e);
            throw new TranslationFailedException(getProviderId(), userFacingError(e), e);
        }
    }

    @Override
    public String translateToChinese(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        if (!isConfigured()) {
            log.warn("{} not configured, returning original text", providerDisplayName());
            return text;
        }
        try {
            List<String> chunks = splitIntoPlainChunks(text, 5000);
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < chunks.size(); i++) {
                result.append(translateChunk(chunks.get(i), false));
                if (i < chunks.size() - 1) {
                    result.append("\n\n");
                }
            }
            return result.toString();
        } catch (TranslationFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("{} text translation failed: {}", providerDisplayName(), e.getMessage(), e);
            throw new TranslationFailedException(getProviderId(), userFacingError(e), e);
        }
    }

    /** HTML pipeline (protected for unit tests with stub chunk translator). */
    protected String translateHtmlStructure(String htmlContent, Function<String, String> chunkTranslator) {
        Document doc = Jsoup.parseBodyFragment(htmlContent);
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.html);

        Map<String, String> placeholderMap = new HashMap<>();
        extractUntranslatableBlocks(doc.body(), placeholderMap);

        List<org.jsoup.nodes.Node> textNodes = new ArrayList<>();
        collectTextNodes(doc.body(), textNodes);

        if (textNodes.isEmpty()) {
            return TranslationMarkers.sanitizeInternalMarkers(htmlContent);
        }

        List<List<org.jsoup.nodes.Node>> chunks = groupIntoChunks(textNodes, 3000);
        for (List<org.jsoup.nodes.Node> chunk : chunks) {
            translateNodeChunk(chunk, chunkTranslator);
        }

        restoreUntranslatableBlocks(doc.body(), placeholderMap);
        return TranslationMarkers.sanitizeInternalMarkers(doc.body().html());
    }

    private void translateNodeChunk(List<org.jsoup.nodes.Node> chunk, Function<String, String> chunkTranslator) {
        String textToTranslate = buildChunkText(chunk);
        String translatedText = chunkTranslator.apply(textToTranslate);
        List<String> translatedParts = splitChunkTranslation(translatedText, chunk.size());

        if (translatedParts.size() != chunk.size()) {
            log.warn(
                    "{} chunk split mismatch (expected {}, got {}); translating nodes individually",
                    providerDisplayName(),
                    chunk.size(),
                    translatedParts.size());
            for (org.jsoup.nodes.Node node : chunk) {
                String nodeText = getNodeText(node).trim();
                if (nodeText.isEmpty()) {
                    continue;
                }
                applyTranslatedText(node, chunkTranslator.apply(nodeText).trim());
            }
            return;
        }

        for (int i = 0; i < chunk.size(); i++) {
            applyTranslatedText(chunk.get(i), translatedParts.get(i).trim());
        }
    }

    private String buildChunkText(List<org.jsoup.nodes.Node> chunk) {
        StringBuilder textToTranslate = new StringBuilder();
        for (int i = 0; i < chunk.size(); i++) {
            if (i > 0) {
                textToTranslate.append('\n').append(nodeSplitMarker(i - 1)).append('\n');
            }
            textToTranslate.append(getNodeText(chunk.get(i)));
        }
        return textToTranslate.toString();
    }

    private static String nodeSplitMarker(int index) {
        return "<<<NODE_" + index + ">>>";
    }

    private List<String> splitChunkTranslation(String translatedText, int expectedCount) {
        if (expectedCount <= 1) {
            return List.of(translatedText);
        }
        String[] exactParts = translatedText.split("\n<<<NODE_\\d+>>>\n");
        if (exactParts.length == expectedCount) {
            return List.of(exactParts);
        }
        List<String> tolerantParts = new ArrayList<>();
        Matcher matcher = NODE_SPLIT_PATTERN.matcher(translatedText);
        int lastEnd = 0;
        while (matcher.find()) {
            tolerantParts.add(translatedText.substring(lastEnd, matcher.start()));
            lastEnd = matcher.end();
        }
        tolerantParts.add(translatedText.substring(lastEnd));
        if (tolerantParts.size() == expectedCount) {
            return tolerantParts;
        }
        return List.of();
    }

    private void applyTranslatedText(org.jsoup.nodes.Node node, String translatedText) {
        if (node instanceof TextNode textNode) {
            textNode.text(translatedText);
        } else if (node instanceof Element element) {
            element.empty();
            element.appendChild(new TextNode(translatedText));
        }
    }

    private void extractUntranslatableBlocks(Element element, Map<String, String> placeholderMap) {
        for (String tag : UNTRANSLATABLE_TAGS) {
            Elements elements = element.getElementsByTag(tag);
            for (int i = elements.size() - 1; i >= 0; i--) {
                Element el = elements.get(i);
                String placeholder = "__TRANSLATE_BLOCK_" + UUID.randomUUID().toString().substring(0, 8) + "__";
                placeholderMap.put(placeholder, el.outerHtml());
                el.replaceWith(new TextNode(placeholder));
            }
        }
    }

    private void restoreUntranslatableBlocks(Element element, Map<String, String> placeholderMap) {
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(org.jsoup.nodes.Node node, int depth) {
                if (!(node instanceof TextNode textNode)) {
                    return;
                }
                String text = textNode.getWholeText();
                Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
                if (!matcher.find()) {
                    return;
                }
                List<org.jsoup.nodes.Node> replacements = new ArrayList<>();
                matcher.reset();
                int lastIndex = 0;
                while (matcher.find()) {
                    if (matcher.start() > lastIndex) {
                        replacements.add(new TextNode(text.substring(lastIndex, matcher.start())));
                    }
                    appendPlaceholderNodes(replacements, placeholderMap.get(matcher.group()), matcher.group());
                    lastIndex = matcher.end();
                }
                if (lastIndex < text.length()) {
                    replacements.add(new TextNode(text.substring(lastIndex)));
                }
                org.jsoup.nodes.Node parent = textNode.parent();
                if (!(parent instanceof Element parentElement) || replacements.isEmpty()) {
                    return;
                }
                int index = textNode.siblingIndex();
                textNode.remove();
                parentElement.insertChildren(index, replacements);
            }

            @Override
            public void tail(org.jsoup.nodes.Node node, int depth) {
            }
        }, element);
    }

    private void appendPlaceholderNodes(List<org.jsoup.nodes.Node> replacements, String html, String placeholderToken) {
        if (html == null) {
            replacements.add(new TextNode(placeholderToken));
            return;
        }
        Document tempDoc = Jsoup.parseBodyFragment(html);
        for (org.jsoup.nodes.Node child : tempDoc.body().childNodes()) {
            replacements.add(child.clone());
        }
    }

    private void collectTextNodes(Element element, List<org.jsoup.nodes.Node> nodes) {
        for (org.jsoup.nodes.Node node : element.childNodes()) {
            if (node instanceof TextNode textNode) {
                String trimmed = textNode.text().trim();
                if (trimmed.isEmpty() || trimmed.matches("\\s+")) {
                    continue;
                }
                if (PLACEHOLDER_PATTERN.matcher(trimmed).matches()) {
                    continue;
                }
                nodes.add(textNode);
            } else if (node instanceof Element el) {
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

    private int getNodeTextLength(org.jsoup.nodes.Node node) {
        if (node instanceof TextNode textNode) {
            return textNode.text().length();
        }
        if (node instanceof Element element) {
            return element.text().length();
        }
        return 0;
    }

    private String getNodeText(org.jsoup.nodes.Node node) {
        if (node instanceof TextNode textNode) {
            return textNode.text();
        }
        if (node instanceof Element element) {
            return element.text();
        }
        return "";
    }

    private List<String> splitIntoPlainChunks(String text, int maxChunkSize) {
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

    protected String userFacingError(Throwable error) {
        if (error instanceof TranslationFailedException tfe) {
            return tfe.getMessage();
        }
        String message = error.getMessage();
        return message != null ? message : "unexpected error (check server logs)";
    }
}
