package com.translationapp.translator;

import java.util.regex.Pattern;

public final class TranslationMarkers {

    private static final Pattern NODE_SPLIT_PATTERN = Pattern.compile("\\n?<<<NODE_(\\d+)>>>\\n?");

    private static final Pattern LEGACY_SENTENCE_BREAK_PATTERN =
            Pattern.compile("\\n?__SENTENCE_BREAK__\\n?");

    private TranslationMarkers() {
    }

    /** Detects legacy failure markers baked into stored HTML from older translator versions. */
    public static boolean containsFailureMarker(String content) {
        return content != null && content.contains("[Translation failed:");
    }

    /** Strip internal chunk delimiters that may leak into rendered HTML. */
    public static String sanitizeInternalMarkers(String html) {
        if (html == null || html.isEmpty()) {
            return html == null ? "" : html;
        }
        String cleaned = LEGACY_SENTENCE_BREAK_PATTERN.matcher(html).replaceAll("");
        cleaned = NODE_SPLIT_PATTERN.matcher(cleaned).replaceAll("");
        return cleaned;
    }
}
