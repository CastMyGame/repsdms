package com.reps.demogcloud.services;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.guidance.GuidanceReferral;
import com.reps.demogcloud.models.guidance.GuidanceRequest;
import com.reps.demogcloud.models.guidance.GuidanceResponse;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GuidanceService {

    private final com.reps.demogcloud.services.email.RequiredNotificationService requiredNotifications;
    private final StudentRepository studentRepository;
    private final SchoolRepository schoolRepository;
    private final EmailService emailService;
    private final GuidanceRepository guidanceRepository;
    private final MongoTemplate mongoTemplate;
    private final WorkflowAccessService access;
    private final UserContextService users;
    //@Value("${sm://RepsDiscipline-twilio_username}")
    private String twilioUsername;
   // @Value("${sm://RepsDiscipline-twilio_password}")
    private String twilioPassword;

    private com.reps.demogcloud.services.tenancy.CanonicalDomainAccess canonical;
    @Autowired(required=false) public void setCanonicalDomainAccess(com.reps.demogcloud.services.tenancy.CanonicalDomainAccess value){canonical=value;}
    private String staffEmail(){return canonical==null?users.getCurrentStaffEmail():canonical.email(true);}

    public List<GuidanceReferral> findAll() {
        if(canonical!=null)return canonical.guidanceList(null);
        access.requireStaff();
        return visible(guidanceRepository.findBySchoolIgnoreCase(users.getCurrentUserSchool()));
    }

    public List<GuidanceReferral> findByStatus(String status) {
        if(canonical!=null){validateStatus(status);return canonical.guidanceList(status);}
        access.requireStaff();
        validateStatus(status);
        return visible(guidanceRepository.findBySchoolIgnoreCaseAndStatus(users.getCurrentUserSchool(),status));
    }

    public void LinkAssignmentToGuidance(Student student, PunishmentFormRequest formRequest, Punishment punishment) {
        GuidanceRequest guidanceRequest = new GuidanceRequest();
        GuidanceReferral guidance = new GuidanceReferral();
        guidance.setGuidanceEmail(student.getGuidanceEmail());
        guidance.setStudentEmail(student.getStudentEmail());
        guidance.setLinkToPunishment(punishment.getPunishmentId());
        guidance.setInfractionName(punishment.getInfractionName());
        guidance.setClassPeriod(punishment.getClassPeriod());
        guidanceRequest.setGuidance(guidance);

        createNewGuidanceForm(guidanceRequest, formRequest);


    }

    public void createNewGuidanceForm(GuidanceRequest request, PunishmentFormRequest punishmentRequest) {
        if(canonical==null)access.requireStaff();else canonical.email(true);
        if(request==null || request.getGuidance()==null || punishmentRequest==null) throw new IllegalArgumentException("Guidance request required");
        if(!Objects.equals(request.getGuidance().getStudentEmail(),punishmentRequest.getStudentEmail())) denied();
        request.getGuidance().setReferralDescription(List.of(Objects.toString(punishmentRequest.getGuidanceDescription(),"")));
        createNewGuidanceFormSimple(request);
    }

    public GuidanceResponse createNewGuidanceFormSimple(GuidanceRequest request) {
        if(request==null || request.getGuidance()==null) throw new IllegalArgumentException("Guidance request required");
        GuidanceReferral input=request.getGuidance();
        Student student;String actor;boolean studentActor;
        if(canonical!=null){
            student=canonical.student(input.getStudentEmail(),false);actor=canonical.email(false);studentActor=canonical.actor().roles().contains("STUDENT");
            if(input.getSchool()!=null || input.getTenantSchoolId()!=null || input.getTeacherEmail()!=null && !actor.equalsIgnoreCase(input.getTeacherEmail()))denied();
            canonical.prepareGuidance(student);
        }else{
            access.requireStudentAccess(input.getStudentEmail());student=studentRepository.findByStudentEmailIgnoreCase(input.getStudentEmail());
            if(student==null || student.isArchived() || !access.sameSchool(student.getSchool()))denied();
            actor=users.getCurrentUsername();studentActor=access.isStudentActor();if(studentActor)access.requireStudent();else access.requireStaff();
            if(input.getSchool()!=null && !input.getSchool().isBlank() && !input.getSchool().equalsIgnoreCase(student.getSchool())
                    || input.getTeacherEmail()!=null && !input.getTeacherEmail().isBlank() && !input.getTeacherEmail().equalsIgnoreCase(actor))denied();
        }
        if(input.getReferralDescription()==null || input.getReferralDescription().isEmpty() || input.getReferralDescription().size()>20
                || input.getReferralDescription().stream().anyMatch(v -> v==null || v.isBlank() || v.length()>10000))
            throw new IllegalArgumentException("A guidance description is required");
        GuidanceReferral record=new GuidanceReferral();
        record.setGuidanceId(UUID.randomUUID().toString());record.setStudentEmail(student.getStudentEmail());record.setSchool(student.getSchool());
        record.setRequestedByEmail(actor);record.setTeacherEmail(studentActor?null:staffEmail());
        if(canonical!=null)record.setTenantSchoolId(canonical.actor().tenantSchoolId());
        record.setGuidanceEmail(student.getGuidanceEmail());record.setClassPeriod(input.getClassPeriod());
        record.setReferralDescription(new ArrayList<>(input.getReferralDescription()));record.setTimeCreated(LocalDate.now());record.setStatus("OPEN");
        if(input.getLinkToPunishment()!=null && !input.getLinkToPunishment().isBlank()) {
            if(canonical!=null){
                canonical.email(true);var linked=canonical.linkedPunishment(input.getLinkToPunishment());
                if(!student.getStudentEmail().equalsIgnoreCase(linked.getString("studentEmail")))denied();
                record.setLinkToPunishment(input.getLinkToPunishment());record.setInfractionName(linked.getString("infractionName"));
            }else{
            access.requireStaff();var punishment=access.requireCase(input.getLinkToPunishment());
            if(!student.getStudentEmail().equalsIgnoreCase(punishment.getStudentEmail()) || !student.getSchool().equalsIgnoreCase(punishment.getSchool())) denied();
            record.setLinkToPunishment(punishment.getPunishmentId());record.setInfractionName(punishment.getInfractionName());
            }
        }
        record=guidanceRepository.save(record);
        GuidanceResponse response=new GuidanceResponse();response.setGuidance(List.of(record));response.setMessage("Guidance request saved");return response;
    }

    private List<GuidanceReferral> visible(List<GuidanceReferral> records) {
        return records.stream().filter(users::canReadGuidance).toList();
    }
    private GuidanceReferral requireRecord(String id,boolean manage) {
        if(canonical!=null)return canonical.guidance(id,manage);
        access.requireStaff();
        GuidanceReferral record=guidanceRepository.findById(id).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Access denied"));
        users.requireGuidanceAccess(record,manage);
        Student student=studentRepository.findByStudentEmailIgnoreCase(record.getStudentEmail());
        if(record.isArchived() || student==null || student.isArchived()) denied();
        return record;
    }
    private void validateStatus(String status) {
        if(!Set.of("OPEN","DORMANT","CLOSED").contains(Objects.toString(status,""))) throw new IllegalArgumentException("Invalid guidance status");
    }
    private static void denied() { throw new org.springframework.security.access.AccessDeniedException("Access denied"); }

    public GuidanceReferral updateGuidanceStatus(String id, String newStatus) {
        GuidanceReferral record=requireRecord(id,true);
        validateStatus(newStatus);
        record.setTimeClosed("CLOSED".equals(newStatus)?LocalDate.now():null);
        record.setStatus(newStatus);
        List<ThreadEvent> events = record.getNotesArray() == null ? new ArrayList<>() : record.getNotesArray();

        LocalDate timePosted = LocalDate.now();
        ThreadEvent newEvent = new ThreadEvent();
        newEvent.setEvent("Status");
        newEvent.setDate(timePosted);
        newEvent.setCreatedBy(staffEmail());
        newEvent.setContent("The Status of This Task was Changed to " + newStatus);
        events.add(newEvent);
        record.setNotesArray(events);

        return guidanceRepository.save(record);
    }

    public GuidanceReferral updateGuidanceFollowUp(String id, LocalDate scheduleFollowUp, String statusChange) {
        GuidanceReferral guidance=requireRecord(id,true);
        validateStatus(statusChange);
        if(scheduleFollowUp==null) throw new IllegalArgumentException("Follow-up date required");
        LocalDate timePosted=LocalDate.now();
        guidance.setFollowUpDate(scheduleFollowUp);
        guidance.setTimeClosed("CLOSED".equals(statusChange)?LocalDate.now():null);

        guidance.setStatus(statusChange);


        List<ThreadEvent> events = guidance.getNotesArray() == null ? new ArrayList<>() : guidance.getNotesArray();

        ThreadEvent newEvent = new ThreadEvent();
        newEvent.setEvent("Follow Up");
        newEvent.setDate(timePosted);
        newEvent.setCreatedBy(staffEmail());
        newEvent.setContent("Follow up for this task has been set for " + guidance.getFollowUpDate().toString());
        events.add(newEvent);
        guidance.setNotesArray(events);
        guidance.setStatus(statusChange);

        return guidanceRepository.save(guidance);
    }

    public GuidanceResponse sendResourcesAndMakeNotes(String id, ResourceUpdateRequest request) throws MessagingException {
        if(canonical!=null)canonical.disconnected(); // Notification/grant ownership belongs to 4B-2.
        GuidanceReferral guidance=requireRecord(id,true);
        if(request==null) throw new IllegalArgumentException("Resource request required");
        if(requiredNotifications.isEnabled() && requiredNotifications.hasAction(guidance,"RESOURCES")) {
            GuidanceResponse prior = new GuidanceResponse(); prior.setGuidance(List.of(guidance)); return prior;
        }
        requiredNotifications.requireActionAccess(guidance,"RESOURCES");
        LocalDate timePosted = LocalDate.now();
        List<ThreadEvent> events = guidance.getNotesArray() == null ? new ArrayList<>() : guidance.getNotesArray();

        ThreadEvent newEvent = new ThreadEvent();
        newEvent.setEvent("Resources");
        newEvent.setDate(timePosted);
        newEvent.setCreatedBy(staffEmail());

        List<ResourceOption> options = request.getResourceOptionList() == null
                ? new ArrayList<>()
                : request.getResourceOptionList();

        if(options.isEmpty() || options.size()>50 || options.stream().anyMatch(o -> o==null || o.getLabel()==null || o.getLabel().isBlank()
                || o.getLabel().length()>500 || o.getUrl()==null || o.getUrl().length()>2000)) throw new IllegalArgumentException("Select valid resources");
        List<String> labels = options.stream()
                .map(ResourceOption::getLabel)
                .toList();

        newEvent.setContent("Resources requested: " + String.join(", ", labels));
        events.add(newEvent);

        guidance.setNotesArray(events);

        Student student = studentRepository.findByStudentEmailIgnoreCase(guidance.getStudentEmail());
        if (student == null) {
            throw new IllegalStateException("Student not found: " + guidance.getStudentEmail());
        }

        boolean spanish=student.getPreferredLanguage()!=null && student.getPreferredLanguage().trim().toLowerCase(Locale.ROOT).matches("es([-_].*)?");
        StringBuilder resourceMessage = new StringBuilder();
        resourceMessage.append("<html>")
                .append("<body>")
                .append(spanish ? "<p>Hola,<br>" : "<p>Hello,<br>")
                .append(org.springframework.web.util.HtmlUtils.htmlEscape(java.util.Objects.toString(student.getFirstName(),"")))
                .append(" ")
                .append(org.springframework.web.util.HtmlUtils.htmlEscape(java.util.Objects.toString(student.getLastName(),"")))
                .append(spanish ? ", Orientación comparte los siguientes recursos para su consideración:</p>" : ", Guidance shares the following resources for your consideration:</p>")
                .append("<ul>");

        for (ResourceOption item : options) {
            {
                java.net.URI uri=java.net.URI.create(item.getUrl());
                if(uri.getScheme()==null || !java.util.Set.of("https","http").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null)
                    throw new IllegalArgumentException("Resources must use HTTP or HTTPS links");
            }
            resourceMessage.append("<li><a href=\"")
                    .append(org.springframework.web.util.HtmlUtils.htmlEscape(item.getUrl()))
                    .append("\">")
                    .append(org.springframework.web.util.HtmlUtils.htmlEscape(item.getLabel()))
                    .append("</a></li>");
        }

        resourceMessage.append("</ul>")
                .append("</body>")
                .append("</html>");

        String subject = student.getSchool() + (spanish ? " Recursos de orientación" : " Guidance resources");
        ArrayList<String> ccList = new ArrayList<>();
        for(String email:Arrays.asList(student.getGuidanceEmail(),guidance.getTeacherEmail())) {
            if(email==null || email.isBlank())continue;
            try { users.requireRecordedGuidanceAccess(email,guidance);if(!ccList.contains(email))ccList.add(email); }
            catch(org.springframework.security.access.AccessDeniedException denied) { /* Revoked participants receive no new disclosure. */ }
        }

        if(requiredNotifications.isEnabled()) {
            requiredNotifications.prepare(guidance,"RESOURCES",requiredNotifications.actionId(),resourceMessage.toString());
        }
        // Persist the authorized history/intent before any legacy transport side effect.
        guidance=guidanceRepository.save(guidance);
        if(!requiredNotifications.isEnabled()) {
        emailService.sendEmailGeneric(
                ccList,
                student.getStudentEmail(),
                subject,
                resourceMessage.toString(),
                student.getPreferredLanguage()
        );

        }
        GuidanceResponse response = new GuidanceResponse();
        List<GuidanceReferral> listOfGuidance = new ArrayList<>();
        listOfGuidance.add(guidance);
        response.setGuidance(listOfGuidance);
        return response;
    }

    public List<GuidanceReferral> getLoggedInUserGuidanceReferrals(List<GuidanceReferral> guidanceList, String loggedInGuidanceEmail) {
        if(canonical!=null){
            if(!canonical.email(true).equalsIgnoreCase(loggedInGuidanceEmail))denied();
            return canonical.guidanceList(null).stream().filter(g->loggedInGuidanceEmail.equalsIgnoreCase(canonical.student(g.getStudentEmail(),true).getGuidanceEmail())).toList();
        }
        access.requireStaff();
        if(!users.getCurrentStaffEmail().equalsIgnoreCase(loggedInGuidanceEmail)) denied();
        return visible(guidanceList).stream().filter(g -> { var student=studentRepository.findByStudentEmailIgnoreCase(g.getStudentEmail()); return student!=null && loggedInGuidanceEmail.equalsIgnoreCase(student.getGuidanceEmail()); }).toList();
    }

    public GuidanceReferral updateGuidance(String id, ThreadEvent event) {
        GuidanceReferral record=requireRecord(id,false);
        if(event==null || event.getContent()==null || event.getContent().isBlank() || event.getContent().length()>10000
                || event.getEvent()==null || event.getEvent().length()>100) throw new IllegalArgumentException("Valid guidance note required");
        LocalDate timePosted = LocalDate.now();

        List<ThreadEvent> events = record.getNotesArray() == null ? new ArrayList<>() : record.getNotesArray();

        ThreadEvent newEvent = new ThreadEvent();
        newEvent.setEvent(event.getEvent());
        newEvent.setDate(timePosted);
        newEvent.setCreatedBy(staffEmail());
        newEvent.setContent(event.getContent());
        events.add(newEvent);
//
        record.setNotesArray(events);

        return guidanceRepository.save(record);


    }

    public List<GuidanceReferral> getAllGuidanceReferrals(String status, boolean filterByLoggedIn) {
        var records="ALL".equals(status)?findAll():findByStatus(status);
        return filterByLoggedIn?getLoggedInUserGuidanceReferrals(records,staffEmail()):records;
    }

    public String deleteGuidanceReferral(String id) throws ResourceNotFoundException {
        GuidanceReferral record=requireRecord(id,true);
        record.setArchived(true);record.setArchivedOn(LocalDate.now());record.setArchivedBy(staffEmail());
        record.setStatus("CLOSED");record.setTimeClosed(LocalDate.now());
        guidanceRepository.save(record);
        return "Guidance referral archived; history retained";
    }
}
