package com.reps.demogcloud.services.translation;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GoogleCloudTranslationService implements TranslationService {

    private static final String DEFAULT_LANGUAGE = "en";

    private volatile Translate translate;

    public GoogleCloudTranslationService() {
        // English-only email and application startup do not need cloud credentials.
    }

    public GoogleCloudTranslationService(Translate translate) {
        this.translate = translate;
    }

    private synchronized Translate translationClient() {
        if (translate == null) {
            // Preserve ADC-based translation, but initialize only when needed.
            translate = TranslateOptions.getDefaultInstance().getService();
        }
        return translate;
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.isBlank()) {
            return text;
        }

        // Normalize target language
        if (targetLang == null || targetLang.isBlank()) {
            targetLang = DEFAULT_LANGUAGE;
        }

        // No-op if already English
        if (DEFAULT_LANGUAGE.equalsIgnoreCase(targetLang)) {
            return text;
        }

        try {
            Translation translation = translationClient().translate(
                    text,
                    sourceLang != null && !sourceLang.isBlank()
                            ? Translate.TranslateOption.sourceLanguage(sourceLang)
                            : Translate.TranslateOption.sourceLanguage(DEFAULT_LANGUAGE),
                    Translate.TranslateOption.targetLanguage(targetLang)
                    // You could also specify model, format, etc.
                    // TranslateOption.model("nmt")
                    // TranslateOption.format("html")  // for HTML emails
            );

            return translation.getTranslatedText();
        } catch (Exception ex) {
            log.error("Failed to translate text to {}. Falling back to original. Error: {}", targetLang, ex.getMessage());
            return text; // graceful fallback
        }
    }
}
