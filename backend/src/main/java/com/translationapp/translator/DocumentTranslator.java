package com.translationapp.translator;

/**
 * Pluggable document translation engine (DeepL, Azure, Tencent, Ollama, etc.).
 */
public interface DocumentTranslator {

    /** Provider id matching {@link TranslationProvider} enum name (lowercase). */
    String getProviderId();

    /** Whether this engine has valid credentials/configuration. */
    boolean isConfigured();

    /** Translate HTML fragment EN→ZH while preserving structure. */
    String translateHtmlToChinese(String htmlContent);

    /** Translate plain text EN→ZH. */
    String translateToChinese(String text);
}
