package com.reps.demogcloud.services.email;

import com.reps.demogcloud.models.email.EmailTemplates;
import com.reps.demogcloud.services.translation.TranslationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Modifier;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EmailCommunicationContentTest {
    TranslationService translations=mock(TranslationService.class);
    EmailTemplateBuilderService builder=new EmailTemplateBuilderService(translations);
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(builder,"accountBaseUrl","https://repsdev.vercel.app");
        when(translations.translate(anyString(),eq("en"),eq("es"))).thenAnswer(i->"Traducción: "+i.getArgument(0));
    }
    Map<String,String> content() {
        return new HashMap<>(Map.of("infractionName","Horseplay","infractionLevel","1","description","Helped <another> & kept {{description}} literal",
                "feedback","Explain <why>","archivedExplanation","Entered in error <script>","status","OPEN","assignmentState","ASSIGNED"));
    }
    @ParameterizedTest @CsvSource({"en","es","ES","es-MX","es_US"})
    void everyCaseEventRendersWithoutTokensOrHtmlInjection(String lang) {
        for(String kind:List.of("punishment","office","guidance")) for(String event:List.of("CREATED","SUBMITTED","CLOSED","COMPLETED","REJECTED","ARCHIVED","RESTORED","REMINDER_ISS","REMINDER_DETENTION")) {
            String result=builder.buildCaseMessage(kind,event,content(),"Ana","López","teacher@example.test",lang);
            assertFalse(result.contains("{{"),kind+event);assertFalse(result.contains("<another>"));assertFalse(result.contains("<script>"));
            EmailContent.requireRendered(builder.buildManagedSubject(kind,content(),lang),result);
            assertFalse(result.contains("repsdiscipline.vercel.app"));
        }
    }
    @ParameterizedTest @ValueSource(strings={"en","es"})
    void recognitionNeverUsesDisciplinaryCopyAndPreservesBehavior(String lang) {
        var c=content();c.put("infractionName","Positive Behavior Shout Out!");c.put("status","SO");
        String body=builder.buildCaseMessage("punishment","CREATED",c,"Ana","López","teacher@example.test",lang);
        String subject=builder.buildManagedSubject("punishment",c,lang);
        for(String word:List.of("punishment","infraction","assignment","disciplin","infracción","tarea")) assertFalse((body+subject).toLowerCase(Locale.ROOT).contains(word));
        assertTrue(body.contains("Helped &lt;another&gt; &amp; kept &#123;&#123;description&#125;&#125; literal"));
        assertTrue(subject.contains(lang.equals("es")?"Reconocimiento":"recognition"));
        assertFalse(subject.contains("Ana"));
    }
    @ParameterizedTest @ValueSource(strings={"en","es"})
    void repeatedReferralRendersItsActualDescription(String lang) {
        String body=builder.createCFRMessage("Ana","López","Horseplay","Actual <behavior>","teacher@example.test","student@example.test",lang);
        assertFalse(body.contains("{{"));assertTrue(body.contains("Actual &lt;behavior&gt;"));
    }
    @ParameterizedTest @CsvSource({"http://localhost:3000,http://localhost:3000/login","https://repsdev.vercel.app/,https://repsdev.vercel.app/login","https://school.example.test,https://school.example.test/login"})
    void linksUseOnlyConfiguredOrigin(String origin,String expected) {
        ReflectionTestUtils.setField(builder,"accountBaseUrl",origin);
        assertTrue(builder.buildPunishmentRestoredMessage("Ana","López","es").contains(expected));
    }
    @ParameterizedTest @ValueSource(strings={"","http://remote.example.test","https://user@example.test","https://school.example.test/path","https://school.example.test/?next=other"})
    void unsafeOrMissingLinksAreNotSilentlyReplaced(String origin) {
        ReflectionTestUtils.setField(builder,"accountBaseUrl",origin);
        assertThrows(IllegalStateException.class,()->builder.buildPunishmentRestoredMessage("Ana","López","en"));
    }
    @Test void productionCannotLinkToLocalHttp() {
        ReflectionTestUtils.setField(builder,"accountBaseUrl","http://localhost:3000");ReflectionTestUtils.setField(builder,"activeProfiles","prod");
        assertThrows(IllegalStateException.class,()->builder.buildPunishmentRestoredMessage("Ana","López","en"));
    }
    @Test void missingRequiredBehaviorAndUnknownTokensFailBeforeSend() {
        var c=content();c.remove("description");
        assertThrows(IllegalArgumentException.class,()->builder.buildCaseMessage("punishment","CREATED",c,"Ana","López","teacher@example.test","en"));
        assertThrows(IllegalArgumentException.class,()->EmailContent.render("{{unknown}}",Map.of(),true));
        assertThrows(IllegalArgumentException.class,()->EmailContent.requireRendered("Subject","{{description}}"));
        assertThrows(IllegalArgumentException.class,()->EmailContent.requireRendered("Injected\r\nBcc: other","Body"));
    }
    @Test void userTokensAreNotReinterpretedAsTemplateVariables() {
        assertEquals("&#123;&#123;second&#125;&#125;",EmailContent.render("{{first}}",Map.of("first","{{second}}","second","must not substitute"),true));
    }
    @Test void translationFailurePreservesOriginalDescriptionWithoutExposingProviderError() {
        when(translations.translate(anyString(),eq("en"),eq("es"))).thenThrow(new IllegalStateException("synthetic private provider payload"));
        String body=builder.buildPositiveMessage("Ana","López","Helped a classmate","es");
        assertTrue(body.contains("Reconocimiento"));assertTrue(body.contains("Helped a classmate"));assertFalse(body.contains("provider payload"));
    }
    @Test void unavailableAssignmentDoesNotPromiseAnAssignmentOrCompletion() {
        var c=content();c.put("assignmentState","UNAVAILABLE");
        assertTrue(builder.buildCaseMessage("punishment","CREATED",c,"Ana","López","teacher@example.test","en").contains("not yet available"));
        String office=builder.buildCaseMessage("office","CLOSED",c,"Ana","López","admin@example.test","en");
        assertTrue(office.contains("does not confirm assignment completion"));
        assertTrue(builder.buildCaseMessage("punishment","SUBMITTED",c,"Ana","López","teacher@example.test","es").contains("pendiente de revisión"));
    }
    @Test void everyStaticTemplateHasAnExplicitCompleteVariableSet() throws Exception {
        var fields=Map.ofEntries(Map.entry("studentFullName","Ana López"),Map.entry("infractionLevel","1"),Map.entry("infractionName","Horseplay"),
                Map.entry("description","Actual behavior"),Map.entry("loginUrl","https://repsdev.vercel.app/login"),Map.entry("studentEmail","student@example.test"),
                Map.entry("teacherEmail","teacher@example.test"),Map.entry("schoolName","School"),Map.entry("feedback","Review feedback"),Map.entry("summary","History"),Map.entry("explanation","Correction"));
        for(var field:EmailTemplates.class.getFields()) if(Modifier.isStatic(field.getModifiers()) && field.getType()==String.class) {
            String rendered=EmailContent.render((String)field.get(null),fields,true);
            assertFalse(rendered.contains("{{"),field.getName());assertFalse(rendered.isBlank(),field.getName());
        }
    }
    @ParameterizedTest @ValueSource(strings={"Positive Behavior Shout Out!","Academic Concern","Behavioral Concern"})
    void legacySpecialCommunicationsDoNotRequireADisciplinaryLevel(String name) throws Exception {
        var referrals=new EmailReferralService(builder,mock(EmailNotificationService.class),mock(com.reps.demogcloud.data.PunishRepository.class),
                mock(com.reps.demogcloud.data.StudentRepository.class),mock(com.reps.demogcloud.data.InfractionRepository.class),mock(com.reps.demogcloud.data.SchoolRepository.class));
        var student=new com.reps.demogcloud.models.student.Student();student.setFirstName("Ana");student.setLastName("López");student.setSchool("School");student.setPreferredLanguage("es");student.setStudentEmail("student@example.test");
        var infraction=new com.reps.demogcloud.models.infraction.Infraction();infraction.setInfractionName(name);
        var p=new com.reps.demogcloud.models.punishment.Punishment();p.setTeacherEmail("teacher@example.test");p.setInfractionDescription(List.of("Actual <behavior>"));
        var response=new com.reps.demogcloud.models.punishment.PunishmentResponse();response.setParentToEmail("guardian@example.test");response.setTeacherToEmail("teacher@example.test");response.setStudentToEmail("student@example.test");
        var mail=mock(com.reps.demogcloud.services.EmailService.class);
        referrals.sendInfractionNotification(p,mail,student,infraction,response);
        assertFalse(response.getMessage().contains("{{"));assertTrue(response.getMessage().contains("Actual &lt;behavior&gt;"));
        assertFalse(response.getMessage().toLowerCase(Locale.ROOT).contains("assignment"));
        verify(mail).sendPtsEmail(eq("guardian@example.test"),eq("teacher@example.test"),eq("student@example.test"),eq(response.getMessage()),eq(response.getSubject()),eq("es"));
    }
}
