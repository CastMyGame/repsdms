package com.reps.demogcloud.services.email;

import com.reps.demogcloud.services.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.reps.demogcloud.models.email.EmailTemplates.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailTemplateBuilderService {
    @org.springframework.beans.factory.annotation.Value("${reps.accounts.public-base-url:}")
    private String accountBaseUrl;
    @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:}")
    private String activeProfiles;

    private final TranslationService translationService;

    public String createEmailText(
            String studentFirstName,
            String studentLastName,
            String infractionLevel,
            String infractionName,
            String description,
            String studentEmail,
            String targetLanguageCode
    ) {
        if (positive(infractionName)) return buildPositiveMessage(studentFirstName,studentLastName,description,targetLanguageCode);
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

    public String createTextMessage(
            String studentFirstName,
            String studentLastName,
            String infractionLevel,
            String infractionName,
            String description,
            String targetLanguageCode
    ) {
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

    public String createCFRMessage(
            String studentFirstName,
            String studentLastName,
            String infractionName,
            String description,
            String teacherEmail,
            String studentEmail,
            String targetLanguageCode
    ) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template = "es".equals(language) ? CFR_ES : CFR_EN;

        return fillTemplate(template, java.util.Map.of(
                "studentFullName", studentFullName,
                "infractionName", infractionName,
                "description", translateDescriptionIfNeeded(description, language),
                "teacherEmail", teacherEmail,
                "studentEmail", studentEmail
        ));
    }

    private String loginUrl() {
        return FrontendLinks.origin(accountBaseUrl, java.util.Arrays.asList(java.util.Objects.toString(activeProfiles, "").split(",")).contains("prod")) + "/login";
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
        return lang.trim().toLowerCase(java.util.Locale.ROOT).split("[-_]", 2)[0].equals("es") ? "es" : "en";
    }

    private String translateDescriptionIfNeeded(String descriptionEn, String lang) {
        if (descriptionEn == null || descriptionEn.isBlank()) {
            return descriptionEn;
        }
        if ("en".equals(lang)) {
            return descriptionEn;
        }

        try {
            String translated=translationService.translate(descriptionEn, "en", lang);
            if(translated==null || translated.isBlank()) throw new IllegalStateException("Translation unavailable");
            return translated;
        } catch (Exception ex) {
            log.warn("Description translation unavailable for supported language {}; preserving original text", lang);
            return descriptionEn;
        }
    }

    private String fillTemplate(String template, java.util.Map<String, String> values) {
        return render(template,values,true);
    }
    private String fillSubject(String template, java.util.Map<String,String> values) {
        String subject=render(template,values,false);
        if(subject.contains("\r") || subject.contains("\n")) throw new IllegalArgumentException("Unsafe email subject");
        return subject;
    }
    private String render(String template, java.util.Map<String,String> values, boolean html) {
        var fields=new java.util.HashMap<>(values);
        if(template.contains("{{loginUrl}}")) fields.put("loginUrl",loginUrl());
        return EmailContent.render(template,fields,html);
    }

    public String buildSubject(
            String schoolName,
            String studentFirstName,
            String studentLastName,
            String targetLanguageCode,
            boolean officeReferral
    ) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template;
        if (officeReferral) {
            template = "es".equals(language) ? SUBJECT_OFFICE_REFERRAL_ES : SUBJECT_OFFICE_REFERRAL_EN;
        } else {
            template = "es".equals(language) ? SUBJECT_REFERRAL_ES : SUBJECT_REFERRAL_EN;
        }

        return fillSubject(template, java.util.Map.of(
                "schoolName", schoolName,
                "studentFullName", studentFullName
        ));
    }

    public String translateUserInput(String text, String targetLanguageCode) {
        String language = normalizeLanguage(targetLanguageCode);
        return translateDescriptionIfNeeded(text, language);
    }

    public String buildOfficeReferralMessage(
            String studentFirstName,
            String studentLastName,
            String infractionName,
            String summary,
            String targetLanguageCode
    ) {
        String language = normalizeLanguage(targetLanguageCode);
        String template = "es".equals(language) ? OFFICE_REFERRAL_MSG_ES : OFFICE_REFERRAL_MSG_EN;

        return fillTemplate(template, java.util.Map.of(
                "studentFullName", studentFirstName + " " + studentLastName,
                "infractionName", infractionName,
                "summary", summary
        ));
    }

    public String buildCompletionSubject(
            String schoolName,
            String studentFirstName,
            String studentLastName,
            String targetLanguageCode,
            boolean keepSchoolInSubject
    ) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template;
        if (keepSchoolInSubject) {
            template = "es".equals(language) ? SUBJECT_COMPLETION_WITH_SCHOOL_ES : SUBJECT_COMPLETION_WITH_SCHOOL_EN;
        } else {
            template = "es".equals(language) ? SUBJECT_COMPLETION_ES : SUBJECT_COMPLETION_EN;
        }

        return fillSubject(template, java.util.Map.of(
                "schoolName", schoolName == null ? "" : schoolName,
                "studentFullName", studentFullName
        ));
    }

    public String buildCompletionMessage(
            String studentFirstName,
            String studentLastName,
            String infractionName,
            String teacherEmail,
            String targetLanguageCode
    ) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template = "es".equals(language) ? COMPLETION_MSG_ES : COMPLETION_MSG_EN;

        return fillTemplate(template, java.util.Map.of(
                "studentFullName", studentFullName,
                "infractionName", infractionName == null ? "" : infractionName,
                "teacherEmail", teacherEmail == null ? "" : teacherEmail
        ));
    }

    public String buildLevelThreeRejectSubject(
            String studentFirstName,
            String studentLastName,
            String targetLanguageCode
    ) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template = "es".equals(language) ? SUBJECT_L3_REJECT_ES : SUBJECT_L3_REJECT_EN;

        return fillSubject(template, java.util.Map.of(
                "studentFullName", studentFullName
        ));
    }

    public String buildLevelThreeRejectMessage(String feedbackRaw, String targetLanguageCode) {
        String language = normalizeLanguage(targetLanguageCode);
        String feedbackTranslated = translateUserInput(feedbackRaw, language);
        String template = "es".equals(language) ? L3_REJECT_MSG_ES : L3_REJECT_MSG_EN;

        return fillTemplate(template, java.util.Map.of(
                "feedback", feedbackTranslated == null ? "" : feedbackTranslated
        ));
    }

    public String buildPunishmentDeletedSubject(
            String schoolName,
            String studentFirstName,
            String studentLastName,
            String targetLanguageCode
    ) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template = "es".equals(language)
                ? SUBJECT_PUNISHMENT_DELETED_ES
                : SUBJECT_PUNISHMENT_DELETED_EN;

        return fillSubject(template, java.util.Map.of(
                "schoolName", schoolName == null ? "" : schoolName,
                "studentFullName", studentFullName
        ));
    }

    public String buildPunishmentDeletedMessage(
            String studentFirstName,
            String studentLastName,
            String infractionName,
            String infractionLevel,
            String explanationUserInput,
            String targetLanguageCode
    ) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;
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

    public String buildPunishmentRestoredSubject(
            String schoolName,
            String studentFirstName,
            String studentLastName,
            String targetLanguageCode
    ) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template = "es".equals(language)
                ? SUBJECT_PUNISHMENT_RESTORED_ES
                : SUBJECT_PUNISHMENT_RESTORED_EN;

        return fillSubject(template, java.util.Map.of(
                "schoolName", schoolName == null ? "" : schoolName,
                "studentFullName", studentFullName
        ));
    }

    public String buildPunishmentRestoredMessage(
            String studentFirstName,
            String studentLastName,
            String targetLanguageCode
    ) {
        String language = normalizeLanguage(targetLanguageCode);
        String studentFullName = studentFirstName + " " + studentLastName;

        String template = "es".equals(language)
                ? PUNISHMENT_RESTORED_MSG_ES
                : PUNISHMENT_RESTORED_MSG_EN;

        return fillTemplate(template, java.util.Map.of(
                "studentFullName", studentFullName
        ));
    }

    public boolean positive(String name) {
        return com.reps.demogcloud.models.enums.InfractionType.POSITIVE.getLabel().equalsIgnoreCase(name);
    }
    public String buildManagedSubject(String kind,java.util.Map<String,String> content,String lang) {
        boolean es="es".equals(normalizeLanguage(lang));
        if(positive(content.get("infractionName")) || "SO".equals(content.get("status")))
            return es ? "Reconocimiento positivo" : "Positive recognition";
        if("office".equals(kind)) return es ? "Actualización de referencia a la dirección" : "Office referral update";
        if("guidance".equals(kind) || com.reps.demogcloud.models.enums.InfractionType.isSpecialCase(content.get("infractionName")))
            return es ? "Actualización escolar" : "School update";
        return es ? "Actualización de referencia escolar" : "School referral update";
    }
    public String buildPositiveMessage(String first,String last,String description,String lang) {
        boolean es="es".equals(normalizeLanguage(lang));
        return fillTemplate(es ? POSITIVE_ES : POSITIVE_EN,java.util.Map.of(
                "studentFullName",first+" "+last,"description",translateUserInput(description,lang)));
    }
    public String buildCommunicationSubject(String kind,java.util.Map<String,String> content,String school,String first,String last,String lang) {
        boolean es="es".equals(normalizeLanguage(lang));
        String name=content.getOrDefault("infractionName","");
        if(positive(name) || "SO".equals(content.get("status")))
            return fillSubject(es ? "{{schoolName}} Reconocimiento positivo para {{studentFullName}}" : "{{schoolName}} Positive recognition for {{studentFullName}}",
                    java.util.Map.of("schoolName",school,"studentFullName",first+" "+last));
        if("guidance".equals(kind) || "Academic Concern".equalsIgnoreCase(name) || "Behavioral Concern".equalsIgnoreCase(name))
            return fillSubject(es ? "{{schoolName}} Actualización escolar para {{studentFullName}}" : "{{schoolName}} School update for {{studentFullName}}",
                    java.util.Map.of("schoolName",school,"studentFullName",first+" "+last));
        return buildSubject(school,first,last,lang,"office".equals(kind));
    }
    public String buildCaseMessage(String kind,String event,java.util.Map<String,String> content,
                                   String first,String last,String contact,String lang) {
        boolean es="es".equals(normalizeLanguage(lang));
        String name=content.getOrDefault("infractionName","");
        var fields=new java.util.HashMap<String,String>();fields.put("studentFullName",first+" "+last);
        fields.put("infractionName",name);fields.put("teacherEmail",contact);
        if(event.equals("CREATED")) {
            String description=content.get("description");
            if(positive(name) || "SO".equals(content.get("status"))) return buildPositiveMessage(first,last,description,lang);
            fields.put("description",translateUserInput(description,lang));
            if("office".equals(kind)) return fillTemplate(es ? OFFICE_CREATED_ES : OFFICE_CREATED_EN,fields);
            if("guidance".equals(kind) || com.reps.demogcloud.models.enums.InfractionType.isSpecialCase(name))
                return fillTemplate(es ? SCHOOL_UPDATE_ES : SCHOOL_UPDATE_EN,fields);
            if("CFR".equals(content.get("status"))) {
                // No new assignment is promised for a repeated offense.
                return fillTemplate(es ? CFR_ES : CFR_EN,fields);
            }
            fields.put("infractionLevel",content.get("infractionLevel"));
            String body=fillTemplate(es ? REFERRAL_CREATED_ES : REFERRAL_CREATED_EN,fields);
            if("UNAVAILABLE".equals(content.get("assignmentState")))
                return body+(es ? "<p>La tarea aún no está disponible. La escuela debe revisar su asignación; comuníquese con la escuela.</p>" : "<p>The assignment is not yet available. School staff must review its attachment; please contact the school.</p>");
            return body+(es ? "<p>Revise REPS para consultar el trabajo asignado y las instrucciones de la escuela.</p>" : "<p>Check REPS for assigned work and the school's instructions.</p>");
        }
        if(event.equals("SUBMITTED")) return fillTemplate(es ? SUBMITTED_ES : SUBMITTED_EN,fields);
        if(event.equals("REJECTED")) return buildLevelThreeRejectMessage(content.get("feedback"),lang);
        if(event.equals("ARCHIVED")) {
            fields.put("explanation",translateUserInput(content.get("archivedExplanation"),lang));
            return fillTemplate(es ? "<p>El registro escolar de {{studentFullName}} fue archivado.</p><p>{{explanation}}</p>" : "<p>The school record for {{studentFullName}} was archived.</p><p>{{explanation}}</p>",fields);
        }
        if(event.equals("RESTORED")) return fillTemplate(es ? "<p>El registro escolar de {{studentFullName}} fue restaurado. Consulte su estado actual en {{loginUrl}}.</p>" : "<p>The school record for {{studentFullName}} was restored. Review its current status at {{loginUrl}}.</p>",fields);
        if(event.equals("CLOSED") || event.equals("COMPLETED")) {
            if(!"punishment".equals(kind) || com.reps.demogcloud.models.enums.InfractionType.isSpecialCase(name)) {
                String body=fillTemplate(es ? "<p>La escuela cerró el registro de {{studentFullName}}. Comuníquese con la escuela para obtener detalles; este aviso no confirma la finalización de una tarea.</p>" : "<p>The school closed the record for {{studentFullName}}. Contact the school for details; this notice does not confirm assignment completion.</p>",fields);
                if(content.get("closureComment")!=null && !content.get("closureComment").isBlank())
                    body+=fillTemplate("<p>{{comment}}</p>",java.util.Map.of("comment",translateUserInput(content.get("closureComment"),lang)));
                return body;
            }
            return buildCompletionMessage(first,last,name,contact,lang);
        }
        if(java.util.Set.of("REMINDER_ISS","REMINDER_DETENTION").contains(event))
            return fillTemplate(es ? REMINDER_ES : REMINDER_EN,fields);
        throw new IllegalArgumentException("Unsupported communication event");
    }
}
