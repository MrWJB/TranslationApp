package com.translationapp.translator;

import java.util.Locale;

public enum TranslationProvider {
    DEEPL,
    AZURE,
    TENCENT,
    OLLAMA;

    public static TranslationProvider fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return DEEPL;
        }
        return TranslationProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
