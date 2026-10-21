package com.reps.demogcloud.services.translation;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleCloudTranslationServiceTest {

    @Mock
    private Translate translate;

    @Mock
    private Translation translation;

    private GoogleCloudTranslationService service;

    @BeforeEach
    void setUp() {
        service = new GoogleCloudTranslationService(translate);
    }

    @Test
    void startupAndEnglishEmailDoNotDiscoverCloudCredentials() {
        try (var options = mockStatic(TranslateOptions.class)) {
            var local = new GoogleCloudTranslationService();
            assertEquals("Hello", local.translate("Hello", "en", "en"));
            options.verifyNoInteractions();
        }
    }

    @Test
    void initializesTranslationClientOnceOnFirstNonEnglishRequest() {
        var configured = mock(TranslateOptions.class);
        when(configured.getService()).thenReturn(translate);
        when(translate.translate(eq("Hello"), any(Translate.TranslateOption[].class))).thenReturn(translation);
        when(translation.getTranslatedText()).thenReturn("Hola");
        try (var options = mockStatic(TranslateOptions.class)) {
            options.when(TranslateOptions::getDefaultInstance).thenReturn(configured);
            var local = new GoogleCloudTranslationService();
            assertEquals("Hola", local.translate("Hello", "en", "es"));
            assertEquals("Hola", local.translate("Hello", "en", "es"));
            options.verify(TranslateOptions::getDefaultInstance, times(1));
        }
    }

    @Test
    void translate_shouldReturnNull_whenTextIsNull() {
        String result = service.translate(null, "en", "es");

        assertNull(result);
        verifyNoInteractions(translate);
    }

    @Test
    void translate_shouldReturnBlank_whenTextIsBlank() {
        String result = service.translate("   ", "en", "es");

        assertEquals("   ", result);
        verifyNoInteractions(translate);
    }

    @Test
    void translate_shouldReturnOriginal_whenTargetLangIsNull() {
        String result = service.translate("Hello world", "en", null);

        assertEquals("Hello world", result);
        verifyNoInteractions(translate);
    }

    @Test
    void translate_shouldReturnOriginal_whenTargetLangIsBlank() {
        String result = service.translate("Hello world", "en", "   ");

        assertEquals("Hello world", result);
        verifyNoInteractions(translate);
    }

    @Test
    void translate_shouldReturnOriginal_whenTargetLangIsEnglish() {
        String result = service.translate("Hello world", "en", "en");

        assertEquals("Hello world", result);
        verifyNoInteractions(translate);
    }

    @Test
    void translate_shouldTranslate_whenSourceLangProvided() {
        when(translate.translate(
                eq("Hello world"),
                argThat(sourceLanguageOption()),
                argThat(targetLanguageOption("es"))
        )).thenReturn(translation);

        when(translation.getTranslatedText()).thenReturn("Hola mundo");

        String result = service.translate("Hello world", "en", "es");

        assertEquals("Hola mundo", result);
        verify(translate, times(1)).translate(
                eq("Hello world"),
                argThat(sourceLanguageOption()),
                argThat(targetLanguageOption("es"))
        );
        verify(translation, times(1)).getTranslatedText();
    }

    @Test
    void translate_shouldDefaultSourceLangToEnglish_whenSourceLangIsNull() {
        when(translate.translate(
                eq("Hello world"),
                argThat(sourceLanguageOption()),
                argThat(targetLanguageOption("fr"))
        )).thenReturn(translation);

        when(translation.getTranslatedText()).thenReturn("Bonjour le monde");

        String result = service.translate("Hello world", null, "fr");

        assertEquals("Bonjour le monde", result);
        verify(translate, times(1)).translate(
                eq("Hello world"),
                argThat(sourceLanguageOption()),
                argThat(targetLanguageOption("fr"))
        );
    }

    @Test
    void translate_shouldDefaultSourceLangToEnglish_whenSourceLangIsBlank() {
        when(translate.translate(
                eq("Hello world"),
                argThat(sourceLanguageOption()),
                argThat(targetLanguageOption("de"))
        )).thenReturn(translation);

        when(translation.getTranslatedText()).thenReturn("Hallo Welt");

        String result = service.translate("Hello world", "   ", "de");

        assertEquals("Hallo Welt", result);
        verify(translate, times(1)).translate(
                eq("Hello world"),
                argThat(sourceLanguageOption()),
                argThat(targetLanguageOption("de"))
        );
    }

    @Test
    void translate_shouldReturnOriginal_whenTranslateThrowsException() {
        when(translate.translate(
                eq("Hello world"),
                argThat(sourceLanguageOption()),
                argThat(targetLanguageOption("es"))
        )).thenThrow(new RuntimeException("boom"));

        String result = service.translate("Hello world", "en", "es");

        assertEquals("Hello world", result);
        verify(translate, times(1)).translate(
                eq("Hello world"),
                argThat(sourceLanguageOption()),
                argThat(targetLanguageOption("es"))
        );
    }

    private ArgumentMatcher<Translate.TranslateOption> sourceLanguageOption() {
        return option -> option != null && option.toString().contains("en");
    }

    private ArgumentMatcher<Translate.TranslateOption> targetLanguageOption(String expectedLanguage) {
        return option -> option != null && option.toString().contains(expectedLanguage);
    }
}
