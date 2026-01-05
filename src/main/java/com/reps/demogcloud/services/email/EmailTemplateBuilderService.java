package com.reps.demogcloud.services.email;

import com.reps.demogcloud.services.translation.TranslationService;
import org.springframework.stereotype.Service;

import static com.reps.demogcloud.models.email.EmailTemplates.*;

@Service
public class EmailTemplateBuilderService {

    private final TranslationService translationService;

    public EmailTemplateBuilderService(TranslationService translationService) {
        this.translationService = translationService;
    }

    public String createEmailText(String studentFirstName, String studentLastName, String infractionLevel, String infractionName, String description, String studentEmail, String targetLanguageCode) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template = switch (language) {
            case "es" -> EMAIL_ES;
                    default -> EMAIL_EN;
        };

        String translatedDescription = translateDescriptionIfNeeded(description, language);

        return fillTemplate(template, java.util.Map.of(
                "studentFullName", studentFullName,
                "infractionLevel", infractionLevel,
                "infractionName", infractionName,
                "description", translatedDescription,
                "studentEmail", studentEmail
        ));
    }

    public String createTextMessage(String studentFirstName, String studentLastName, String infractionLevel, String infractionName, String description, String targetLanguageCode) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template = "es".equals(language) ? TEXT_ES : TEXT_EN;

        String translatedDescription = translateDescriptionIfNeeded(description, language);

        return fillTemplate(template, java.util.Map.of(
                "studentFullName", studentFullName,
                "infractionLevel", infractionLevel,
                "infractionName", infractionName,
                "description", translatedDescription
        ));
    }

    public String createCFRMessage(String studentFirstName, String studentLastName, String infractionName, String teacherEmail, String studentEmail, String targetLanguageCode) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template = "es".equals(language) ? CFR_ES : CFR_EN;

        return fillTemplate(template, java.util.Map.of(
                "studentFullName", studentFullName,
                "infractionName", infractionName,
                "teacherEmail", teacherEmail,
                "studentEmail", studentEmail
        ));
    }

    public String replaceString(String input) {
        return input.replace("[,", "").replace(",]", "").trim();
    }

    public String adjustString(String input) {
        return input.replaceAll("(.*?)(\\d+)$", "$1 $2").trim();
    }
    public String normalizeLanguage(String lang) {
        if (lang == null || lang.isBlank()) {
            return "en";
        }
        return lang.toLowerCase();
    }

    private String translateDescriptionIfNeeded(String descriptionEn, String lang) {
        if (descriptionEn == null || descriptionEn.isBlank()) return descriptionEn;
        if ("en".equals(lang)) return descriptionEn;

        try {
            // IMPORTANT: only translate THIS field
            return translationService.translate(descriptionEn, "en", lang);
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(EmailTemplateBuilderService.class)
                    .error("translateDescriptionIfNeeded failed for lang {}: {}", lang, ex.getMessage());
            return descriptionEn;
        }
    }

    private String fillTemplate(String template, java.util.Map<String, String> values) {
        String result = template;
        for (var entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() == null ? "" : entry.getValue());
        }
        return replaceString(result);
    }

    public String buildSubject(String schoolName, String studentFirstName, String studentLastName,
                               String targetLanguageCode, boolean officeReferral) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template;
        if (officeReferral) {
            template = "es".equals(language) ? SUBJECT_OFFICE_REFERRAL_ES : SUBJECT_OFFICE_REFERRAL_EN;
        } else {
            template = "es".equals(language) ? SUBJECT_REFERRAL_ES : SUBJECT_REFERRAL_EN;
        }

        return fillTemplate(template, java.util.Map.of(
                "schoolName", schoolName,
                "studentFullName", studentFullName
        ));
    }

    public String translateUserInput(String text, String targetLanguageCode) {
        String language = normalizeLanguage(targetLanguageCode);
        return translateDescriptionIfNeeded(text, language); // reuse your existing safe logic
    }

    public String buildOfficeReferralMessage(String studentFirstName, String studentLastName,
                                             String infractionName, String summary, String targetLanguageCode) {
        String language = normalizeLanguage(targetLanguageCode);
        String template = "es".equals(language) ? OFFICE_REFERRAL_MSG_ES : OFFICE_REFERRAL_MSG_EN;

        return fillTemplate(template, java.util.Map.of(
                "studentFullName", studentFirstName + " " + studentLastName,
                "infractionName", infractionName,
                "summary", summary
        ));
    }

    public String buildCompletionSubject(String schoolName,
                                         String studentFirstName,
                                         String studentLastName,
                                         String targetLanguageCode,
                                         boolean keepSchoolInSubject) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template;
        if (keepSchoolInSubject) {
            template = "es".equals(language) ? SUBJECT_COMPLETION_WITH_SCHOOL_ES : SUBJECT_COMPLETION_WITH_SCHOOL_EN;
        } else {
            template = "es".equals(language) ? SUBJECT_COMPLETION_ES : SUBJECT_COMPLETION_EN;
        }

        // schoolName placeholder is only used in the WITH_SCHOOL template, but harmless to always pass it
        return fillTemplate(template, java.util.Map.of(
                "schoolName", schoolName == null ? "" : schoolName,
                "studentFullName", studentFullName
        ));
    }

    /**
     * Build completion email body.
     * No user input in this message today -> no translation calls needed.
     */
    public String buildCompletionMessage(String studentFirstName,
                                         String studentLastName,
                                         String infractionName,
                                         String teacherEmail,
                                         String targetLanguageCode) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template = "es".equals(language) ? COMPLETION_MSG_ES : COMPLETION_MSG_EN;

        return fillTemplate(template, java.util.Map.of(
                "studentFullName", studentFullName,
                "infractionName", infractionName == null ? "" : infractionName,
                "teacherEmail", teacherEmail == null ? "" : teacherEmail
        ));
    }

    public String buildLevelThreeRejectSubject(String studentFirstName,
                                               String studentLastName,
                                               String targetLanguageCode) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template = "es".equals(language) ? SUBJECT_L3_REJECT_ES : SUBJECT_L3_REJECT_EN;

        return fillTemplate(template, java.util.Map.of(
                "studentFullName", studentFullName
        ));
    }

    public String buildLevelThreeRejectMessage(String feedbackRaw,
                                               String targetLanguageCode) {
        String language = normalizeLanguage(targetLanguageCode);

        // translate ONLY the user input portion
        String feedbackTranslated = translateUserInput(feedbackRaw, language);

        String template = "es".equals(language) ? L3_REJECT_MSG_ES : L3_REJECT_MSG_EN;

        return fillTemplate(template, java.util.Map.of(
                "feedback", feedbackTranslated == null ? "" : feedbackTranslated
        ));
    }

    public String buildPunishmentDeletedSubject(String schoolName,
                                                String studentFirstName,
                                                String studentLastName,
                                                String targetLanguageCode) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template = "es".equals(language)
                ? SUBJECT_PUNISHMENT_DELETED_ES
                : SUBJECT_PUNISHMENT_DELETED_EN;

        return fillTemplate(template, java.util.Map.of(
                "schoolName", schoolName == null ? "" : schoolName,
                "studentFullName", studentFullName
        ));
    }

    public String buildPunishmentDeletedMessage(String studentFirstName,
                                                String studentLastName,
                                                String infractionName,
                                                String infractionLevel,
                                                String explanationUserInput,
                                                String targetLanguageCode) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        // Translate ONLY the user-input explanation (if you include it)
        String explanation = translateUserInput(explanationUserInput, language);

        String template = "es".equals(language)
                ? PUNISHMENT_DELETED_MSG_ES
                : PUNISHMENT_DELETED_MSG_EN;

        return fillTemplate(template, java.util.Map.of(
                "studentFullName", studentFullName,
                "infractionName", infractionName == null ? "" : infractionName,
                "infractionLevel", infractionLevel == null ? "" : infractionLevel,
                "explanation", explanation == null ? "" : explanation
        ));
    }

    public String buildPunishmentRestoredSubject(String schoolName,
                                                 String studentFirstName,
                                                 String studentLastName,
                                                 String targetLanguageCode) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template = "es".equals(language)
                ? SUBJECT_PUNISHMENT_RESTORED_ES
                : SUBJECT_PUNISHMENT_RESTORED_EN;

        return fillTemplate(template, java.util.Map.of(
                "schoolName", schoolName == null ? "" : schoolName,
                "studentFullName", studentFullName
        ));
    }

    public String buildPunishmentRestoredMessage(String studentFirstName,
                                                 String studentLastName,
                                                 String targetLanguageCode) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template = "es".equals(language)
                ? PUNISHMENT_RESTORED_MSG_ES
                : PUNISHMENT_RESTORED_MSG_EN;

        return fillTemplate(template, java.util.Map.of(
                "studentFullName", studentFullName
        ));
    }
}