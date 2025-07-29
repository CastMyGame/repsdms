package com.reps.demogcloud.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.data.filters.CustomFilters;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.employee.CurrencyTransferRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.enums.InfractionType;
import com.reps.demogcloud.models.officeReferral.OfficeReferralCode;
import com.reps.demogcloud.models.officeReferral.OfficeReferralRequest;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.punishment.PunishmentClosureService;
import com.reps.demogcloud.services.punishment.PunishmentCreationService;
import com.reps.demogcloud.services.punishment.PunishmentQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import javax.mail.MessagingException;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;


@Service
@Slf4j
@RequiredArgsConstructor
public class PunishmentService {

    private final PunishmentCreationService punishmentCreationService;
    private final PunishmentClosureService punishmentClosureService;
    private final PunishmentQueryService punishmentQueryService;

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
    private final GuidanceService guidanceService;
    private final OfficeReferralService officeReferralService;
    @Autowired
    private MongoTemplate mongoTemplate;


    // -----------------------------------------FIND BY METHODS-----------------------------------------
    public List<Punishment> findByStudentEmailAndInfraction(String email, String infractionId) throws ResourceNotFoundException {
        var fetchData = punishRepository.findByStudentEmailAndInfractionId(email, infractionId);
        var punishmentRecord = fetchData.stream()
                .filter(x -> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list

        if (punishmentRecord.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }
        logger.debug(String.valueOf(punishmentRecord));
        return punishmentRecord;
    }

    public List<Punishment> findByStatus(String status) throws ResourceNotFoundException {
        var fetchData = customFilters.FetchPunishmentDataByIsArchivedAndSchoolAndStatus(false, status);


        if (fetchData.isEmpty()) {
            throw new ResourceNotFoundException("No punishments with that status exist");
        }
        logger.debug(String.valueOf(fetchData));
        return fetchData;
    }

    public Punishment findByPunishmentId(String punishmentId) throws ResourceNotFoundException {
        return punishmentQueryService.findByPunishmentId(punishmentId);
    }

    public List<Punishment> findAll() {
        return punishmentQueryService.findAll();
    }

    public List<Punishment> findAllPunishmentIsArchived(boolean bool) {
        return punishmentQueryService.findAllPunishmentIsArchived(bool);
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


    //-----------------------------------------------CREATE METHODS-------------------------------------------

    // Methods that Need Global Filters Due for schools
    public List<Punishment> findAllSchool() {
        return customFilters.FetchPunishmentDataByIsArchivedAndSchool(false);
    }

    public List<Punishment> findAllPunishmentsByStudentEmail() {
        return customFilters.LoggedInStudentFetchPunishmentDataByIsArchivedAndSchool(false);
    }

    public PunishmentResponse createNewPunishForm(PunishmentFormRequest formRequest) throws MessagingException {
        return punishmentCreationService.createNewPunishForm(formRequest);
    }

    public List<PunishmentResponse> createNewPunishFormBulk(List<PunishmentFormRequest> requests) throws MessagingException {
        return punishmentCreationService.createNewPunishFormBulk(requests);
    }

    //  --------------------------------------DURATION METHODS AND CRON JOBS----------------------------------------------------------

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
        Punishment punishment = punishRepository.findByPunishmentId(id);
        if (punishment != null) {
            punishment.setMapIndex(index);
            punishRepository.save(punishment);
            return punishment;

        } else {
            throw new ResourceNotFoundException("No Punishment with Id " + id + " number exist");

        }


    }

    public List<Punishment> updateTimeCreated() {
        List<Punishment> all = punishRepository.findByIsArchived(false);
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            if (punishment.getInfractionName().equals("Tardy") ||
                    punishment.getInfractionName().equals("Horseplay") ||
                    punishment.getInfractionName().equals("Disruptive Behavior") ||
                    punishment.getInfractionName().equals("Unauthorized Device/Cell Phone") ||
                    punishment.getInfractionName().equals("Dress Code")) {
                punishment.setArchived(true);
                punishment.setArchivedBy("repsdiscipline@gmail.com");
                punishment.setArchivedOn(LocalDate.now());
                punishment.setArchivedExplanation(" Tardy Sweep 5/10");
                punishRepository.save(punishment);
                saved.add(punishment);
            }
//            int year = punishment.getTimeCreated().getYear();
//            Month month = punishment.getTimeCreated().getMonth();
//            int day = punishment.getTimeCreated().getDayOfMonth();
//            LocalDate time = LocalDate.of(year, month, day);
//            punishment.setTimeCreated(time);
//            punishRepository.save(punishment);
//            saved.add(punishment);
        }
        return saved;
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
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            if (punishment.getInfractionDescription().size() > 1) {
                punishment.getInfractionDescription().remove(0);
                punishRepository.save(punishment);
                saved.add(punishment);
            }
        }
        return saved;
    }

    public List<Punishment> updateStudentEmails() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            String studentEmail = punishment.getStudentEmail();
            punishment.setStudentEmail(studentEmail);
            punishRepository.save(punishment);
            saved.add(punishment);
        }
        return saved;
    }

    public List<Punishment> updateInfractionName() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            Infraction infractionName = infractionRepository.findByInfractionId(punishment.getInfractionId());
            punishment.setInfractionName(infractionName.getInfractionName());
            punishRepository.save(punishment);
            saved.add(punishment);
        }
        return saved;
    }

    public List<Punishment> updateInfractionLevel() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            Infraction infractionName = infractionRepository.findByInfractionId(punishment.getInfractionId());
            punishment.setInfractionLevel(infractionName.getInfractionLevel());
            punishRepository.save(punishment);
            saved.add(punishment);
        }
        return saved;
    }

    public List<Punishment> updateSchools() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
            punishment.setSchoolName(student.getSchool());
            punishRepository.save(punishment);
            saved.add(punishment);
        }
        return saved;
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

        List<Punishment> punishments = punishRepository.findByIsArchivedAndStatus(false, "OPEN");
        for (Punishment punishment : punishments) {
            if (studentService.getWorkDaysBetweenTwoDates(punishment.getTimeCreated(), tomorrow) == 1) {
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

