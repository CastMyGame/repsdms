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

    private static String levelCheck(List<Integer> levels, int maxLevel) {
        int level = 1;
        int discLevel;
        if (maxLevel == 0) {
            discLevel = 4;
        } else {
            discLevel = maxLevel;
        }
        for (Integer lev : levels) {
            if (lev > level) {
                level = lev;
            }
            if (level >= discLevel) {
                level = 4;
            }
        }
        return String.valueOf(level);
    }



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

    public List<Punishment> findAll() {
        return punishRepository.findByIsArchived(false);
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
        var fetchData = punishRepository.findByPunishmentId(punishmentId);
        if (fetchData == null) {
            throw new ResourceNotFoundException("No punishments with that ID exist");
        }

        if (fetchData.isArchived()) {
            throw new ResourceNotFoundException("No punishments with that ID exist");

        }

        logger.debug(String.valueOf(fetchData));
        return fetchData;
    }


    //-----------------------------------------------CREATE METHODS-------------------------------------------

    // Methods that Need Global Filters Due for schools
    public List<Punishment> findAllSchool() {
        return customFilters.FetchPunishmentDataByIsArchivedAndSchool(false);
    }

    public List<Punishment> findAllPunishmentsByStudentEmail() {
        return customFilters.LoggedInStudentFetchPunishmentDataByIsArchivedAndSchool(false);
    }

    //  --------------------------------------DURATION METHODS AND CRON JOBS----------------------------------------------------------

    //--------------------------------------------------CLOSE AND DELETE PUNISHMENTS--------------------------------------
    public PunishmentResponse closePunishment(String infractionName, String studentEmail, List<StudentAnswer> studentAnswers) throws ResourceNotFoundException, MessagingException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        List<Punishment> fetchPunishmentData = punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(studentEmail,
                infractionName, "OPEN");

        var findOpen = fetchPunishmentData.stream()
                .filter(x -> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list


        Punishment findMe;
        if (!findOpen.isEmpty()) {
            findMe = findOpen.get(0);

        } else {
            // Handle the case where findOpen is empty
            throw new ResourceNotFoundException("No open punishments found for the given criteria.");
        }

        Student studentClose = studentRepository.findByStudentEmailIgnoreCase(findMe.getStudentEmail());
        Infraction infractionClose = infractionRepository.findByInfractionId(findMe.getInfractionId());

        if (!studentAnswers.isEmpty()) {
            ArrayList<String> answers = findMe.getInfractionDescription();
            for (StudentAnswer answer : studentAnswers
            ) {
                answers.add(answer.toString());
            }

            findMe.setInfractionDescription(answers);
            findMe.setStatus("PENDING");

            punishRepository.save(findMe);

            PunishmentResponse response = new PunishmentResponse();
            response.setPunishment(findMe);
            return response;
        } else {
            findMe.setStatus("CLOSED");
            findMe.setClosedTimes(findMe.getClosedTimes() + 1);
            findMe.setTimeClosed(LocalDate.now());
            punishRepository.save(findMe);
            PunishmentResponse punishmentResponse = new PunishmentResponse();
            punishmentResponse.setPunishment(findMe);
            punishmentResponse.setMessage(" Hello, \n" +
                    " Your child, " + studentClose.getFirstName() + " " + studentClose.getLastName() +
                    " has successfully completed the assignment given to them in response to the infraction: " + infractionClose.getInfractionName() + ". As a result, no further action is required. Thank you for your support during this process and we appreciate " +
                    studentClose.getFirstName() + " " + studentClose.getLastName() + "'s effort in completing the assignment. \n" +
                    "You may email the teacher directly at " + findMe.getTeacherEmail() + " if you have any questions or concerns.");
            punishmentResponse.setSubject(" referral for " + studentClose.getFirstName() + " " + studentClose.getLastName());
            punishmentResponse.setParentToEmail(studentClose.getParentEmail());
            punishmentResponse.setStudentToEmail(studentClose.getStudentEmail());
            punishmentResponse.setTeacherToEmail(findMe.getTeacherEmail());

            emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                    punishmentResponse.getTeacherToEmail(),
                    punishmentResponse.getStudentToEmail(),
                    punishmentResponse.getSubject(),
                    punishmentResponse.getMessage());

//            Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//                    new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return punishmentResponse;
        }
    }

//    public List<Punishment> getAllOpenForADay() {
//        String subject = "Burke High School Open Referrals";
//        List<Punishment> fetchPunishmentData = punishRepository.findByStatus("OPEN");
//        var open = fetchPunishmentData.stream()
//                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
//                .toList();  // Collect the filtered punishments into a list
//
//        List<Punishment> names = new ArrayList<>();
//        for(Punishment punishment: open) {
//            LocalDate timestamp = punishment.getTimeCreated();
//            LocalDate now = LocalDate.now();
//
//
//            Duration duration = Duration.between(timestamp, now);
//            long hours = duration.toHours();
//            if (hours >= 24) {
//                names.add(punishment);
//            }
//        }
//        String email = "Here is the list of students who have open assignments" + names;
//
//        emailService.sendEmail("castmygameinc@gmail.com", subject, email);
//
//        return open;
//    }

    public Punishment rejectLevelThree(String punishmentId) throws MessagingException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        //get punishment
        Punishment punishment = punishRepository.findByPunishmentId(punishmentId);
        Student studentReject = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        ArrayList<String> infractionContext = punishment.getInfractionDescription();
        String resetContext = infractionContext.get(1);
        List<String> contextToStore = infractionContext.subList(1, infractionContext.size());

        ArrayList<String> studentAnswer = new ArrayList<>();
        studentAnswer.add("");
        studentAnswer.add(resetContext);
        Date currentDate = new Date();
        if (punishment.getAnswerHistory() != null) {
            Map<Date, List<String>> answers = punishment.getAnswerHistory();
            answers.put(currentDate, new ArrayList<>(contextToStore));
        } else {
            punishment.setAnswerHistory(currentDate, new ArrayList<>(contextToStore));

        }
        punishment.setInfractionDescription(studentAnswer);

        punishment.setStatus("OPEN");

        String message = "Hello, \n" +
                "Unfortunately your answers provided to the open ended questions were unacceptable and you must resubmit with acceptable answers to close this out. A description of why your answers were not accepted is:  \n" +
                " \n" +
                contextToStore + " \n" +
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment.";

        String subject = "Level Three Answers not accepted for " + studentReject.getFirstName() + " " + studentReject.getLastName();

        emailService.sendPtsEmail(studentReject.getParentEmail(),
                punishment.getTeacherEmail(),
                studentReject.getStudentEmail(),
                subject,
                message);
        punishment.setMapIndex(0);
        punishRepository.save(punishment);

        return punishment;
    }

    public PunishmentResponse closeByPunishmentId(String punishmentId) throws ResourceNotFoundException, MessagingException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        Punishment findMe = punishRepository.findByPunishmentId(punishmentId);
        Student studentClose = studentRepository.findByStudentEmailIgnoreCase(findMe.getStudentEmail());
        Infraction infractionClose = infractionRepository.findByInfractionId(findMe.getInfractionId());

        findMe.setStatus("CLOSED");
        findMe.setClosedTimes(findMe.getClosedTimes() + 1);
        findMe.setTimeClosed(LocalDate.now());
        punishRepository.save(findMe);
        PunishmentResponse punishmentResponse = new PunishmentResponse();
        punishmentResponse.setPunishment(findMe);
        punishmentResponse.setMessage(" Hello," +
                " Your child, " + studentClose.getFirstName() + " " + studentClose.getLastName() +
                " has successfully completed the assignment given to them in response to the infraction: " + infractionClose.getInfractionName() + ". As a result, no further action is required. Thank you for your support during this process and we appreciate " +
                studentClose.getFirstName() + " " + studentClose.getLastName() + "'s effort in completing the assignment. \n" +
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response.");
        punishmentResponse.setSubject(studentClose.getSchool() + " assignment completion for " + studentClose.getFirstName() + " " + studentClose.getLastName());
        punishmentResponse.setParentToEmail(studentClose.getParentEmail());
        punishmentResponse.setStudentToEmail(studentClose.getStudentEmail());
        punishmentResponse.setTeacherToEmail(findMe.getTeacherEmail());

        emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                punishmentResponse.getTeacherToEmail(),
                punishmentResponse.getStudentToEmail(),
                punishmentResponse.getSubject(),
                punishmentResponse.getMessage());

//            Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//                    new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

        return punishmentResponse;
    }

    public String deletePunishment(Punishment punishment) throws ResourceNotFoundException {
        try {
            punishRepository.delete(punishment);

        } catch (Exception e) {
            throw new ResourceNotFoundException("That infraction does not exist");
        }
        return "Punishment has been deleted";
    }

    public List<Punishment> getAllOpenAssignments() {
        LocalDate now = LocalDate.now();

        List<Punishment> fetchPunishmentData = punishRepository.findByStatusAndTimeCreatedBefore("OPEN", now);
        return fetchPunishmentData.stream()
                .filter(x -> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list
    }

    public List<Punishment> getAllPunishmentsForStudents(String studentEmail) {

        List<Punishment> fetchPunishmentData = punishRepository.findByStudentEmailIgnoreCase(studentEmail);
        return fetchPunishmentData.stream()
                .filter(x -> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list
    }

    public List<Punishment> findAllPunishmentIsArchived(boolean bool) throws ResourceNotFoundException {
        List<Punishment> archivedRecords = punishRepository.findByIsArchived(bool);
        if (archivedRecords.isEmpty()) {
            throw new ResourceNotFoundException("No Archived Records exist in punihsment table");
        }
        return archivedRecords;
    }

    public Punishment archiveRecord(String punishmentId, String userId, String explanation) throws MessagingException {
        //Check for existing record
        Punishment existingRecord = findByPunishmentId(punishmentId);
        Student student = studentRepository.findByStudentEmailIgnoreCase(existingRecord.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(existingRecord.getInfractionId());
        //Updated Record
        existingRecord.setArchived(true);
        LocalDate createdOn = LocalDate.now();
        existingRecord.setArchivedOn(createdOn);
        existingRecord.setArchivedBy(userId);
        existingRecord.setArchivedExplanation(explanation);

        String deleteMessage = "Hello,\n" +
                "Your child, " + student.getFirstName() + " " + student.getLastName() +
                " received a referral in error. The referral that was written was for offense number " + infraction.getInfractionLevel() + " for " + infraction.getInfractionName() +
                ". They were assigned a restorative assignment which has now been removed and the referral will be removed from their record. Thank you for your patience. \n" +
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response.";

        String subject = student.getSchool() + " High School Punishment Deleted for " + student.getFirstName() + " " + student.getLastName();
        emailService.sendPtsEmail(student.getParentEmail(),
                existingRecord.getTeacherEmail(),
                student.getStudentEmail(),
                subject,
                deleteMessage);
        return punishRepository.save(existingRecord);


    }

    public Punishment restoreRecord(String punishmentId) throws MessagingException {
        //Check for existing record
        Punishment existingRecord = punishRepository.findByPunishmentIdAndIsArchived(punishmentId, true);
        Student student = studentRepository.findByStudentEmailIgnoreCase(existingRecord.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(existingRecord.getInfractionId());
        //Updated Record
        existingRecord.setArchived(false);
        existingRecord.setArchivedOn(null);
        existingRecord.setArchivedBy(null);
        existingRecord.setArchivedExplanation(null);

        String restoreMessage = "Hello,\n" +
                "Your child, " + student.getFirstName() + " " + student.getLastName() +
                ", had their referral for offense " + infraction.getInfractionLevel() + " for " + infraction.getInfractionName() +
                " unintentionally deleted. This referral has now been restored and as a result " + student.getFirstName() + " " + student.getLastName() + " will need to complete the restorative assignment that accompanies the referral at repsdiscipline.vercel.app/student-login . \n" +
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response.";

        String subject = student.getSchool() + " High School Punishment Restored for " + student.getFirstName() + " " + student.getLastName();
        emailService.sendPtsEmail(student.getParentEmail(),
                existingRecord.getTeacherEmail(),
                student.getStudentEmail(),
                subject,
                restoreMessage);


        return punishRepository.save(existingRecord);


    }

    public List<Punishment> getAllPunishmentByStudentEmail(String studentEmail) {
        return punishRepository.getAllPunishmentByStudentEmail(studentEmail);
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

    public List<Punishment> getAllPunishmentForStudent(String studentEmail) {
        return punishRepository.findByStudentEmailIgnoreCase(studentEmail);
    }

    public List<TeacherDTO> getTeacherResponse(List<Punishment> punishmentList) {
        // Extract student emails from the given punishmentList
        List<String> studentEmails = punishmentList.stream()
                .map(Punishment::getStudentEmail)
                .collect(Collectors.toList());

        Aggregation aggregation = newAggregation(
                match(Criteria.where("studentEmail").in(studentEmails)), // Match only the specified student emails
                lookup("students", "studentEmail", "studentEmail", "studentInfo"), // Join with the students collection
                unwind("studentInfo"),
                project()
                        .and("studentInfo.studentEmail").as("studentEmail")
                        .and("studentInfo.firstName").as("studentFirstName")
                        .and("studentInfo.lastName").as("studentLastName")
                        .and("infractionName").as("infractionName")
                        .and("timeCreated").as("timeCreated")
                        .and("infractionDescription").as("infractionDescription")
                        .and("classPeriod").as("classPeriod")
                        .and("teacherEmail").as("teacherEmail")
                        .and("status").as("status")
                        .and("infractionLevel").as("infractionLevel")
                        .andExclude("_id")
        );

        AggregationResults<TeacherDTO> results =
                mongoTemplate.aggregate(aggregation, "Punishments", TeacherDTO.class);

        return results.getMappedResults();
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

