package com.reps.demogcloud.services;


import com.reps.demogcloud.data.*;
import com.reps.demogcloud.data.filters.CustomFilters;
import com.reps.demogcloud.models.ResourceNotFoundException;
//import com.twilio.Twilio;
//import com.twilio.rest.api.v2010.account.Message;
//import com.twilio.type.PhoneNumber;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Month;
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
    private final UserService userService;
    private final CustomFilters customFilters;
    private final EmployeeRepository employeeRepository;




    @Autowired
    private MongoTemplate mongoTemplate;


    // -----------------------------------------FIND BY METHODS-----------------------------------------

    public List<Punishment> findByStudentEmailAndInfraction(String email,String infractionId) throws ResourceNotFoundException {
        var fetchData = punishRepository.findByStudentEmailAndInfractionId(email,infractionId);
        var punishmentRecord = fetchData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list

        if (punishmentRecord.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }
        logger.debug(String.valueOf(punishmentRecord));
        System.out.println(punishmentRecord);
        return punishmentRecord;


    }

    public List<Punishment> findAll() {
       List<Punishment> punishments = punishRepository.findByIsArchived(false);
        return punishments;
    }

    public List<Punishment> findAllPunishmentsByTeacherEmail(String email){
        List<Punishment> preProcessedPunishments = punishRepository.findByIsArchived(false);

        return preProcessedPunishments.stream().filter(record -> record.getTeacherEmail().equalsIgnoreCase(email)).toList();
    }

    public List<Punishment> findByInfractionId(String infractionName) throws ResourceNotFoundException {
        List<Punishment> fetchData = punishRepository.findByInfractionId(infractionName);
        var punishmentRecord = fetchData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list


        if (punishmentRecord.isEmpty()) {
            throw new ResourceNotFoundException("No students with that Infraction exist");
        }
        logger.debug(String.valueOf(punishmentRecord));
        return punishmentRecord;
    }

    public List<Punishment> findByStatus(String status) throws ResourceNotFoundException {
        var fetchData = customFilters.FetchPunishmentDataByIsArchivedAndSchoolAndStatus(false,status);


        if (fetchData.isEmpty()) {
            throw new ResourceNotFoundException("No punishments with that status exist");
        }
        logger.debug(String.valueOf(fetchData));
        return fetchData;
    }

//    public Punishment findByPunishmentId(Punishment punishment) throws ResourceNotFoundException {
//        var fetchData = punishRepository.findByPunishmentId(punishment.getPunishmentId());
//
//        if (fetchData == null) {
//            throw new ResourceNotFoundException("No punishments with that ID exist");
//        }
//        if(fetchData.isArchived()){
//            throw new ResourceNotFoundException("Punishment with that Id is archived");
//        }



//        logger.debug(String.valueOf(fetchData));
//        return fetchData;
//    }

    public Punishment findByPunishmentId(String punishmentId) throws ResourceNotFoundException {
        var fetchData = punishRepository.findByPunishmentId(punishmentId);
        if (fetchData == null) {
            throw new ResourceNotFoundException("No punishments with that ID exist");
        }

        if(fetchData.isArchived()){
            throw new ResourceNotFoundException("No punishments with that ID exist");

        }

        logger.debug(String.valueOf(fetchData));
        return fetchData;
    }



    // Methods that Need Global Filters Due for schools
    public List<Punishment> findAllSchool() {
        return customFilters.FetchPunishmentDataByIsArchivedAndSchool(false);    }


    // Uses Logged In User as Teacher Email
    public List<Punishment> findAllPunishmentsByTeacherEmail(){
       return customFilters.LoggedInUserFetchPunishmentDataByIsArchivedAndSchool(false);
    }

    public List<Punishment> findAllPunishmentsByStudentEmail(){
        return customFilters.LoggedInStudentFetchPunishmentDataByIsArchivedAndSchool(false);
    }

    public List<Punishment> findByInfractionName(String infractionName) throws ResourceNotFoundException {
        List<Punishment> fetchData = customFilters.FetchPunishmentDataByInfractionNameAndIsArchived(infractionName,false);


        if (fetchData.isEmpty()) {
            throw new ResourceNotFoundException("No students with that Infraction exist");
        }
        logger.debug(String.valueOf(fetchData));
        return fetchData;
    }



    //-----------------------------------------------CREATE METHODS-------------------------------------------

    public PunishmentResponse createNewPunishForm(PunishmentFormRequest formRequest) throws MessagingException {
//        Twilio.init(secretClient.getSecret("TWILIO-ACCOUNT-SID").toString(), secretClient.getSecret("TWILIO-AUTH-TOKEN").toString());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
        LocalDate now = LocalDate.now();

        Student findMe = studentRepository.findByStudentEmailIgnoreCase(formRequest.getStudentEmail());
        School ourSchool = schoolRepository.findSchoolBySchoolName(findMe.getSchool());
        int maxLevel = ourSchool.getMaxPunishLevel();
        List<Punishment> closedPunishments = punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(formRequest.getStudentEmail(), formRequest.getInfractionName(), "CLOSED");

        List<Integer> closedTimes = new ArrayList<>();
        for(Punishment punishment : closedPunishments) {
            closedTimes.add(punishment.getClosedTimes());
        }

        String level = levelCheck(closedTimes, maxLevel);
        System.out.println(level);
        Infraction infraction = new Infraction();
        if (!formRequest.getInfractionName().equals("Positive Behavior Shout Out!")
        && !formRequest.getInfractionName().equals("Behavioral Concern")
        && !formRequest.getInfractionName().equals("Failure to Complete Work")) {
            infraction = infractionRepository.findByInfractionNameAndInfractionLevel(formRequest.getInfractionName(), level);
        } else {
            infraction = infractionRepository.findByInfractionName(formRequest.getInfractionName());
        }
        Punishment punishment = new Punishment();
//        punishment.setStudent(findMe);
        ArrayList<String> description = new ArrayList<>();
        description.add(formRequest.getInfractionDescription());
        punishment.setStudentEmail(formRequest.getStudentEmail());
        punishment.setInfractionId(infraction.getInfractionId());
        punishment.setClassPeriod(formRequest.getInfractionPeriod());
        punishment.setPunishmentId(UUID.randomUUID().toString());
        punishment.setTimeCreated(now);
        punishment.setClosedTimes(Integer.parseInt(level));
        punishment.setTeacherEmail(formRequest.getTeacherEmail());
        punishment.setInfractionDescription(description);
        punishment.setSchoolName(ourSchool.getSchoolName());
        punishment.setInfractionLevel(infraction.getInfractionLevel());
        punishment.setInfractionName(infraction.getInfractionName());

        List<Punishment> fetchPunishmentData = punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(formRequest.getStudentEmail(), formRequest.getInfractionName(), "OPEN");
        List<Punishment> pendingPunishmentData = punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(formRequest.getStudentEmail(), formRequest.getInfractionName(), "PENDING");
        fetchPunishmentData.addAll(pendingPunishmentData);
        var findOpen = fetchPunishmentData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list

        var openFilter = findOpen.stream()
                        .filter(x-> x.getStatus().equals("OPEN"))
                                .toList();

        System.out.println(findOpen);
//        Infraction infraction = infractionRepository.findByInfractionId(formRequest.getInfractionId());
        if(infraction.getInfractionName().equals("Positive Behavior Shout Out!")) {
         //save Points if more then zero
            if(formRequest.getCurrency() > 0 ){
                int prevPoints = findMe.getCurrency();
                findMe.setPoints(prevPoints + formRequest.getCurrency());
                studentRepository.save(findMe);

            }
            punishment.setStatus("SO");
            punishment.setTimeClosed(now);
            punishRepository.save(punishment);

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return sendEmailBasedOnType(formRequest,punishment, punishRepository, studentRepository, infractionRepository, emailService, schoolRepository);
        }
        if(infraction.getInfractionName().equals("Behavioral Concern")) {
            punishment.setStatus("BC");
            punishment.setTimeClosed(now);
            punishRepository.save(punishment);

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return sendEmailBasedOnType(formRequest, punishment, punishRepository, studentRepository, infractionRepository, emailService, schoolRepository);
        }
        if(infraction.getInfractionName().equals("Failure to Complete Work")) {
            punishment.setStatus("PENDING");
            punishment.setTimeClosed(now);
            punishRepository.save(punishment);

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return sendEmailBasedOnType(formRequest, punishment, punishRepository, studentRepository, infractionRepository, emailService, schoolRepository);
        }

        if (findOpen.isEmpty()) {
            punishment.setStatus("OPEN");
            punishRepository.save(punishment);

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return sendEmailBasedOnType(formRequest, punishment, punishRepository, studentRepository, infractionRepository, emailService, schoolRepository);



        } else {
            punishment.setStatus("CFR");
            punishment.setTimeClosed(LocalDate.now());
            punishRepository.save(punishment);

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return sendCFREmailBasedOnType(punishment, studentRepository, infractionRepository, schoolRepository);

        }
    }

    public List<PunishmentResponse> createNewPunishFormBulk(List<PunishmentFormRequest> listRequest) throws MessagingException {
        List<PunishmentResponse> punishmentResponse = new ArrayList<>();
        for(PunishmentFormRequest punishmentFormRequest : listRequest) {
            punishmentResponse.add(createNewPunishForm(punishmentFormRequest));
        } return  punishmentResponse;
    }

    //--------------------------------------------------CLOSE AND DELETE PUNISHMENTS--------------------------------------
    public PunishmentResponse closePunishment(String infractionName, String studentEmail, List<StudentAnswer> studentAnswers) throws ResourceNotFoundException, MessagingException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        List<Punishment> fetchPunishmentData = punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(studentEmail,
                infractionName, "OPEN");

        var findOpen = fetchPunishmentData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list



        Punishment findMe = null;
        if (!findOpen.isEmpty()) {
            findMe = findOpen.get(0);

        } else {
            // Handle the case where findOpen is empty
            throw new ResourceNotFoundException("No open punishments found for the given criteria.");
        }

        Student studentClose = studentRepository.findByStudentEmailIgnoreCase(findMe.getStudentEmail());
        Infraction infractionClose = infractionRepository.findByInfractionId(findMe.getInfractionId());

        if(!studentAnswers.isEmpty()) {
            System.out.println(studentAnswers + " Not Null");
            ArrayList<String> answers = findMe.getInfractionDescription();
            for (StudentAnswer answer:studentAnswers
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
//        System.out.println(findMe.getClosedTimes());
        findMe.setClosedTimes(findMe.getClosedTimes() + 1);
//        System.out.println(findMe.getClosedTimes());
//        System.out.println(findMe.getTeacherEmail());
        findMe.setTimeClosed(LocalDate.now());
        punishRepository.save(findMe);
//        System.out.println(findMe);
            PunishmentResponse punishmentResponse = new PunishmentResponse();
            punishmentResponse.setPunishment(findMe);
            punishmentResponse.setMessage(" Hello, \n" +
                    " Your child, " + studentClose.getFirstName() + " " + studentClose.getLastName() +
                    " has successfully completed the assignment given to them in response to the infraction: " + infractionClose.getInfractionName() + ". As a result, no further action is required. Thank you for your support during this process and we appreciate " +
                    studentClose.getFirstName() + " " + studentClose.getLastName() + "'s effort in completing the assignment. \n" +
                    "Do not respond to this message. Call the school at (843) 579-4815 or email the teacher directly at " + findMe.getTeacherEmail() + " if you have any questions or concerns.");
            punishmentResponse.setSubject("Burke High School referral for " + studentClose.getFirstName() + " " + studentClose.getLastName());
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
        }}

    public Punishment rejectLevelThree(String punishmentId, String description) throws MessagingException {
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
        if(punishment.getAnswerHistory() !=null){
            Map<Date,List<String>> answers = punishment.getAnswerHistory();
            answers.put(currentDate,new ArrayList<>(contextToStore));
        }else {
            punishment.setAnswerHistory(currentDate, new ArrayList<>(contextToStore));

        }
        punishment.setInfractionDescription(studentAnswer);

        punishment.setStatus("OPEN");

        String message =  "Hello, \n" +
                "Unfortunately your answers provided to the open ended questions were unacceptable and you must resubmit with acceptable answers to close this out. A description of why your answers were not accepted is:  \n" +
                " \n" +
                contextToStore + " \n" +
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.";

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
        if (findMe != null) {
            PunishmentResponse punishmentResponse = new PunishmentResponse();
            punishmentResponse.setPunishment(findMe);
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + studentClose.getFirstName() + " " + studentClose.getLastName() +
                    " has successfully completed the assignment given to them in response to the infraction: " + infractionClose.getInfractionName() + ". As a result, no further action is required. Thank you for your support during this process and we appreciate " +
                    studentClose.getFirstName() + " " + studentClose.getLastName() + "'s effort in completing the assignment. \n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. You can also call the school directly at (843) 579-4815.");
            punishmentResponse.setSubject("Burke High School assignment completion for " + studentClose.getFirstName() + " " + studentClose.getLastName());
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
        } else {
            throw new ResourceNotFoundException("That infraction does not exist");
        }
    }

    public String deletePunishment(Punishment punishment) throws ResourceNotFoundException {
        try {
            punishRepository.delete(punishment);

        } catch (Exception e) {
            throw new ResourceNotFoundException("That infraction does not exist");
        }
        return "Punishment has been deleted";
    }

    //  --------------------------------------DURATION METHODS AND CRON JOBS----------------------------------------------------------

    public List<Punishment> getAllOpenAssignments() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
        LocalDate now = LocalDate.now();
        String subject = "Burke High School Open Referrals";

        List<Punishment> fetchPunishmentData = punishRepository.findByStatusAndTimeCreatedBefore("OPEN", now);
        var open = fetchPunishmentData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list

        List<String> names = new ArrayList<>();

        for(Punishment punishment: open) {
            Student studentAdd = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
            Infraction infractionAdd = infractionRepository.findByInfractionId(punishment.getInfractionId());
            names.add(studentAdd.getFirstName() + " " + studentAdd.getLastName() + " " + infractionAdd.getInfractionName());
        }
        Set<String> openNames = new HashSet<String>(names);
        String email = "Here is the list of students who have open assignments" + openNames;

        return open;
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

    public List<Punishment> getAllPunishmentsForStudents(String studentEmail) {
            List<String> messages = new ArrayList<>();
            Student student = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
            String subject = "Burke High School Student Report for " + student.getFirstName() + " " + student.getLastName() + "\n";
            String intro = "Punishment report for: " + student.getFirstName() + " " + student.getLastName();
            List<Punishment> fetchPunishmentData = punishRepository.findByStudentEmailIgnoreCase(studentEmail);
        var studentPunishments = fetchPunishmentData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list

            for(Punishment punishment : studentPunishments) {
                Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());
                String punishmentMessage = punishment.getTimeCreated() + " " + infraction.getInfractionName()
                        + " " + punishment.getInfractionDescription() + "\n";
                messages.add(punishmentMessage);
            }

        return studentPunishments;
    }



    public List<Punishment> getAllReferrals() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        List<Punishment> writeUps = findAllPunishmentIsArchived(false);
        Infraction infraction = infractionRepository.findByInfractionName("Positive Behavior Shout Out!");
        Infraction infraction2 = infractionRepository.findByInfractionName("Behavioral Concern");
        List<Punishment> wu1 = writeUps.stream().filter(pun -> !pun.getInfractionId().equals(infraction.getInfractionId()) && !pun.getInfractionId().equals(infraction2.getInfractionId())).toList();
//        List<Punishment> wu2 = wu1.stream().filter(pun -> !pun.getInfraction().getInfractionName().equals("Behavioral Concern")).toList();


        //Make Sure We have Logged In User Details
        if (authentication != null && authentication.getPrincipal() != null) {
            UserModel userModel = userService.loadUserModelByUsername(authentication.getName());

            //Fetch Data
            List<Punishment> data = customFilters.FetchPunishmentDataByIsArchivedAndSchool(false);

            // Switch Filter Depending On Role
            if (userModel.getRoles().stream().anyMatch(role -> "TEACHER".equals(role.getRole()))) {
                data = customFilters.filterPunishmentsByTeacherEmail(data,userModel.getUsername());
            }

            if (userModel.getRoles().stream().anyMatch(role -> "STUDENT".equals(role.getRole()))) {
                data = customFilters.filterPunishmentObjByStudent(data,userModel.getUsername());
            }

            //If Admin, defaults to no additional filter

            //Method Specific Filters
            return data.stream()
                    .filter(punishment -> !punishment.getPunishmentId().equals(infraction.getInfractionId()) &&
                            !punishment.getPunishmentId().equals(infraction2.getInfractionId()))
                    .collect(Collectors.toList());
        }

        // Return an empty list instead of null
        return Collections.emptyList();
    }

    public List<Punishment> getAllReferralsFilteredByTeacher(String email) {
        List<Punishment> writeUps = findAllPunishmentIsArchived(false);
        Infraction infraction = infractionRepository.findByInfractionName("Positive Behavior Shout Out!");
        Infraction infraction2 = infractionRepository.findByInfractionName("Behavioral Concern");
        List<Punishment> wu1 = writeUps.stream().filter(pun -> !pun.getInfractionId().equals(infraction.getInfractionId()) && !pun.getInfractionId().equals(infraction2.getInfractionId()) && pun.getTeacherEmail().equalsIgnoreCase(email)).toList();
//        List<Punishment> wu2 = wu1.stream().filter(pun -> !pun.getInfraction().getInfractionName().equals("Behavioral Concern")).toList();

        return wu1;

    }

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


    private static PunishmentResponse sendEmailBasedOnType(PunishmentFormRequest formRequest, Punishment punishment,
                                                           PunishRepository punishRepository,
                                                           StudentRepository studentRepository,
                                                           InfractionRepository infractionRepository,
                                                           EmailService emailService,
                                                           SchoolRepository schoolRepository) throws MessagingException {
        PunishmentResponse punishmentResponse = new PunishmentResponse();
        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());
        punishmentResponse.setParentToEmail(student.getParentEmail());
        punishmentResponse.setStudentToEmail(student.getStudentEmail());
        punishmentResponse.setTeacherToEmail(punishment.getTeacherEmail());
        punishmentResponse.setPunishment(punishment);


        // Grab school info and populate into punishment
        School ourSchool = schoolRepository.findSchoolBySchoolName(student.getSchool());
        punishmentResponse.setSubject(ourSchool.getSchoolName() +" High School Referral for " + student.getFirstName() + " " + student.getLastName());
        if(punishment.getClosedTimes() == ourSchool.getMaxPunishLevel()) {

            List<Punishment> punishments = punishRepository.findByStudentEmailIgnoreCaseAndInfractionIdAndStatusAndIsArchived(
                    student.getStudentEmail(), infraction.getInfractionName(), "CLOSED",false
            );
            List<Punishment> referrals = punishRepository.findByStudentEmailIgnoreCaseAndInfractionIdAndStatusAndIsArchived(
                    student.getStudentEmail(), infraction.getInfractionName(), "REFERRAL",false
            );
            List<Punishment> cfr = punishRepository.findByStudentEmailIgnoreCaseAndInfractionIdAndStatusAndIsArchived(
                    student.getStudentEmail(), infraction.getInfractionName(), "CFR",false
            );
            punishments.addAll(referrals);
            punishments.addAll(cfr);
            List<String> message = new ArrayList<>();
            for(Punishment closed : punishments) {
                String messageIn ="Infraction took place on" + closed.getTimeCreated() + " the description of the event is as follows: " + closed.getInfractionDescription() + ". The student received a restorative assignment to complete. The restorative assignment was completed on " + closed.getTimeClosed() + ". ";
                messageIn.replace("[,", "");
                messageIn.replace(",]","");
                message.add(messageIn);
            }

            punishment.setTimeClosed(LocalDate.now());
            punishment.setStatus("REFERRAL");
            punishRepository.save(punishment);

            punishmentResponse.setSubject(ourSchool.getSchoolName() + " High School Office Referral for " + student.getFirstName() + " " + student.getLastName());
            punishmentResponse.setMessage(
                    " Thank you for using the teacher managed referral. Because " + student.getFirstName() + " " + student.getLastName() +
                            " has received their fourth or greater offense for " + infraction.getInfractionName() + " they will need to receive an office referral. Please Complete an office managed referral for Failure to Comply with Disciplinary Action. Copy and paste the following into “behavior description”. " +
                            student.getFirstName() + " " + student.getLastName() +  " received their 4th offense for " + infraction.getInfractionName() + " on " + punishment.getTimeCreated() +
                            "A description of the event is as follows: " + punishment.getInfractionDescription() + " . A summary of their previous infractions is listed below." +
                            message);

            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

        }
        if(infraction.getInfractionName().equals("Tardy") && !(punishment.getClosedTimes() == 4)) {
            String description = punishment.getInfractionDescription().get(0);
            description.replace("[,", "");
            description.replace("]", "");
            String messageIn = " Hello, \n" +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    " has received offense number " + infraction.getInfractionLevel() + " for " + infraction.getInfractionName() + ". " + description +
                    ".\n " +
                    "As a result they have received an assignment and lunch detention for tomorrow. The goal of the assignment is to provide " + student.getFirstName() + " " + student.getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + student.getFirstName() + " " + student.getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential. \n" +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.";
            punishmentResponse.setMessage(messageIn);
          

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                    punishmentResponse.getTeacherToEmail(),
                    punishmentResponse.getStudentToEmail(),
                    punishmentResponse.getSubject(),
                    punishmentResponse.getMessage());

        }
        if(infraction.getInfractionName().equals("Unauthorized Device/Cell Phone") & !(punishment.getClosedTimes() == 4)) {
            String description = punishment.getInfractionDescription().get(0);
            description.replace("[,", "");
            description.replace("]", "");
            String messageIn = " Hello, \n" +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    " has received offense number " + infraction.getInfractionLevel() + " for " + infraction.getInfractionName() + ". " + description +
                    ".\n " +
                    "As a result they have received an assignment and lunch detention for tomorrow. The goal of the assignment is to provide " + student.getFirstName() + " " + student.getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + student.getFirstName() + " " + student.getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential. \n" +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.";
            messageIn.replace("[,", "");
            messageIn.replace(",]","");
            punishmentResponse.setMessage(messageIn);

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                    punishmentResponse.getTeacherToEmail(),
                    punishmentResponse.getStudentToEmail(),
                    punishmentResponse.getSubject(),
                    punishmentResponse.getMessage());
        }
        if(infraction.getInfractionName().equals("Disruptive Behavior") & !(punishment.getClosedTimes() == 4)) {
            String description = punishment.getInfractionDescription().get(0);
            description.replace("[,", "");
            description.replace("]", "");
            String messageIn = " Hello, \n" +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    " has received offense number " + infraction.getInfractionLevel() + " for " + infraction.getInfractionName() + ". " + description +
                    ".\n " +
                    "As a result they have received an assignment and lunch detention for tomorrow. The goal of the assignment is to provide " + student.getFirstName() + " " + student.getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + student.getFirstName() + " " + student.getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential. \n" +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.";
            messageIn.replace("[,", "");
            messageIn.replace(",]","");
            punishmentResponse.setMessage(messageIn);
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                    punishmentResponse.getTeacherToEmail(),
                    punishmentResponse.getStudentToEmail(),
                    punishmentResponse.getSubject(),
                    punishmentResponse.getMessage());
        }
        if(infraction.getInfractionName().equals("Horseplay") & !(punishment.getClosedTimes() == 4)) {
            String description = punishment.getInfractionDescription().get(0);
            description.replace("[,", "");
            description.replace("]", "");
            String messageIn = " Hello, \n" +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    " has received offense number " + infraction.getInfractionLevel() + " for " + infraction.getInfractionName() + ". " + description +
                    ".\n " +
                    "As a result they have received an assignment and lunch detention for tomorrow. The goal of the assignment is to provide " + student.getFirstName() + " " + student.getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + student.getFirstName() + " " + student.getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential. \n" +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.";
            messageIn.replace("[,", "");
            messageIn.replace(",]","");
            punishmentResponse.setMessage(messageIn);
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                    punishmentResponse.getTeacherToEmail(),
                    punishmentResponse.getStudentToEmail(),
                    punishmentResponse.getSubject(),
                    punishmentResponse.getMessage());
        }
        if(infraction.getInfractionName().equals("Dress Code") & !(punishment.getClosedTimes() == 4)) {
            String description = punishment.getInfractionDescription().get(0);
            description.replace("[,", "");
            description.replace("]", "");
            String messageIn = " Hello, \n" +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    " has received offense number " + infraction.getInfractionLevel() + " for " + infraction.getInfractionName() + ". " + description +
                    ".\n " +
                    "As a result they have received an assignment and lunch detention for tomorrow. The goal of the assignment is to provide " + student.getFirstName() + " " + student.getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + student.getFirstName() + " " + student.getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential. \n" +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.";
            messageIn.replace("[,", "");
            messageIn.replace(",]","");
            punishmentResponse.setMessage(messageIn);
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                    punishmentResponse.getTeacherToEmail(),
                    punishmentResponse.getStudentToEmail(),
                    punishmentResponse.getSubject(),
                    punishmentResponse.getMessage());
        }
        if(infraction.getInfractionName().equals("Failure to Complete Work")) {
            String description = punishment.getInfractionDescription().get(0);
            description.replace("[,", "");
            description.replace("]", "");
            String messageIn = " Hello, \n" +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    " has received an infraction for " + infraction.getInfractionName() +
                    " \n" +
                    "As a result they have been assigned lunch detention for tomorrow to complete the following assignment: " + description + ". If " +
                    student.getFirstName() + " " + student.getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We believe that consistency in completing assignments will have a profound impact on their grade and understanding. Please continue to encourage them to finish the assignment. \n"
                    + "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.";
            messageIn.replace("[,", "");
            messageIn.replace(",]","");
            punishmentResponse.setMessage(messageIn);
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                    punishmentResponse.getTeacherToEmail(),
                    punishmentResponse.getStudentToEmail(),
                    punishmentResponse.getSubject(),
                    punishmentResponse.getMessage());
        }
        if(infraction.getInfractionName().equals("Positive Behavior Shout Out!")) {
            String shoutOut = punishment.getInfractionDescription().get(0);
            shoutOut.replace("[,", "");
            shoutOut.replace(",]","");

            punishmentResponse.setSubject(ourSchool.getSchoolName() + " High School Positive Shout Out for " + student.getFirstName() + " " + student.getLastName());
            String pointsStatement = "";
            if(formRequest.getCurrency() > 0){
                pointsStatement = "The teacher has added " + formRequest.getCurrency() + " to the student's Bucks Account. New Total Balance is " + student.getCurrency() + " Points.";
            }
//            punishmentResponse.setMessage(" Hello," +
//                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
//                    " has received a shout out from their teacher for the following: " + shoutOut + "\n" + pointsStatement + "\n" +
//                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. You can also call the school directly at (843) 579-4815.");
//            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            String message = "<!DOCTYPE html>\n" +
                    "<html lang=\"en\">\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                    "    <title>Shout Out Notification</title>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <div style=\"background-color: lightblue; padding: 10px;\">\n" + // Added header banner with light blue background color
                    "        <h2 style=\"margin: 0;\">REPSDMS</h2>\n" + // Header text
                    "    </div>\n" +
                    "    <div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">\n" +
                    "        <h1>Hello,</h1>\n" +
                    "        <p>Your child, <strong>" + student.getFirstName() + " " + student.getLastName() + "</strong>, has received a shout out from their teacher for the following:</p>\n" +
                    "        <p>" + shoutOut + "</p>\n" +
                    "        <p>" + pointsStatement + "</p>\n" +
                    "        <p>If you have any questions or concerns, you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. You can also call the school directly at (843) 579-4815.</p>\n" +
                    "    </div>\n" +
                    "</body>\n" +
                    "</html>";

            emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                    punishmentResponse.getTeacherToEmail(),
                    punishmentResponse.getStudentToEmail(),
                    punishmentResponse.getSubject(),
                  message);
        }
        if(infraction.getInfractionName().equals("Behavioral Concern")) {
            String concern = punishment.getInfractionDescription().get(0);
            concern.replace("[,", "");
            concern.replace(",]","");

            punishmentResponse.setSubject(ourSchool.getSchoolName() + " High School Behavioral Concern for " + student.getFirstName() + " " + student.getLastName());

            punishmentResponse.setMessage(" Hello, \n" +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    ", demonstrated some concerning behavior during " + punishment.getClassPeriod() + ". " + concern + "\n" +
                    " At this time there is no disciplinary action being taken. We just wanted to inform you of our concerns and ask for feedback if you have any insight on the behavior and if there is any way Burke can help better support " + student.getFirstName() + " " + student.getLastName() +
                    ". We appreciate your assistance and will continue to work to help your child reach their full potential. If you wish to respond to the teacher who wrote the behavioral concern you can do so by clicking reply all to this message and typing a response. You can also contact the school at (843) 579-4815");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                    punishmentResponse.getStudentToEmail(),
                    punishmentResponse.getTeacherToEmail(),
                    punishmentResponse.getSubject(),
                    punishmentResponse.getMessage());
        }
        return punishmentResponse;
    }


    private static PunishmentResponse sendCFREmailBasedOnType(Punishment punishment, StudentRepository studentRepository, InfractionRepository infractionRepository, SchoolRepository schoolRepository) {
        PunishmentResponse punishmentResponse = new PunishmentResponse();
        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());
        punishmentResponse.setParentToEmail(student.getParentEmail());
        punishmentResponse.setStudentToEmail(student.getStudentEmail());
        punishmentResponse.setTeacherToEmail(punishment.getTeacherEmail());
        punishmentResponse.setPunishment(punishment);
        punishment.setTimeClosed(LocalDate.now());


        // Grab school info and populate into punishment
        School ourSchool = schoolRepository.findSchoolBySchoolName(student.getSchool());


        punishmentResponse.setSubject(ourSchool.getSchoolName() + " High School referral for " + student.getFirstName() + " " + student.getLastName());
        if (infraction.getInfractionName().equals("Tardy")) {

            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    " has been written up for being " + infraction.getInfractionName() + ". \n" +
                    " " + "They currently have this an assignment at the website https://repsdiscipline.vercel.app they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening. \n" +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns." +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (infraction.getInfractionName().equals("Unauthorized Device/Cell Phone")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    " has been written up for having an " + infraction.getInfractionName() + ". \n" +
                    " " + "They currently have this an assignment at the website https://repsdiscipline.vercel.app they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening. \n" +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns." +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (infraction.getInfractionName().equals("Disruptive Behavior")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    " has been written up for " + infraction.getInfractionName() + ". \n" +
                    " " + "They currently have this an assignment at the website https://repsdiscipline.vercel.app they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening. \n" +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns." +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (infraction.getInfractionName().equals("Horseplay")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    " has been written up for " + infraction.getInfractionName() + ". \n" +
                    " " + "They currently have this an assignment at the website https://repsdiscipline.vercel.app they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening. \n" +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns." +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (infraction.getInfractionName().equals("Dress Code")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    " has been written up for " + infraction.getInfractionName() + ". \n" +
                    " " + "They currently have this an assignment at the website https://repsdiscipline.vercel.app they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening. \n" +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns." +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (infraction.getInfractionName().equals("Failure to Complete Work")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    " has been written up for " + infraction.getInfractionName() + ". \n" +
                    " " + "They currently have this an assignment at the website https://repsdiscipline.vercel.app they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening. \n" +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns." +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
//        if(infraction.getInfractionName().equals("Positive Behavior Shout Out!")) {
//            punishmentResponse.setMessage(" Hello," +
//                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
//                    " has received a shout out from their teacher for the following: " + infraction.getInfractionDescription());
//            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
//
//
//    }
//        if(infraction.getInfractionName().equals("Behavioral Concern")) {
//            punishmentResponse.setMessage(" Hello," +
//                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
//                    ", demonstrated some concerning behavior during " + punishment.getClassPeriod() + ". " + infraction.getInfractionDescription() +
//                    "At this time there is no disciplinary action being taken. We just wanted to inform you of our concerns and ask for feedback if you have any insight on the behavior and if there is any way Burke can help better support " + student.getFirstName() + " " + student.getLastName() +
//                    ". We appreciate your assistance and will continue to work to help your child reach their full potential. Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at" + punishment.getTeacherEmail());
//            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
//
//        }
        return punishmentResponse;
    }

//    private static PunishmentResponse sendDisciplineEmail(Punishment punishment, PunishRepository punishRepostory) {
//        PunishmentResponse punishmentResponse = new PunishmentResponse();
//        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
//        Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());
//        punishmentResponse.setParentToEmail(student.getParentEmail());
//        punishmentResponse.setStudentToEmail(student.getStudentEmail());
//        punishmentResponse.setTeacherToEmail(punishment.getTeacherEmail());
//        punishmentResponse.setPunishment(punishment);
//        List<Punishment> fetchPunishmentData = punishRepostory.findByInfractionInfractionName(infraction.getInfractionName());
//        var likeWise = fetchPunishmentData.stream()
//                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
//                .toList();  // Collect the filtered punishments into a list
//        List<String> message = new ArrayList<>();
//        for(Punishment punishments : likeWise) {
//            message.add("First infraction took place on" + punishments.getTimeCreated() + " the description of the event is as follows: " + punishments.getInfraction().getInfractionDescription() + ". The student received a restorative assignment to complete. The restorative assignment was completed on " + punishments.getTimeClosed() + ". ");
//        }
//        punishmentResponse.setSubject("Burke High School Office Referral for " + student.getFirstName() + " " + student.getLastName());
//        punishmentResponse.setMessage(
//                " Thank you for using the teacher managed referral. Because " + student.getFirstName() + " " + student.getLastName() +
//                " has received their fourth or greater offense for " + infraction.getInfractionName() + " they will need to receive an office referral. Please Complete an office managed referral for Failure to Comply with Disciplinary Action. Copy and paste the following into “behavior description”. " +
//                student.getFirstName() + " " + student.getLastName() +  " received their 4th offense for " + infraction.getInfractionName() + " on " + punishment.getTimeCreated() +
//                "A description of the event is as follows: " + infraction.getInfractionDescription() + " . A summary of their previous infractions is listed below." +
//                message + "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
//        //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//        //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
//        return punishmentResponse;
//    }
//
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
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. You can also call the school directly at (843) 579-4815.";

        String subject = "Burke High School Punishment Deleted for " + student.getFirstName() + " " + student.getLastName();
        emailService.sendPtsEmail(student.getParentEmail(),
                existingRecord.getTeacherEmail(),
                student.getStudentEmail(),
                subject,
                deleteMessage);
        return punishRepository.save(existingRecord);


    }

    public Punishment restoreRecord(String punishmentId) throws MessagingException {
        //Check for existing record
        Punishment existingRecord = punishRepository.findByPunishmentIdAndIsArchived(punishmentId,true);
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
                " unintentionally deleted. This referral has now been restored and as a result " + student.getFirstName() + " " + student.getLastName() + " will need to complete the restorative assignment that accompanies the referral at repsdiscipline.vercel.app . \n" +
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. You can also call the school directly at (843) 579-4815.";

        String subject = "Burke High School Punishment Restored for " + student.getFirstName() + " " + student.getLastName();
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
        if(punishment !=null){
                punishment.setMapIndex(index);
                punishRepository.save(punishment);
            return punishment;

        }else{
            throw new ResourceNotFoundException("No Punishment with Id " +id +" number exist");

        }


    }

    public List<Punishment> updateTimeCreated() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for(Punishment punishment : all) {
            int year = punishment.getTimeCreated().getYear();
            Month month = punishment.getTimeCreated().getMonth();
            int day = punishment.getTimeCreated().getDayOfMonth();
            LocalDate time = LocalDate.of(year, month, day);
            punishment.setTimeCreated(time);
            punishRepository.save(punishment);
            saved.add(punishment);
        }
        return saved;
    }

    public List<Punishment> updateDescriptions() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for(Punishment punishment : all) {
            if(punishment.getInfractionDescription().size() > 1) {
                punishment.getInfractionDescription().remove(0);
                punishRepository.save(punishment);
                saved.add(punishment);
            }
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

    public List<Punishment> updateStudentEmails() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for(Punishment punishment : all) {
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
        for(Punishment punishment : all) {
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
        for(Punishment punishment : all) {
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
        for(Punishment punishment : all) {
            Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
            String studentEmail = punishment.getStudentEmail();
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
                        .and("teacherEmail").as("teacherEmail")
                        .and("status").as("status")
                        .and("level").as("level")
                        .andExclude("_id")
        );

        AggregationResults<TeacherDTO> results =
                mongoTemplate.aggregate(aggregation, "Punishments", TeacherDTO.class);

        return results.getMappedResults();
    }
    }

