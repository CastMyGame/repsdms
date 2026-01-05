package com.reps.demogcloud.services.translation;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GoogleCloudTranslationService implements TranslationService {

    private final Translate translate;

    public GoogleCloudTranslationService() {
        // Uses GOOGLE_APPLICATION_CREDENTIALS / ADC under the hood
        this.translate = TranslateOptions.getDefaultInstance().getService();
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.isBlank()) {
            return text;
        }

        // Normalize target language
        if (targetLang == null || targetLang.isBlank()) {
            targetLang = "en";
        }

        // No-op if already English
        if ("en".equalsIgnoreCase(targetLang)) {
            return text;
        }

        try {
            Translation translation = translate.translate(
                    text,
                    sourceLang != null && !sourceLang.isBlank()
                            ? Translate.TranslateOption.sourceLanguage(sourceLang)
                            : Translate.TranslateOption.sourceLanguage("en"),
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
