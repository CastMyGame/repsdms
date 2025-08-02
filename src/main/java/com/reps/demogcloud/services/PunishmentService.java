package com.reps.demogcloud.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.punishment.PunishmentClosureService;
import com.reps.demogcloud.services.punishment.PunishmentCreationService;
import com.reps.demogcloud.services.punishment.PunishmentQueryService;
import com.reps.demogcloud.services.punishment.PunishmentUpdateService;

import com.reps.demogcloud.utils.StudentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.mail.MessagingException;

@Service
@Slf4j
@RequiredArgsConstructor
public class PunishmentService {

    private final PunishmentCreationService punishmentCreationService;
    private final PunishmentClosureService punishmentClosureService;
    private final PunishmentQueryService punishmentQueryService;
    private final PunishmentUpdateService punishmentUpdateService;
    private final StudentRepository studentRepository;
    private final PunishRepository punishRepository;
    private final EmailService emailService;
    private final EmployeeRepository employeeRepository;
    private final StudentUtils studentUtils;


    // -----------------------------------------FIND BY METHODS-----------------------------------------
    public List<Punishment> findByStudentEmailAndInfraction(String email, String infractionId) throws ResourceNotFoundException {
        return punishmentQueryService.findByStudentEmailAndInfraction(email, infractionId);
    }

    public List<Punishment> findByStatus(String status) throws ResourceNotFoundException {
        return punishmentQueryService.findByStatus(status);
    }

    public Punishment findByPunishmentId(String punishmentId) throws ResourceNotFoundException {
        return punishmentQueryService.findByPunishmentId(punishmentId);
    }

    public List<Punishment> findAll() {
        return punishmentQueryService.findAll();
    }

    public List<Punishment> findAllPunishmentArchived(boolean bool) {
        return punishmentQueryService.findAllPunishmentArchived(bool);
    }

    public List<Punishment> getAllOpenAssignments() {
        return punishmentQueryService.getAllOpenAssignments();
    }

    public List<Punishment> getAllPunishmentsForStudents(String studentEmail) {
        return punishmentQueryService.findAllForStudent(studentEmail);
    }

    public List<Punishment> getAllPunishmentByStudentEmail(String studentEmail) {
        return punishmentQueryService.getAllPunishmentByStudentEmail(studentEmail);
    }

    public List<Punishment> getAllPunishmentForStudent(String studentEmail) {
        return punishmentQueryService.getAllPunishmentForStudent(studentEmail);
    }

    public List<TeacherDTO> getTeacherResponse(List<Punishment> punishmentList) {
        return punishmentQueryService.getTeacherResponse(punishmentList);
    }

    public List<Punishment> findAllSchool() {
        return punishmentQueryService.findAllSchool();
    }

    public List<Punishment> findAllPunishmentsByStudentEmail() {
        return punishmentQueryService.findAllPunishmentsByStudentEmail();
    }


    //-----------------------------------------------CREATE METHODS-------------------------------------------

    // Methods that Need Global Filters Due for schools
    public PunishmentResponse createNewPunishForm(PunishmentFormRequest formRequest) throws MessagingException {
        return punishmentCreationService.createNewPunishForm(formRequest);
    }

    public List<PunishmentResponse> createNewPunishFormBulk(List<PunishmentFormRequest> requests) throws MessagingException {
        return punishmentCreationService.createNewPunishFormBulk(requests);
    }

    //--------------------------------------------------CLOSE AND DELETE PUNISHMENTS--------------------------------------

    public PunishmentResponse closePunishment(String infractionName, String studentEmail, List<StudentAnswer> studentAnswers) throws MessagingException {
        return punishmentClosureService.closePunishment(infractionName, studentEmail, studentAnswers);
    }

    public Punishment rejectLevelThree(String punishmentId) throws MessagingException {
        return punishmentClosureService.rejectLevelThree(punishmentId);
    }

    public PunishmentResponse closeByPunishmentId(String punishmentId) throws MessagingException {
        return punishmentClosureService.closeByPunishmentId(punishmentId);
    }

    public Punishment archiveRecord(String punishmentId, String userId, String explanation) throws MessagingException {
        return punishmentClosureService.archiveRecord(punishmentId, userId, explanation);
    }

    public Punishment restoreRecord(String punishmentId) throws MessagingException {
        return punishmentClosureService.restoreRecord(punishmentId);
    }

    public String deletePunishment(Punishment punishment) throws ResourceNotFoundException {
        try {
            punishRepository.delete(punishment);

        } catch (Exception e) {
            throw new ResourceNotFoundException("That infraction does not exist");
        }
        return "Punishment has been deleted";
    }

    public Punishment updateMapIndex(String id, int index) {
        return punishmentUpdateService.updateMapIndex(id, index);
    }

    public List<Punishment> updateTimeCreated() {
        return punishmentUpdateService.updateTimeCreated();
    }

//    public List<Punishment> updateInfractions() {
//        List<Punishment> all = punishRepository.findAll();
//        List<Punishment> saved = new ArrayList<>();
//        for(Punishment punishment : all) {
//            Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfraction().getInfractionId());
//            String id = infraction.getInfractionId();
//            punishment.setInfractionId(id);
//            punishRepository.save(punishment);
//            saved.add(punishment);
//        }
//        return saved;
//    }

    public List<Punishment> updateDescriptions() {
        return punishmentUpdateService.updateDescriptions();
    }

    public List<Punishment> updateStudentEmails() {
        return punishmentUpdateService.updateStudentEmails();
    }

    public List<Punishment> updateInfractionName() {
        return punishmentUpdateService.updateInfractionName();
    }

    public List<Punishment> updateInfractionLevel() {
        return punishmentUpdateService.updateInfractionLevel();
    }

    public List<Punishment> updateSchools() {
        return punishmentUpdateService.updateSchools();
    }

    private void filePositiveWithState(PunishmentFormRequest formRequest) throws IOException, InterruptedException {
        //Get Student and Teacher Details
        Student writeUp = studentRepository.findByStudentEmailIgnoreCase(formRequest.getStudentEmail());
        Employee wroteUp = employeeRepository.findByEmailIgnoreCase(formRequest.getTeacherEmail());

        DateTimeFormatter date = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        DateTimeFormatter time = DateTimeFormatter.ofPattern("h:mm a");

        StateFileRequest stateRequest = new StateFileRequest();
        List<String> parties = new ArrayList<>();
        stateRequest.setParties(parties);
        // Set all the pieces of the State Request
        StateFormIntElement incidentTypeId = new StateFormIntElement(40, "Positive Behavior Achievement");
        stateRequest.setIncidentTypeId(incidentTypeId);
        stateRequest.setIncidentConfigurationGroupId(207);

        StateTimeElement versionDate = new StateTimeElement("5/6/2024", "1:00 PM");
        stateRequest.setVersionDate(new StateTimeElement("5/6/2024", null));
        stateRequest.setIncidentDate(versionDate);

        StateFormIntElement teacher = new StateFormIntElement(2509677, "Iverson, Justin");
        stateRequest.setReportedById(teacher);
        stateRequest.setIncidentPartyId(-1);
        stateRequest.setCurrentUser(teacher);

        StateFormIntElement incidentParty = new StateFormIntElement(1, "");
        stateRequest.setIncidentPartyTypeId(incidentParty);

        StateFormIntElement student = new StateFormIntElement(writeUp.getStateStudentId(), (writeUp.getLastName() + ", " + writeUp.getFirstName() + " (" + writeUp.getStudentIdNumber() + ")"));
        stateRequest.setStudentId(student);

        StateFormIntElement school = new StateFormIntElement(5672, "Burke High School");

        //This is studuent org id
        stateRequest.setOrganizationId(school);

        stateRequest.setOccurredAtOrganizationId(school);

        StateFormIntElement location = new StateFormIntElement(52, "Classroom");
        stateRequest.setLocationId(location);

        List<FieldOptionElement> fieldElements = new ArrayList<>();
        FieldOptionElement positive = new FieldOptionElement(166470, "Other Positive Behavior", "", null, false);
        fieldElements.add(positive);
        stateRequest.setIncidentBehavior(fieldElements);
        stateRequest.setDescription(formRequest.getInfractionDescription());
        List<StateFormIntElement> staffResponse = new ArrayList<>();
        if (formRequest.getCurrency() > 0) {
            staffResponse.add(new StateFormIntElement(166485, "Reward"));
        }
        staffResponse.add(new StateFormIntElement(166484, "Recognition"));
        staffResponse.add(new StateFormIntElement(166486, "Other positive staff response"));
        staffResponse.add(new StateFormIntElement(166487, "Parent Contact - Email"));
        stateRequest.setStaffResponse(staffResponse);
        stateRequest.setIncidentRoleId(1);
        stateRequest.setReadyToAssignActions(false);
        stateRequest.setBehaviorRequiredForActions(true);
        stateRequest.setStudentNumber(writeUp.getStudentIdNumber());
        stateRequest.setOrganizationId(school);

        StateFormBooleanElement notTrue = new StateFormBooleanElement(false, "No");
        stateRequest.setIsSpecialEd(notTrue);
        stateRequest.setIs504(notTrue);

        StateFormIntElement grade = new StateFormIntElement(9, "9th Grade");
        stateRequest.setStudentGrade(grade);

        StateFormIntElement homeless = new StateFormIntElement(1, "Not Homeless");
        stateRequest.setIsHomeless(homeless);
        stateRequest.setRuleInstanceToken(null);


        ObjectMapper mapper = new ObjectMapper();
        String jsonRequest = mapper.writeValueAsString(stateRequest);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://calendar-service-mygto2ljcq-wn.a.run.app/sendincident"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                .header("Content-Type", "application/json") // Set the Content-Type header
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    }

//    //Scheduler for Dormant Guidance Files
//    @Scheduled(cron = "0 0 0 * * ?") // This cron expression means the method will run at midnight every day
//@Transactional
//    public void updateDormantGuidanceReferrals (){
//        LocalDate today = LocalDate.now();
//        System.out.println(today);
//        List<Punishment> punishments = punishRepository.findByFollowUpDateAndGuidanceStatus(today, "DORMANT");
//        System.out.println(punishments);
//        for (Punishment punishment : punishments) {
////            punishment.setGuidanceStatus("OPEN");
//            punishRepository.save(punishment);
//            logger.info("Updated punishment with id: " + punishment.getPunishmentId());
//
//        }
//
//    }

    //    @Scheduled(cron = "0 10 22 * * MON-FRI") // This cron job operates every night at
//    @Bean
//    @Transactional
    public void alertIssAndDetention() throws MessagingException {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        List<Punishment> punishments = punishRepository.findByArchivedAndStatus(false, "OPEN");
        for (Punishment punishment : punishments) {
            if (studentUtils.getWorkDaysBetweenTwoDates(punishment.getTimeCreated(), tomorrow) == 1) {
                emailService.sendAlertEmail("DETENTION", punishment);
            } else {
                emailService.sendAlertEmail("ISS", punishment);
            }


        }
    }

//    public List<PunishmentResponse> createNewAdminReferralBulk(List<PunishmentFormRequest> adminReferralListRequest) throws MessagingException, IOException, InterruptedException {
//        List<PunishmentResponse> punishmentResponse = new ArrayList<>();
//        for(PunishmentFormRequest punishmentFormRequest : adminReferralListRequest) {
//            punishmentResponse.add(createNewAdminReferral(punishmentFormRequest));
//        } return  punishmentResponse;
//    }

//    private PunishmentResponse createNewAdminReferral(PunishmentFormRequest punishmentFormRequest) {
//        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
//        LocalDate now = LocalDate.now();
//
//        Student findMe = studentRepository.findByStudentEmailIgnoreCase(formRequest.getStudentEmail());
//        School ourSchool = schoolRepository.findSchoolBySchoolName(findMe.getSchool());
//    }
}

