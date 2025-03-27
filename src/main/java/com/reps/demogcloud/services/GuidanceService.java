package com.reps.demogcloud.services;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.data.filters.CustomFilters;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.guidance.GuidanceReferral;
import com.reps.demogcloud.models.guidance.GuidanceRequest;
import com.reps.demogcloud.models.guidance.GuidanceResponse;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GuidanceService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final StudentRepository studentRepository;
    private final InfractionRepository infractionRepository;
    private final PunishRepository punishRepository;
    private final SchoolRepository schoolRepository;
    private final EmailService emailService;
    private final CustomFilters customFilters;
    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final StudentService studentService;
    private final GuidanceRepository guidanceRepository;
    private final OfficeReferralService officeReferralService;
    @Value("${sm://RepsDiscipline-twilio_username}")
    private String twilioUsername;
    @Value("${sm://RepsDiscipline-twilio_password}")
    private String twilioPassword;
    @Autowired
    private MongoTemplate mongoTemplate;

    public List<GuidanceReferral> findAll() {
        return guidanceRepository.findAll();
    }

    public List<GuidanceReferral> findByStatus(String status) {
        return guidanceRepository.findAllByStatus(status);
    }

    public void LinkAssignmentToGuidance(Student student, PunishmentFormRequest formRequest, Punishment punishment) {
        GuidanceRequest guidanceRequest = new GuidanceRequest();
        GuidanceReferral guidance = new GuidanceReferral();
        guidance.setGuidanceEmail(student.getGuidanceEmail());
        guidance.setStudentEmail(student.getStudentEmail());
        guidance.setLinkToPunishment(punishment.getPunishmentId());
        guidance.setInfractionName(punishment.getInfractionName());
        guidanceRequest.setGuidance(guidance);

        createNewGuidanceForm(guidanceRequest, formRequest);


    }

    public void createNewGuidanceForm(GuidanceRequest request, PunishmentFormRequest punishmentRequest) {
//        Twilio.init(secretClient.getSecret("TWILIO-ACCOUNT-SID").toString(), secretClient.getSecret("TWILIO-AUTH-TOKEN").toString());
        LocalDate now = LocalDate.now();

        Student studentRecord = studentRepository.findByStudentEmailIgnoreCase(punishmentRequest.getStudentEmail());
        School ourSchool = schoolRepository.findSchoolBySchoolName(studentRecord.getSchool());

        GuidanceReferral guidanceObj = request.getGuidance();
        guidanceObj.setStudentEmail(studentRecord.getStudentEmail());
        guidanceObj.setGuidanceId(UUID.randomUUID().toString());
        guidanceObj.setTimeCreated(now);
        guidanceObj.setTeacherEmail(punishmentRequest.getTeacherEmail());
        ArrayList<String> description = new ArrayList<>();
        description.add(punishmentRequest.getGuidanceDescription());
        guidanceObj.setReferralDescription(description);
        guidanceObj.setSchoolName(ourSchool.getSchoolName());
        guidanceObj.setStatus("OPEN");
        guidanceObj.setGuidanceEmail(studentRecord.getGuidanceEmail());
        guidanceObj.setClassPeriod(request.getGuidance().getClassPeriod());
        guidanceRepository.save(guidanceObj);

        GuidanceResponse response = new GuidanceResponse();
        ArrayList<GuidanceReferral> guidances = new ArrayList<>();
        guidances.add(guidanceObj);
        response.setGuidance(guidances);
        response.setMessage("Successfully Created Guidance Referral");


    }

    public GuidanceResponse createNewGuidanceFormSimple(GuidanceRequest request) {
//        Twilio.init(secretClient.getSecret("TWILIO-ACCOUNT-SID").toString(), secretClient.getSecret("TWILIO-AUTH-TOKEN").toString());
        LocalDate now = LocalDate.now();

        Student studentRecord = studentRepository.findByStudentEmailIgnoreCase(request.getGuidance().getStudentEmail());
        School ourSchool = schoolRepository.findSchoolBySchoolName(studentRecord.getSchool());

        GuidanceReferral guidanceObj = request.getGuidance();
        guidanceObj.setStudentEmail(studentRecord.getStudentEmail());
        guidanceObj.setGuidanceId(UUID.randomUUID().toString());
        guidanceObj.setTimeCreated(now);
        guidanceObj.setTeacherEmail(request.getGuidance().getTeacherEmail());
        guidanceObj.setReferralDescription(request.getGuidance().getReferralDescription());
        guidanceObj.setSchoolName(ourSchool.getSchoolName());
        guidanceObj.setStatus("OPEN");
        guidanceObj.setGuidanceEmail(studentRecord.getGuidanceEmail());
        guidanceObj.setClassPeriod(request.getGuidance().getClassPeriod());
        guidanceRepository.save(guidanceObj);

        GuidanceResponse response = new GuidanceResponse();
        ArrayList<GuidanceReferral> guidances = new ArrayList<>();
        guidances.add(guidanceObj);
        response.setGuidance(guidances);
        response.setMessage("Successfully Created Guidance Referral");
        return response;


    }

    public GuidanceReferral updateGuidanceStatus(String id, String newStatus) {
        Optional<GuidanceReferral> getReferral = guidanceRepository.findById(id);

        if (getReferral.isEmpty()) {
            GuidanceResponse response = new GuidanceResponse();
            response.setError("Guidance Referral with id " + " was not found");
            return null;
        }
        GuidanceReferral record = getReferral.get();
        record.setStatus(newStatus);
        List<ThreadEvent> events = record.getNotesArray() == null ? new ArrayList<>() : record.getNotesArray();

        LocalDate timePosted = LocalDate.now();
        ThreadEvent newEvent = new ThreadEvent();
        newEvent.setEvent("Status");
        newEvent.setDate(timePosted);
        newEvent.setContent("The Status of This Task was Changed to " + newStatus);
        events.add(newEvent);

        return guidanceRepository.save(record);
    }

    public GuidanceReferral updateGuidanceFollowUp(String id, LocalDate scheduleFollowUp, String statusChange) {
        Optional<GuidanceReferral> record = guidanceRepository.findById(id);
        if (record.isEmpty()) {
            GuidanceResponse response = new GuidanceResponse();
            response.setError("No Guidance with id " + id + " was found");
            return null;
        }

        LocalDate timePosted = LocalDate.now();
        GuidanceReferral guidance = record.get();

        try {
            guidance.setFollowUpDate(scheduleFollowUp);
        } catch (DateTimeParseException e) {
            System.out.println("Invalid date format: " + e.getMessage());
        }

        guidance.setStatus(statusChange);


        List<ThreadEvent> events = guidance.getNotesArray() == null ? new ArrayList<>() : guidance.getNotesArray();

        ThreadEvent newEvent = new ThreadEvent();
        newEvent.setEvent("Follow Up");
        newEvent.setDate(timePosted);
        newEvent.setContent("Follow up for this task has been set for " + guidance.getFollowUpDate().toString());
        events.add(newEvent);
        guidance.setNotesArray(events);
        guidance.setStatus(statusChange);

        return guidanceRepository.save(guidance);
    }

    public GuidanceResponse sendResourcesAndMakeNotes(String id, ResourceUpdateRequest request) throws MessagingException {
        Optional<GuidanceReferral> guidanceOptional = guidanceRepository.findById(id);
        if (guidanceOptional.isEmpty()) {
            GuidanceResponse response = new GuidanceResponse();
            response.setMessage("No Guidance Found by Id: " + id);
            return response;
        }


        GuidanceReferral guidance = guidanceOptional.get();
        LocalDate timePosted = LocalDate.now();
        List<ThreadEvent> events = guidance.getNotesArray() == null ? new ArrayList<>() : guidance.getNotesArray();

        ThreadEvent newEvent = new ThreadEvent();
        newEvent.setEvent("Resources");
        newEvent.setDate(timePosted);

        // Extract URLs from ResourceOption list
        List<String> labels = request.getResourceOptionList().stream()
                .map(ResourceOption::getLabel)
                .toList();

        // Set the content of the new event
        newEvent.setContent("Resources Sent: " + String.join(", ", labels));
        events.add(newEvent);

        guidance.setNotesArray(events);

        Student student = studentRepository.findByStudentEmailIgnoreCase(guidance.getStudentEmail());

        // Construct the resource message in HTML format
        StringBuilder resourceMessage = new StringBuilder();
        resourceMessage.append("<html>")
                .append("<body>")
                .append("<p>Hello,<br>")
                .append(student.getFirstName())
                .append(" ")
                .append(student.getLastName())
                .append(", Guidance has sent you the following resources for your consideration:</p>")
                .append("<ul>");

        for (ResourceOption item : request.getResourceOptionList()) {
            resourceMessage.append("<li><a href=\"")
                    .append(item.getUrl())
                    .append("\">")
                    .append(item.getLabel())
                    .append("</a></li>");
        }

        resourceMessage.append("</ul>")
                .append("</body>")
                .append("</html>");

        String finalMessage = resourceMessage.toString();

        String subject = student.getSchool() + " High School Guidance's Resources";
        ArrayList<String> ccList = new ArrayList<>();
        ccList.add(student.getGuidanceEmail());
        ccList.add(guidance.getTeacherEmail());


        emailService.sendEmailGeneric(
                ccList,
                student.getStudentEmail(),
                subject,
                finalMessage// Use HTML message
        );

        guidanceRepository.save(guidance);
        GuidanceResponse response = new GuidanceResponse();
        List<GuidanceReferral> listOfGuidance = new ArrayList<>();
        listOfGuidance.add(guidance);
        response.setGuidance(listOfGuidance);
        return response;
    }

    public List<GuidanceReferral> getLoggedInUserGuidanceReferrals(List<GuidanceReferral> guidanceList, String loggedInGuidanceEmail) {
        //Extrat Student Email from Guidance List
        List<String> studentEmails = guidanceList.stream()
                .map(GuidanceReferral::getStudentEmail)
                .collect(Collectors.toList());


        // Build the aggregation pipeline to match students by the extracted student emails
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("studentEmail").in(studentEmails)),
                Aggregation.project("studentEmail", "guidanceEmail")
        );

        // Execute the aggregation query and map the results to a Map
        AggregationResults<Map> results =
                mongoTemplate.aggregate(aggregation, "students", Map.class);

        // Extract the student emails associated with the specified guidance email
        List<String> studentEmailsForGuidance = results.getMappedResults().stream()
                .filter(result -> loggedInGuidanceEmail.equals(result.get("guidanceEmail")))
                .map(result -> (String) result.get("studentEmail"))
                .toList();

        // Filter punishments based on the extracted student emails
        return guidanceList.stream()
                .filter(g -> studentEmailsForGuidance.contains(g.getStudentEmail()))
                .collect(Collectors.toList());
    }

    public GuidanceReferral updateGuidance(String id, ThreadEvent event) {
        Optional<GuidanceReferral> guidance = guidanceRepository.findById(id);

        if (guidance.isEmpty()) {
            GuidanceResponse response = new GuidanceResponse();
            response.setError("No Guidance with id " + id + " was found");
            return null;

        }

        GuidanceReferral record = guidance.get();
        LocalDate timePosted = LocalDate.now();

        List<ThreadEvent> events = record.getNotesArray() == null ? new ArrayList<>() : record.getNotesArray();

        ThreadEvent newEvent = new ThreadEvent();
        newEvent.setCreatedBy(event.getCreatedBy());
        newEvent.setEvent(event.getEvent());
        newEvent.setDate(timePosted);
        newEvent.setContent(event.getContent());
        events.add(newEvent);
//
        record.setNotesArray(events);

        return guidanceRepository.save(record);


    }

    public List<GuidanceReferral> getAllGuidanceReferrals(String status, boolean filterByLoggedIn) {
        List<GuidanceReferral> allReferrals = guidanceRepository.findAllByStatus(status);
        if (filterByLoggedIn) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication != null && authentication.getPrincipal() != null
                    ? authentication.getName()
                    : "";

            return getLoggedInUserGuidanceReferrals(allReferrals, username);
        }

        return allReferrals;
    }

    public String deleteGuidanceReferral(String id) throws ResourceNotFoundException {
        try {
            guidanceRepository.deleteById(id);

        } catch (Exception e) {
            throw new ResourceNotFoundException("That infraction does not exist");
        }
        return "Punishment has been deleted";
    }
}
