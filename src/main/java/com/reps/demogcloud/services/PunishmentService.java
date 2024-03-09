package com.reps.demogcloud.services;


import com.reps.demogcloud.data.*;
import com.reps.demogcloud.data.filters.CustomFilters;
import com.reps.demogcloud.models.ResourceNotFoundException;
//import com.twilio.Twilio;
//import com.twilio.rest.api.v2010.account.Message;
//import com.twilio.type.PhoneNumber;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDate;

import org.apache.catalina.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;


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




    // -----------------------------------------FIND BY METHODS-----------------------------------------


    public List<Punishment> findByStudentEmailAndInfraction(String email,String infractionName) throws ResourceNotFoundException {
        var fetchData = punishRepository.findByStudentStudentEmailAndInfractionInfractionName(email,infractionName);
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

    public List<Punishment> findByStatus(String status) throws ResourceNotFoundException {
        var fetchData = customFilters.FetchPunishmentDataByIsArchivedAndSchoolAndStatus(false,status);


        if (fetchData.isEmpty()) {
            throw new ResourceNotFoundException("No punishments with that status exist");
        }
        logger.debug(String.valueOf(fetchData));
        return fetchData;
    }

    public Punishment findByPunishmentId(Punishment punishment) throws ResourceNotFoundException {
        var fetchData = punishRepository.findByPunishmentId(punishment.getPunishmentId());

        if (fetchData == null) {
            throw new ResourceNotFoundException("No punishments with that ID exist");
        }
        if(fetchData.isArchived()){
            throw new ResourceNotFoundException("Punishment with that Id is archived");
        }



        logger.debug(String.valueOf(fetchData));
        return fetchData;
    }

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
    public List<Punishment> findAll() {
        return customFilters.FetchPunishmentDataByIsArchivedAndSchool(false);    }


    public List<Punishment> findAllPunishmentsByTeacherEmail(String email){
        List<Punishment> preProcessedPunishments = customFilters.FetchPunishmentDataByIsArchivedAndSchool(false);
        return preProcessedPunishments.stream().filter(record -> record.getTeacherEmail().equalsIgnoreCase(email)).toList();
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

    public PunishmentResponse createNewPunishForm(PunishmentFormRequest formRequest) {
//        Twilio.init(secretClient.getSecret("TWILIO-ACCOUNT-SID").toString(), secretClient.getSecret("TWILIO-AUTH-TOKEN").toString());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
        LocalDate now = LocalDate.now();

        Student findMe = studentRepository.findByStudentEmailIgnoreCase(formRequest.getStudentEmail());
        School ourSchool = schoolRepository.findSchoolBySchoolName(findMe.getSchool());
        int maxLevel = ourSchool.getMaxPunishLevel();
        List<Punishment> closedPunishments = punishRepository.findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatus(formRequest.getStudentEmail(), formRequest.getInfractionName(), "CLOSED");
        List<Integer> closedTimes = new ArrayList<>();
        for(Punishment punishment : closedPunishments) {
//            if (punishments.getInfraction().getInfractionName().equals("Failure to Complete Work") | punishments.getInfraction().getInfractionName().equals("Behavioral Concern")
//                    | punishments.getInfraction().getInfractionName().equals("Positive Behavior Shout Out!")) {
//                Infraction findInf = infractionRepository.findByInfractionNameAndInfractionLevel(formRequest.getInfractionName(), "1");
//                findInf.setInfractionDescription(formRequest.getInfractionDescription());
//                punishments.setInfraction(findInf);
//            } else {
            closedTimes.add(punishment.getClosedTimes());
        }

        String level = levelCheck(closedTimes, maxLevel);
        System.out.println(level);

        Punishment punishment = new Punishment();
        punishment.setStudent(findMe);
        punishment.setClassPeriod(formRequest.getInfractionPeriod());
        punishment.setPunishmentId(UUID.randomUUID().toString());
        punishment.setTimeCreated(now);
        punishment.setClosedTimes(Integer.parseInt(level));
        punishment.setTeacherEmail(formRequest.getTeacherEmail());

            Infraction findInf = infractionRepository.findByInfractionNameAndInfractionLevel(formRequest.getInfractionName(), level);
            List<String> description = findInf.getInfractionDescription();
            description.add(formRequest.getInfractionDescription());
            punishment.setInfraction(findInf);
        List<Punishment> fetchPunishmentData = punishRepository.findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatus(punishment.getStudent().getStudentEmail(),
                punishment.getInfraction().getInfractionName(), "OPEN");
        List<Punishment> pendingPunishmentData = punishRepository.findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatus(punishment.getStudent().getStudentEmail(),
                punishment.getInfraction().getInfractionName(), "PENDING");
        fetchPunishmentData.addAll(pendingPunishmentData);
        var findOpen = fetchPunishmentData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list

        var openFilter = findOpen.stream()
                        .filter(x-> x.getStatus().equals("OPEN"))
                                .toList();

        System.out.println(findOpen);
        if(punishment.getInfraction().getInfractionName().equals("Positive Behavior Shout Out!")) {
            punishment.setStatus("SO");
            punishment.setTimeClosed(now);
            punishRepository.save(punishment);

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return sendEmailBasedOnType(punishment,schoolRepository, punishRepository, emailService);
        }
        if(punishment.getInfraction().getInfractionName().equals("Behavioral Concern")) {
            punishment.setStatus("BC");
            punishment.setTimeClosed(now);
            punishRepository.save(punishment);

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return sendEmailBasedOnType(punishment,schoolRepository, punishRepository, emailService);
        }
        if(punishment.getInfraction().getInfractionName().equals("Failure to Complete Work")) {
            punishment.setStatus("PENDING");
            punishment.setTimeClosed(now);
            punishRepository.save(punishment);

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return sendEmailBasedOnType(punishment,schoolRepository, punishRepository, emailService);
        }

        if (findOpen.isEmpty()) {
            punishment.setStatus("OPEN");
            punishRepository.save(punishment);

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return sendEmailBasedOnType(punishment,schoolRepository, punishRepository, emailService);


        } else {
            punishment.setStatus("CFR");
            punishment.setTimeClosed(LocalDate.now());
            punishRepository.save(punishment);

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return sendCFREmailBasedOnType(punishment, schoolRepository);
        }
    }

    public List<PunishmentResponse> createNewPunishFormBulk(List<PunishmentFormRequest> listRequest) {
        List<PunishmentResponse> punishmentResponse = new ArrayList<>();
        for(PunishmentFormRequest punishmentFormRequest : listRequest) {
            punishmentResponse.add(createNewPunishForm(punishmentFormRequest));
        } return  punishmentResponse;
    }

    //--------------------------------------------------CLOSE AND DELETE PUNISHMENTS--------------------------------------
    public PunishmentResponse closePunishment(String infractionName, String studentEmail, List<StudentAnswer> studentAnswers) throws ResourceNotFoundException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        List<Punishment> fetchPunishmentData = punishRepository.findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatus(studentEmail,
                infractionName, "OPEN");

        var findOpen = fetchPunishmentData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list

        System.out.println("Student Answers " + studentAnswers);

        Punishment findMe = findOpen.get(0);

        if(!studentAnswers.isEmpty()) {
            System.out.println(studentAnswers + " Not Null");
            ArrayList<String> answers = findMe.getInfraction().getInfractionDescription();
            for (StudentAnswer answer:studentAnswers
                 ) {
                answers.add(answer.toString());
            }
            Infraction answer = findMe.getInfraction();
            answer.setInfractionDescription(answers);
            findMe.setInfraction(answer);
            findMe.setStatus("PENDING");

            punishRepository.save(findMe);

            PunishmentResponse response = new PunishmentResponse();
            response.setPunishment(findMe);
            return response;
        } else {
        findMe.setStatus("CLOSED");
        System.out.println(findMe.getClosedTimes());
        findMe.setClosedTimes(findMe.getClosedTimes() + 1);
        System.out.println(findMe.getClosedTimes());
        System.out.println(findMe.getTeacherEmail());
        findMe.setTimeClosed(LocalDate.now());
        punishRepository.save(findMe);
        System.out.println(findMe);
        if (findMe != null) {
            PunishmentResponse punishmentResponse = new PunishmentResponse();
            punishmentResponse.setPunishment(findMe);
            punishmentResponse.setMessage(" Hello, \n" +
                    " Your child, " + findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName() +
                    " has successfully completed the assignment given to them in response to the infraction: " + findMe.getInfraction().getInfractionName() + ". As a result, no further action is required. Thank you for your support during this process and we appreciate " +
                    findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName() + "'s effort in completing the assignment. \n" +
                    "Do not respond to this message. Call the school at (843) 579-4815 or email the teacher directly at " + findMe.getTeacherEmail() + " if you have any questions or concerns.");
            punishmentResponse.setSubject("Burke High School referral for " + findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName());
            punishmentResponse.setParentToEmail(findMe.getStudent().getParentEmail());
            punishmentResponse.setStudentToEmail(findMe.getStudent().getStudentEmail());
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
    }};

    public Punishment rejectLevelThree(String punishmentId, String description) {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        //get punishment
        Punishment punishment = punishRepository.findByPunishmentId(punishmentId);
        ArrayList<String> infractionContext = punishment.getInfraction().getInfractionDescription();
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
        Infraction infraction = new Infraction();
        infraction = punishment.getInfraction();
        infraction.setInfractionDescription(studentAnswer);

        punishment.setStatus("OPEN");

        punishment.setInfraction(infraction);

        String message =  "Hello, \n" +
                "Unfortunately your answers provided to the open ended questions were unacceptable and you must resubmit with acceptable answers to close this out. A description of why your answers were not accepted is:  \n" +
                " \n" +
                contextToStore + " \n" +
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.";

        String subject = "Level Three Answers not accepted for " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName();

        emailService.sendPtsEmail(punishment.getStudent().getParentEmail(),
                punishment.getTeacherEmail(),
                punishment.getStudent().getStudentEmail(),
                subject,
                message);
        punishment.setMapIndex(0);
        punishRepository.save(punishment);

        return punishment;
    }

    public PunishmentResponse closeByPunishmentId(String punishmentId) throws ResourceNotFoundException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        Punishment findMe = punishRepository.findByPunishmentId(punishmentId);

        findMe.setStatus("CLOSED");
        findMe.setClosedTimes(findMe.getClosedTimes() + 1);
        findMe.setTimeClosed(LocalDate.now());
        punishRepository.save(findMe);
        if (findMe != null) {
            PunishmentResponse punishmentResponse = new PunishmentResponse();
            punishmentResponse.setPunishment(findMe);
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName() +
                    " has successfully completed the assignment given to them in response to the infraction: " + findMe.getInfraction().getInfractionName() + ". As a result, no further action is required. Thank you for your support during this process and we appreciate " +
                    findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName() + "'s effort in completing the assignment. \n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. You can also call the school directly at (843) 579-4815.");
            punishmentResponse.setSubject("Burke High School assignment completion for " + findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName());
            punishmentResponse.setParentToEmail(findMe.getStudent().getParentEmail());
            punishmentResponse.setStudentToEmail(findMe.getStudent().getStudentEmail());
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
            names.add(punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " " + punishment.getInfraction().getInfractionName());
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
            List<Punishment> fetchPunishmentData = punishRepository.findByStudentStudentEmailIgnoreCase(studentEmail);
        var studentPunishments = fetchPunishmentData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list

            for(Punishment punishment : studentPunishments) {
                String punishmentMessage = punishment.getTimeCreated() + " " + punishment.getInfraction().getInfractionName()
                        + " " + punishment.getInfraction().getInfractionDescription() + "\n";
                messages.add(punishmentMessage);
            }

        return studentPunishments;
    }



    public List<Punishment> getAllReferrals() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

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
                    .filter(punishment -> !punishment.getInfraction().getInfractionName().equals("Positive Behavior Shout Out!") &&
                            !punishment.getInfraction().getInfractionName().equals("Behavioral Concern"))
                    .collect(Collectors.toList());
        }

        // Return an empty list instead of null
        return Collections.emptyList();
    }

    public List<Punishment> getAllReferralsFilteredByTeacher(String email) {
        List<Punishment> writeUps = findAllPunishmentIsArchived(false);
        List<Punishment> wu1 = writeUps.stream().filter(pun -> !pun.getInfraction().getInfractionName().equals("Positive Behavior Shout Out!") && !pun.getInfraction().getInfractionName().equals("Behavioral Concern") && pun.getTeacherEmail().equalsIgnoreCase(email)).toList();
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

    private static PunishmentResponse sendEmailBasedOnType(Punishment punishment,SchoolRepository schoolRepository, PunishRepository punishRepository, EmailService emailService) {
        PunishmentResponse punishmentResponse = new PunishmentResponse();
        punishmentResponse.setParentToEmail(punishment.getStudent().getParentEmail());
        punishmentResponse.setStudentToEmail(punishment.getStudent().getStudentEmail());
        punishmentResponse.setTeacherToEmail(punishment.getTeacherEmail());
        punishmentResponse.setPunishment(punishment);

        // Grab school info and populate into punishment
        School ourSchool = schoolRepository.findSchoolBySchoolName(punishment.getStudent().getSchool());
        punishmentResponse.setSubject(ourSchool.getSchoolName() +" High School Referral for " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName());
        if(punishment.getClosedTimes() == ourSchool.getMaxPunishLevel()) {
            List<Punishment> punishments = punishRepository.findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatusAndIsArchived(
                    punishment.getStudent().getStudentEmail(), punishment.getInfraction().getInfractionName(), "CLOSED",false
            );
            List<Punishment> referrals = punishRepository.findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatusAndIsArchived(
                    punishment.getStudent().getStudentEmail(), punishment.getInfraction().getInfractionName(), "REFERRAL",false
            );
            List<Punishment> cfr = punishRepository.findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatusAndIsArchived(
                    punishment.getStudent().getStudentEmail(), punishment.getInfraction().getInfractionName(), "CFR",false
            );
            punishments.addAll(referrals);
            punishments.addAll(cfr);
            List<String> message = new ArrayList<>();
            for(Punishment closed : punishments) {
                String messageIn ="Infraction took place on" + closed.getTimeCreated() + " the description of the event is as follows: " + closed.getInfraction().getInfractionDescription() + ". The student received a restorative assignment to complete. The restorative assignment was completed on " + closed.getTimeClosed() + ". ";
                messageIn.replace("[,", "");
                messageIn.replace(",]","");
                message.add(messageIn);
            }

            punishment.setTimeClosed(LocalDate.now());
            punishment.setStatus("REFERRAL");
            punishRepository.save(punishment);
            punishmentResponse.setSubject(ourSchool.getSchoolName() + " High School Office Referral for " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName());
            punishmentResponse.setMessage(
                    " Thank you for using the teacher managed referral. Because " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                            " has received their fourth or greater offense for " + punishment.getInfraction().getInfractionName() + " they will need to receive an office referral. Please Complete an office managed referral for Failure to Comply with Disciplinary Action. Copy and paste the following into “behavior description”. " +
                            punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +  " received their 4th offense for " + punishment.getInfraction().getInfractionName() + " on " + punishment.getTimeCreated() +
                            "A description of the event is as follows: " + punishment.getInfraction().getInfractionDescription() + " . A summary of their previous infractions is listed below." +
                            message);

            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

        }
        if(punishment.getInfraction().getInfractionName().equals("Tardy") && !(punishment.getClosedTimes() == 4)) {
            String description = punishment.getInfraction().getInfractionDescription().get(1);
            description.replace("[,", "");
            description.replace("]", "");
            String messageIn = " Hello, \n" +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has received offense number " + punishment.getInfraction().getInfractionLevel() + " for " + punishment.getInfraction().getInfractionName() + ". " + description +
                    ".\n " +
                    "As a result they have received an assignment and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential. \n" +
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
        if(punishment.getInfraction().getInfractionName().equals("Unauthorized Device/Cell Phone") & !(punishment.getClosedTimes() == 4)) {
            String description = punishment.getInfraction().getInfractionDescription().get(1);
            description.replace("[,", "");
            description.replace("]", "");
            String messageIn = " Hello, \n" +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has received offense number " + punishment.getInfraction().getInfractionLevel() + " for " + punishment.getInfraction().getInfractionName() + ". " + description +
                    ".\n " +
                    "As a result they have received an assignment and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential. \n" +
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
        if(punishment.getInfraction().getInfractionName().equals("Disruptive Behavior") & !(punishment.getClosedTimes() == 4)) {
            String description = punishment.getInfraction().getInfractionDescription().get(1);
            description.replace("[,", "");
            description.replace("]", "");
            String messageIn = " Hello, \n" +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has received offense number " + punishment.getInfraction().getInfractionLevel() + " for " + punishment.getInfraction().getInfractionName() + ". " + description +
                    ".\n " +
                    "As a result they have received an assignment and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential. \n" +
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
        if(punishment.getInfraction().getInfractionName().equals("Horseplay") & !(punishment.getClosedTimes() == 4)) {
            String description = punishment.getInfraction().getInfractionDescription().get(1);
            description.replace("[,", "");
            description.replace("]", "");
            String messageIn = " Hello, \n" +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has received offense number " + punishment.getInfraction().getInfractionLevel() + " for " + punishment.getInfraction().getInfractionName() + ". " + description +
                    ".\n " +
                    "As a result they have received an assignment and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential. \n" +
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
        if(punishment.getInfraction().getInfractionName().equals("Dress Code") & !(punishment.getClosedTimes() == 4)) {
            String description = punishment.getInfraction().getInfractionDescription().get(1);
            description.replace("[,", "");
            description.replace("]", "");
            String messageIn = " Hello, \n" +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has received offense number " + punishment.getInfraction().getInfractionLevel() + " for " + punishment.getInfraction().getInfractionName() + ". " + description +
                    ".\n " +
                    "As a result they have received an assignment and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential. \n" +
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
        if(punishment.getInfraction().getInfractionName().equals("Failure to Complete Work")) {
            String description = punishment.getInfraction().getInfractionDescription().get(1);
            description.replace("[,", "");
            description.replace("]", "");
            String messageIn = " Hello, \n" +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has received an infraction for " + punishment.getInfraction().getInfractionName() +
                    " \n" +
                    "As a result they have been assigned lunch detention for tomorrow to complete the following assignment: " + description + ". If " +
                    punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We believe that consistency in completing assignments will have a profound impact on their grade and understanding. Please continue to encourage them to finish the assignment. \n"
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
        if(punishment.getInfraction().getInfractionName().equals("Positive Behavior Shout Out!")) {
            String shoutOut = punishment.getInfraction().getInfractionDescription().get(1);
            shoutOut.replace("[,", "");
            shoutOut.replace(",]","");
            punishmentResponse.setSubject(ourSchool.getSchoolName() + " High School Positive Shout Out for " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName());
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has received a shout out from their teacher for the following: " + shoutOut + "\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. You can also call the school directly at (843) 579-4815.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                    punishmentResponse.getTeacherToEmail(),
                    punishmentResponse.getStudentToEmail(),
                    punishmentResponse.getSubject(),
                    punishmentResponse.getMessage());
        }
        if(punishment.getInfraction().getInfractionName().equals("Behavioral Concern")) {
            String concern = punishment.getInfraction().getInfractionDescription().get(1);
            concern.replace("[,", "");
            concern.replace(",]","");
            punishmentResponse.setSubject(ourSchool.getSchoolName() + " High School Behavioral Concern for " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName());
            punishmentResponse.setMessage(" Hello, \n" +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    ", demonstrated some concerning behavior during " + punishment.getClassPeriod() + ". " + concern + "\n" +
                    " At this time there is no disciplinary action being taken. We just wanted to inform you of our concerns and ask for feedback if you have any insight on the behavior and if there is any way Burke can help better support " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
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

    private static PunishmentResponse sendCFREmailBasedOnType(Punishment punishment, SchoolRepository schoolRepository) {
        PunishmentResponse punishmentResponse = new PunishmentResponse();
        punishmentResponse.setParentToEmail(punishment.getStudent().getParentEmail());
        punishmentResponse.setStudentToEmail(punishment.getStudent().getStudentEmail());
        punishmentResponse.setTeacherToEmail(punishment.getTeacherEmail());
        punishmentResponse.setPunishment(punishment);
        punishment.setTimeClosed(LocalDate.now());

        // Grab school info and populate into punishment
        School ourSchool = schoolRepository.findSchoolBySchoolName(punishment.getStudent().getSchool());

        punishmentResponse.setSubject(ourSchool.getSchoolName() + " High School referral for " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName());
        if (punishment.getInfraction().getInfractionName().equals("Tardy")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for being " + punishment.getInfraction().getInfractionName() + ". \n" +
                    " " + "They currently have this an assignment at the website https://repsdiscipline.vercel.app they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening. \n" +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns." +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (punishment.getInfraction().getInfractionName().equals("Unauthorized Device/Cell Phone")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for having an " + punishment.getInfraction().getInfractionName() + ". \n" +
                    " " + "They currently have this an assignment at the website https://repsdiscipline.vercel.app they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening. \n" +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns." +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (punishment.getInfraction().getInfractionName().equals("Disruptive Behavior")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "They currently have this Open assignment: " + punishment.getInfraction().getInfractionAssign() + " they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening." +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (punishment.getInfraction().getInfractionName().equals("Horseplay")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for " + punishment.getInfraction().getInfractionName() + ". \n" +
                    " " + "They currently have this an assignment at the website https://repsdiscipline.vercel.app they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening. \n" +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns." +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (punishment.getInfraction().getInfractionName().equals("Dress Code")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for " + punishment.getInfraction().getInfractionName() + ". \n" +
                    " " + "They currently have this an assignment at the website https://repsdiscipline.vercel.app they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening. \n" +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns." +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (punishment.getInfraction().getInfractionName().equals("Failure to Complete Work")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for " + punishment.getInfraction().getInfractionName() + ". \n" +
                    " " + "They currently have this an assignment at the website https://repsdiscipline.vercel.app they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening. \n" +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns." +
                    "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app :\n" +
                    "The username is their school email and their password is 123abc unless they have changed their password using the forgot my password button on the login screen.\n" +
                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
//        if(punishment.getInfraction().getInfractionName().equals("Positive Behavior Shout Out!")) {
//            punishmentResponse.setMessage(" Hello," +
//                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
//                    " has received a shout out from their teacher for the following: " + punishment.getInfraction().getInfractionDescription());
//            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
//
//
//    }
//        if(punishment.getInfraction().getInfractionName().equals("Behavioral Concern")) {
//            punishmentResponse.setMessage(" Hello," +
//                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
//                    ", demonstrated some concerning behavior during " + punishment.getClassPeriod() + ". " + punishment.getInfraction().getInfractionDescription() +
//                    "At this time there is no disciplinary action being taken. We just wanted to inform you of our concerns and ask for feedback if you have any insight on the behavior and if there is any way Burke can help better support " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
//                    ". We appreciate your assistance and will continue to work to help your child reach their full potential. Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at" + punishment.getTeacherEmail());
//            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
//
//        }
        return punishmentResponse;
    }

    private static PunishmentResponse sendDisciplineEmail(Punishment punishment, PunishRepository punishRepostory) {
        PunishmentResponse punishmentResponse = new PunishmentResponse();
        punishmentResponse.setParentToEmail(punishment.getStudent().getParentEmail());
        punishmentResponse.setStudentToEmail(punishment.getStudent().getStudentEmail());
        punishmentResponse.setTeacherToEmail(punishment.getTeacherEmail());
        punishmentResponse.setPunishment(punishment);
        List<Punishment> fetchPunishmentData = punishRepostory.findByInfractionInfractionName(punishment.getInfraction().getInfractionName());
        var likeWise = fetchPunishmentData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list
        List<String> message = new ArrayList<>();
        for(Punishment punishments : likeWise) {
            message.add("First infraction took place on" + punishments.getTimeCreated() + " the description of the event is as follows: " + punishments.getInfraction().getInfractionDescription() + ". The student received a restorative assignment to complete. The restorative assignment was completed on " + punishments.getTimeClosed() + ". ");
        }
        punishmentResponse.setSubject("Burke High School Office Referral for " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName());
        punishmentResponse.setMessage(
                " Thank you for using the teacher managed referral. Because " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                " has received their fourth or greater offense for " + punishment.getInfraction().getInfractionName() + " they will need to receive an office referral. Please Complete an office managed referral for Failure to Comply with Disciplinary Action. Copy and paste the following into “behavior description”. " +
                punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +  " received their 4th offense for " + punishment.getInfraction().getInfractionName() + " on " + punishment.getTimeCreated() +
                "A description of the event is as follows: " + punishment.getInfraction().getInfractionDescription() + " . A summary of their previous infractions is listed below." +
                message + "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
        //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
        //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
        return punishmentResponse;
    }

        public List<Punishment> findAllPunishmentIsArchived(boolean bool) throws ResourceNotFoundException {
            List<Punishment> archivedRecords = punishRepository.findByIsArchived(bool);
            if (archivedRecords.isEmpty()) {
                throw new ResourceNotFoundException("No Archived Records exist in punihsment table");
            }
            return archivedRecords;
        }


    public Punishment archiveRecord(String punishmentId, String userId, String explanation) {
        //Check for existing record
        Punishment existingRecord = findByPunishmentId(punishmentId);
        //Updated Record
        existingRecord.setArchived(true);
        LocalDate createdOn = LocalDate.now();
        existingRecord.setArchivedOn(createdOn);
        existingRecord.setArchivedBy(userId);
        existingRecord.setArchivedExplanation(explanation);

        String deleteMessage = "Hello,\n" +
                "Your child, " + existingRecord.getStudent().getFirstName() + " " + existingRecord.getStudent().getLastName() +
                " received a referral in error. The referral that was written was for offense number " + existingRecord.getInfraction().getInfractionLevel() + " for " + existingRecord.getInfraction().getInfractionName() +
                ". They were assigned a restorative assignment which has now been removed and the referral will be removed from their record. Thank you for your patience. \n" +
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. You can also call the school directly at (843) 579-4815.";

        String subject = "Burke High School Punishment Deleted for " + existingRecord.getStudent().getFirstName() + " " + existingRecord.getStudent().getLastName();
        emailService.sendPtsEmail(existingRecord.getStudent().getParentEmail(),
                existingRecord.getTeacherEmail(),
                existingRecord.getStudent().getStudentEmail(),
                subject,
                deleteMessage);
        return punishRepository.save(existingRecord);


    }

    public Punishment restoreRecord(String punishmentId) {
        //Check for existing record
        Punishment existingRecord = punishRepository.findByPunishmentIdAndIsArchived(punishmentId,true);
        //Updated Record
        existingRecord.setArchived(false);
        existingRecord.setArchivedOn(null);
        existingRecord.setArchivedBy(null);
        existingRecord.setArchivedExplanation(null);

        String restoreMessage = "Hello,\n" +
                "Your child, " + existingRecord.getStudent().getFirstName() + " " + existingRecord.getStudent().getLastName() +
                ", had their referral for offense " + existingRecord.getInfraction().getInfractionLevel() + " for " + existingRecord.getInfraction().getInfractionName() +
                " unintentionally deleted. This referral has now been restored and as a result " + existingRecord.getStudent().getFirstName() + " " + existingRecord.getStudent().getLastName() + " will need to complete the restorative assignment that accompanies the referral at repsdiscipline.vercel.app . \n" +
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. You can also call the school directly at (843) 579-4815.";

        String subject = "Burke High School Punishment Restored for " + existingRecord.getStudent().getFirstName() + " " + existingRecord.getStudent().getLastName();
        emailService.sendPtsEmail(existingRecord.getStudent().getParentEmail(),
                existingRecord.getTeacherEmail(),
                existingRecord.getStudent().getStudentEmail(),
                subject,
                restoreMessage);



        return punishRepository.save(existingRecord);


    }

    public List<Punishment> getAllPunishmentByStudentEmail(String studentEmail) {
        return punishRepository.getAllPunishmentByStudentStudentEmail(studentEmail);
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

    public List<Punishment> getAllPunishmentForStudent(String studentEmail) {
        return punishRepository.findByStudentStudentEmailIgnoreCase(studentEmail);
    }
}