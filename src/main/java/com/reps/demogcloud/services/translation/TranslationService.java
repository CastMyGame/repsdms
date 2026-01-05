package com.reps.demogcloud.services.translation;

public interface TranslationService {
    /**
     * Translate text from sourceLang -> targetLang.
     * sourceLang can be "en" or null (auto-detect).
     */
    String translate(String text, String sourceLang, String targetLang);
}
