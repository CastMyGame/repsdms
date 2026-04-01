package com.reps.demogcloud.services.email;

import com.reps.demogcloud.services.translation.TranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailTemplateBuilderServiceTest {

    @Mock
    private TranslationService translationService;

    @InjectMocks
    private EmailTemplateBuilderService service;

    @Test
    void createEmailText_shouldBuildEnglishTemplateWithoutTranslation() {
        String result = service.createEmailText(
                "John",
                "Doe",
                "1",
                "Tardy",
                "Late to class",
                "student@test.com",
                "en"
        );

        assertNotNull(result);
        assertTrue(result.contains("John Doe"));
        assertTrue(result.contains("Tardy"));
        assertTrue(result.contains("Late to class"));
        assertTrue(result.contains("student@test.com"));

        verifyNoInteractions(translationService);
    }

    @Test
    void createEmailText_shouldTranslateDescriptionForSpanish() {
        when(translationService.translate("Late to class", "en", "es")).thenReturn("Tarde a clase");

        String result = service.createEmailText(
                "John",
                "Doe",
                "1",
                "Tardy",
                "Late to class",
                "student@test.com",
                "es"
        );

        assertNotNull(result);
        assertTrue(result.contains("John Doe"));
        assertTrue(result.contains("Tarde a clase"));

        verify(translationService, times(1)).translate("Late to class", "en", "es");
    }

    @Test
    void createEmailText_shouldFallbackToOriginalDescriptionWhenTranslationFails() {
        when(translationService.translate("Late to class", "en", "es"))
                .thenThrow(new RuntimeException("translation failed"));

        String result = service.createEmailText(
                "John",
                "Doe",
                "1",
                "Tardy",
                "Late to class",
                "student@test.com",
                "es"
        );

        assertNotNull(result);
        assertTrue(result.contains("Late to class"));

        verify(translationService, times(1)).translate("Late to class", "en", "es");
    }

    @Test
    void createTextMessage_shouldBuildEnglishMessageWithoutTranslation() {
        String result = service.createTextMessage(
                "John",
                "Doe",
                "2",
                "Disruptive Behavior",
                "Talking in class",
                "en"
        );

        assertNotNull(result);
        assertTrue(result.contains("John Doe"));
        assertTrue(result.contains("Talking in class"));

        verifyNoInteractions(translationService);
    }

    @Test
    void createTextMessage_shouldTranslateDescriptionForSpanish() {
        when(translationService.translate("Talking in class", "en", "es"))
                .thenReturn("Hablando en clase");

        String result = service.createTextMessage(
                "John",
                "Doe",
                "2",
                "Disruptive Behavior",
                "Talking in class",
                "es"
        );

        assertNotNull(result);
        assertTrue(result.contains("Hablando en clase"));

        verify(translationService, times(1)).translate("Talking in class", "en", "es");
    }

    @Test
    void createCFRMessage_shouldBuildEnglishMessage() {
        String result = service.createCFRMessage(
                "Jane",
                "Doe",
                "Fight",
                "teacher@test.com",
                "student@test.com",
                "en"
        );

        assertNotNull(result);
        assertTrue(result.contains("Jane Doe"));
        assertTrue(result.contains("teacher@test.com"));
        assertTrue(result.contains("student@test.com"));

        verifyNoInteractions(translationService);
    }

    @Test
    void replaceString_shouldRemoveBracketCommaPatterns() {
        String result = service.replaceString("[, hello ,]");

        assertEquals("hello", result);
    }

    @Test
    void adjustString_shouldInsertSpaceBeforeTrailingNumber() {
        String result = service.adjustString("Level3");

        assertEquals("Level 3", result);
    }

    @Test
    void normalizeLanguage_shouldReturnEnWhenNull() {
        assertEquals("en", service.normalizeLanguage(null));
    }

    @Test
    void normalizeLanguage_shouldReturnEnWhenBlank() {
        assertEquals("en", service.normalizeLanguage("   "));
    }

    @Test
    void normalizeLanguage_shouldLowercaseLanguage() {
        assertEquals("es", service.normalizeLanguage("ES"));
    }

    @Test
    void buildSubject_shouldBuildOfficeReferralSubjectInEnglish() {
        String result = service.buildSubject("Burke High", "John", "Doe", "en", true);

        assertNotNull(result);
        assertTrue(result.contains("Burke High"));
        assertTrue(result.contains("John Doe"));
    }

    @Test
    void buildSubject_shouldBuildRegularReferralSubjectInSpanish() {
        String result = service.buildSubject("Burke High", "John", "Doe", "es", false);

        assertNotNull(result);
        assertTrue(result.contains("Burke High"));
        assertTrue(result.contains("John Doe"));
    }

    @Test
    void translateUserInput_shouldReturnSameTextForEnglish() {
        String result = service.translateUserInput("Hello there", "en");

        assertEquals("Hello there", result);
        verifyNoInteractions(translationService);
    }

    @Test
    void translateUserInput_shouldTranslateForNonEnglish() {
        when(translationService.translate("Hello there", "en", "es")).thenReturn("Hola");

        String result = service.translateUserInput("Hello there", "es");

        assertEquals("Hola", result);
        verify(translationService, times(1)).translate("Hello there", "en", "es");
    }

    @Test
    void buildOfficeReferralMessage_shouldBuildMessage() {
        String result = service.buildOfficeReferralMessage(
                "John",
                "Doe",
                "Fight",
                "Summary text",
                "en"
        );

        assertNotNull(result);
        assertTrue(result.contains("John Doe"));
        assertTrue(result.contains("Fight"));
        assertTrue(result.contains("Summary text"));
    }

    @Test
    void buildCompletionSubject_shouldBuildWithSchool() {
        String result = service.buildCompletionSubject("Burke High", "John", "Doe", "en", true);

        assertNotNull(result);
        assertTrue(result.contains("Burke High"));
        assertTrue(result.contains("John Doe"));
    }

    @Test
    void buildCompletionSubject_shouldBuildWithoutSchool() {
        String result = service.buildCompletionSubject("Burke High", "John", "Doe", "en", false);

        assertNotNull(result);
        assertTrue(result.contains("John Doe"));
    }

    @Test
    void buildCompletionMessage_shouldBuildMessageWithoutTranslation() {
        String result = service.buildCompletionMessage(
                "John",
                "Doe",
                "Tardy",
                "teacher@test.com",
                "en"
        );

        assertNotNull(result);
        assertTrue(result.contains("John Doe"));
        assertTrue(result.contains("Tardy"));
        assertTrue(result.contains("teacher@test.com"));

        verifyNoInteractions(translationService);
    }

    @Test
    void buildLevelThreeRejectSubject_shouldBuildSubject() {
        String result = service.buildLevelThreeRejectSubject("John", "Doe", "en");

        assertNotNull(result);
        assertTrue(result.contains("John Doe"));
    }

    @Test
    void buildLevelThreeRejectMessage_shouldTranslateFeedbackForSpanish() {
        when(translationService.translate("- feedback line", "en", "es"))
                .thenReturn("- línea de comentarios");

        String result = service.buildLevelThreeRejectMessage("- feedback line", "es");

        assertNotNull(result);
        assertTrue(result.contains("- línea de comentarios"));
        verify(translationService, times(1)).translate("- feedback line", "en", "es");
    }

    @Test
    void buildPunishmentDeletedSubject_shouldBuildSubject() {
        String result = service.buildPunishmentDeletedSubject("Burke High", "John", "Doe", "en");

        assertNotNull(result);
        assertTrue(result.contains("Burke High"));
        assertTrue(result.contains("John Doe"));
    }

    @Test
    void buildPunishmentDeletedMessage_shouldTranslateExplanationWhenNeeded() {
        when(translationService.translate("Teacher request", "en", "es"))
                .thenReturn("Solicitud del maestro");

        String result = service.buildPunishmentDeletedMessage(
                "John",
                "Doe",
                "Tardy",
                "2",
                "Teacher request",
                "es"
        );

        assertNotNull(result);
        assertTrue(result.contains("John Doe"));
        assertTrue(result.contains("Tardy"));
        assertTrue(result.contains("2"));
        assertTrue(result.contains("Solicitud del maestro"));

        verify(translationService, times(1)).translate("Teacher request", "en", "es");
    }

    @Test
    void buildPunishmentDeletedMessage_shouldFallbackWhenTranslationFails() {
        when(translationService.translate("Teacher request", "en", "es"))
                .thenThrow(new RuntimeException("translation failed"));

        String result = service.buildPunishmentDeletedMessage(
                "John",
                "Doe",
                "Tardy",
                "2",
                "Teacher request",
                "es"
        );

        assertNotNull(result);
        assertTrue(result.contains("Teacher request"));

        verify(translationService, times(1)).translate("Teacher request", "en", "es");
    }

    @Test
    void buildPunishmentRestoredSubject_shouldBuildSubject() {
        String result = service.buildPunishmentRestoredSubject("Burke High", "John", "Doe", "en");

        assertNotNull(result);
        assertTrue(result.contains("Burke High"));
        assertTrue(result.contains("John Doe"));
    }

    @Test
    void buildPunishmentRestoredMessage_shouldBuildMessage() {
        String result = service.buildPunishmentRestoredMessage("John", "Doe", "en");

        assertNotNull(result);
        assertTrue(result.contains("John Doe"));
    }

    @Test
    void translateUserInput_shouldReturnNullWhenInputIsNull() {
        String result = service.translateUserInput(null, "es");

        assertNull(result);
        verifyNoInteractions(translationService);
    }

    @Test
    void translateUserInput_shouldReturnBlankWhenInputIsBlank() {
        String result = service.translateUserInput("   ", "es");

        assertEquals("   ", result);
        verifyNoInteractions(translationService);
    }
}