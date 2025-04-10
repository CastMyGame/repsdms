package com.reps.demogcloud.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.data.filters.CustomFilters;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.employee.CurrencyTransferRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.officeReferral.OfficeReferralCode;
import com.reps.demogcloud.models.officeReferral.OfficeReferralRequest;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${sm://RepsDiscipline-twilio_username}")
    private String twilioUsername;
    @Value("${sm://RepsDiscipline-twilio_password}")
    private String twilioPassword;
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

    private static String adjustString(String input) {
        // Use regex to match the part before the number and the number itself
        String regex = "(.*?)(\\d+)$";
        return input.replaceAll(regex, "$1 $2").trim();
    }

    private String replaceString(String description) {
        return description.replace("[,", "").replace(",]", "").trim();
    }

    public PunishmentResponse setUpPunishmentResponse(Punishment punishment, Student student) {
        PunishmentResponse punishmentResponse = new PunishmentResponse();
        punishmentResponse.setParentToEmail(student.getParentEmail());
        punishmentResponse.setStudentToEmail(student.getStudentEmail());
        punishmentResponse.setTeacherToEmail(punishment.getTeacherEmail());
        punishmentResponse.setPunishment(punishment);

        return punishmentResponse;
    }

    private String createEmailText(String studentFirstName, String studentLastName, String infractionLevel, String infractionName, String description, String studentEmail) {
        String emailText = " Hello, <br>" +
                " Your child, " + studentFirstName + " " + studentLastName +
                " has received offense number " + infractionLevel + " for " + infractionName + ". " + description +
                ".<br> " +
                "<br>" +
                " As a result they have received an assignment. The goal of the assignment is to provide " + studentFirstName + " " + studentLastName +
                " with information about the infraction and ways to make beneficial decisions in the future. If " + studentFirstName + " " + studentLastName + " does not complete the assignment by the end of the school day tomorrow they will receive a failure to comply with disciplinary action referral which is an office managed referral. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential. <br>" +
                "<br> " +
                " Your child’s login information is as follows at the website https://repsdiscipline.vercel.app/student-login:<br>" +
                " The username is their school email and their password is " + studentEmail + " unless they have changed their password using the forgot my password button on the login screen.<br>" +
                "<br> " +
                " If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment.";

        return replaceString(emailText);
    }

    private String createTextMessage(String studentFirstName, String studentLastName, String infractionLevel, String infractionName, String description) {
        String textMessage = " Your child, " + studentFirstName + " " + studentLastName +
                " has received offense number " + infractionLevel + " for " + infractionName + ". " + description +
                ". " +
                "They have an assignment which is due by the end of the school day tomorrow and if the assignment is not done they will receive a failure to comply with disciplinary action referral which is an office managed referral." +
                "Check your email for additional details, including login info. This is an automated text—please reply to the email or contact the school directly with any questions.";

        return replaceString(textMessage);
    }

    private String createCFRMessage(String studentFirstName, String studentLastName, String infractionName, String teacherEmail, String studentEmail) {
        String cfrMessage = " Hello," +
                "<br>" +
                " Your child, " + studentFirstName + " " + studentLastName +
                " has received another offense for " + infractionName + ". <br>" +
                "<br>" +
                " They currently have an assignment at the website https://repsdiscipline.vercel.app/student-login they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening. <br>" +
                "You may email the teacher directly at " + teacherEmail + " if there are any extenuating circumstances that may have led to this behavior, will prevent the completion of the assignment, or if you have any questions or concerns." +
                "Your child’s login information is as follows at the website https://repsdiscipline.vercel.app/student-login :<br>" +
                "The username is their school email and their password is " + studentEmail + " unless they have changed their password using the forgot my password button on the login screen.<br>" +
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment.";

        return replaceString(cfrMessage);
    }

    private PunishmentResponse sendCFREmailBasedOnType(Punishment punishment, StudentRepository studentRepository, InfractionRepository infractionRepository, SchoolRepository schoolRepository) {
        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());

        PunishmentResponse punishmentResponse = setUpPunishmentResponse(punishment, student);
        punishment.setTimeClosed(LocalDate.now());


        // Grab school info and populate into punishment
        School ourSchool = schoolRepository.findSchoolBySchoolName(student.getSchool());


        punishmentResponse.setSubject(ourSchool.getSchoolName() + " referral for " + student.getFirstName() + " " + student.getLastName());
        if (infraction.getInfractionName().equals("Tardy")) {

            punishmentResponse.setMessage(createCFRMessage(student.getFirstName(), student.getLastName(), infraction.getInfractionName(), punishmentResponse.getTeacherToEmail(), student.getStudentEmail()));
            return punishmentResponse;
        }
        if (infraction.getInfractionName().equals("Unauthorized Device/Cell Phone")) {
            punishmentResponse.setMessage(createCFRMessage(student.getFirstName(), student.getLastName(), infraction.getInfractionName(), punishmentResponse.getTeacherToEmail(), student.getStudentEmail()));
            return punishmentResponse;
        }
        if (infraction.getInfractionName().equals("Disruptive Behavior")) {
            punishmentResponse.setMessage(createCFRMessage(student.getFirstName(), student.getLastName(), infraction.getInfractionName(), punishmentResponse.getTeacherToEmail(), student.getStudentEmail()));
            return punishmentResponse;
        }
        if (infraction.getInfractionName().equals("Horseplay")) {
            punishmentResponse.setMessage(createCFRMessage(student.getFirstName(), student.getLastName(), infraction.getInfractionName(), punishmentResponse.getTeacherToEmail(), student.getStudentEmail()));
            return punishmentResponse;
        }
        if (infraction.getInfractionName().equals("Dress Code")) {
            punishmentResponse.setMessage(createCFRMessage(student.getFirstName(), student.getLastName(), infraction.getInfractionName(), punishmentResponse.getTeacherToEmail(), student.getStudentEmail()));
            return punishmentResponse;
        }
        if (infraction.getInfractionName().equals("Failure to Complete Work")) {
            punishmentResponse.setMessage(createCFRMessage(student.getFirstName(), student.getLastName(), infraction.getInfractionName(), punishmentResponse.getTeacherToEmail(), student.getStudentEmail()));
            return punishmentResponse;
        }

        return punishmentResponse;
    }

    private PunishmentResponse sendEmailBasedOnType(PunishmentFormRequest formRequest, Punishment punishment,
                                                    PunishRepository punishRepository,
                                                    StudentRepository studentRepository,
                                                    InfractionRepository infractionRepository,
                                                    EmailService emailService,
                                                    SchoolRepository schoolRepository) throws MessagingException {

        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());
        PunishmentResponse punishmentResponse = setUpPunishmentResponse(punishment, student);

        Twilio.init(twilioUsername, twilioPassword);


        // Grab school info and populate into punishment
        School ourSchool = schoolRepository.findSchoolBySchoolName(student.getSchool());
        punishmentResponse.setSubject(ourSchool.getSchoolName() + " Referral for " + student.getFirstName() + " " + student.getLastName());
        if (punishment.getClosedTimes() == ourSchool.getMaxPunishLevel()) {
            ////               CHANGE THIS WHEN YOU GET UPDATED EMAIL FOR ADMIN REFERRAL   /////////////////////////

            List<Punishment> punishments = punishRepository.findByStudentEmailIgnoreCaseAndInfractionIdAndStatusAndIsArchived(
                    student.getStudentEmail(), infraction.getInfractionName(), "CLOSED", false
            );
            List<Punishment> referrals = punishRepository.findByStudentEmailIgnoreCaseAndInfractionIdAndStatusAndIsArchived(
                    student.getStudentEmail(), infraction.getInfractionName(), "REFERRAL", false
            );
            List<Punishment> cfr = punishRepository.findByStudentEmailIgnoreCaseAndInfractionIdAndStatusAndIsArchived(
                    student.getStudentEmail(), infraction.getInfractionName(), "CFR", false
            );
            punishments.addAll(referrals);
            punishments.addAll(cfr);
            List<String> message = new ArrayList<>();
            for (Punishment closed : punishments) {
                String messageIn = "Infraction took place on" + closed.getTimeCreated() + " the description of the event is as follows: " + closed.getInfractionDescription() + ". The student received a restorative assignment to complete. The restorative assignment was completed on " + closed.getTimeClosed() + ". ";
                message.add(replaceString(messageIn));
            }

            punishment.setTimeClosed(LocalDate.now());
            punishment.setStatus("REFERRAL");
            punishRepository.save(punishment);

            punishmentResponse.setSubject(ourSchool.getSchoolName() + " Office Referral for " + student.getFirstName() + " " + student.getLastName());
            punishmentResponse.setMessage(
                    " Thank you for using the teacher managed referral. Because " + student.getFirstName() + " " + student.getLastName() +
                            " has received their fourth or greater offense for " + infraction.getInfractionName() + " they will need to receive an office referral. Please Complete an office managed referral for Failure to Comply with Disciplinary Action. Copy and paste the following into “behavior description”. " +
                            student.getFirstName() + " " + student.getLastName() + " received their 4th offense for " + infraction.getInfractionName() + " on " + punishment.getTimeCreated() +
                            "A description of the event is as follows: " + punishment.getInfractionDescription() + " . A summary of their previous infractions is listed below." +
                            message);

            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

        }
        if (infraction.getInfractionName().equals("Tardy") && !(punishment.getClosedTimes() == 4)) {
            sendTextAndEmail(punishment, emailService, student, infraction, punishmentResponse);

        }
        if (infraction.getInfractionName().equals("Unauthorized Device/Cell Phone") & !(punishment.getClosedTimes() == 4)) {
            sendTextAndEmail(punishment, emailService, student, infraction, punishmentResponse);
        }
        if (infraction.getInfractionName().equals("Disruptive Behavior") & !(punishment.getClosedTimes() == 4)) {
            sendTextAndEmail(punishment, emailService, student, infraction, punishmentResponse);
        }
        if (infraction.getInfractionName().equals("Horseplay") & !(punishment.getClosedTimes() == 4)) {
            sendTextAndEmail(punishment, emailService, student, infraction, punishmentResponse);
        }
        if (infraction.getInfractionName().equals("Dress Code") & !(punishment.getClosedTimes() == 4)) {
            sendTextAndEmail(punishment, emailService, student, infraction, punishmentResponse);
        }
        if (infraction.getInfractionName().equals("Inappropriate Language") & !(punishment.getClosedTimes() == 4)) {
            sendTextAndEmail(punishment, emailService, student, infraction, punishmentResponse);
        }
        if (infraction.getInfractionName().equals("Failure to Complete Work")) {
            sendTextAndEmail(punishment, emailService, student, infraction, punishmentResponse);
        }
        if (infraction.getInfractionName().equals("Positive Behavior Shout Out!")) {
            punishmentResponse.setSubject(ourSchool.getSchoolName() + " Positive Shout Out for " + student.getFirstName() + " " + student.getLastName());
            String pointsStatement = "";
            if (formRequest.getCurrency() > 0) {
                pointsStatement = "The teacher has added " + formRequest.getCurrency() + " " + ourSchool.getCurrency() + " to the student's Account. New Total Balance is " + student.getCurrency() + " " + ourSchool.getCurrency() + ".";
            }

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
                    "       <p>" + student.getParentEmail() + ", " + student.getStudentEmail() +
                    "        <p>Your child, <strong>" + student.getFirstName() + " " + student.getLastName() + "</strong>, has received a shout out from their teacher for the following:</p>\n" +
                    "        <p>" + replaceString(punishment.getInfractionDescription().get(0)) + "</p>\n" +
                    "        <p>" + pointsStatement + "</p>\n" +
                    "        <p>If you have any questions or concerns, you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response.</p>\n" +
                    "    </div>\n" +
                    "</body>\n" +
                    "</html>";

//            String textMessage = student.getFirstName() + " " + student.getLastName() +
//                    " has received a positive shout out from their teacher for the following: " + replaceString(punishment.getInfractionDescription().get(0)) +
//                    ".\n " +
//                    "Please check your email for additional details and respond to the teacher directly with any comments, as this is an automated text.";

//            Message.creator(new PhoneNumber(student.getParentPhoneNumber()),
//                    new PhoneNumber("+18437900073"), textMessage).create();

            emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                    punishmentResponse.getTeacherToEmail(),
                    punishmentResponse.getStudentToEmail(),
                    punishmentResponse.getSubject(),
                    message);
        }
        if (infraction.getInfractionName().equals("Behavioral Concern")) {
            punishmentResponse.setSubject(ourSchool.getSchoolName() + " Behavioral Concern for " + student.getFirstName() + " " + student.getLastName());

            punishmentResponse.setMessage(student.getParentEmail() + ", " + student.getStudentEmail() +
                    " Hello, \n" +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    ", demonstrated some concerning behavior during " + adjustString(punishment.getClassPeriod()) + ". " + replaceString(punishment.getInfractionDescription().get(0)) + ". \n" +
                    " At this time there is no disciplinary action being taken. We just wanted to inform you of our concerns and ask for feedback if you have any insight on the behavior and if there is any way" + student.getSchool() + " can help better support " + student.getFirstName() + " " + student.getLastName() +
                    ". We appreciate your assistance and will continue to work to help your child reach their full potential.");

//            String textMessage = student.getFirstName() + " " + student.getLastName() +
//                    " exhibited concerning behavior." + replaceString(punishment.getInfractionDescription().get(0)) +
//                    ".\n " +
//                    "No disciplinary action is being taken at this time.\n" +
//                    "\n" +
//                    "Please check your email for details and respond to the teacher directly with any questions, as this is an automated text.";
//
//            Message.creator(new PhoneNumber(student.getParentPhoneNumber()),
//                    new PhoneNumber("+18437900073"), textMessage).create();

            emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                    punishmentResponse.getTeacherToEmail(),
                    punishmentResponse.getStudentToEmail(),
                    punishmentResponse.getSubject(),
                    punishmentResponse.getMessage());
        }
        if (infraction.getInfractionName().equals("Academic Concern")) {
            punishmentResponse.setSubject(ourSchool.getSchoolName() + " Academic Concern for " + student.getFirstName() + " " + student.getLastName());

            punishmentResponse.setMessage(student.getParentEmail() + ", " + student.getStudentEmail() +
                    " Hello, \n" +
                    " There are some concerns with " + student.getFirstName() + " " + student.getLastName() +
                    "’s academic progress in their " + adjustString(punishment.getClassPeriod()) + " class. " + replaceString(punishment.getInfractionDescription().get(0)) + "\n" +
                    " At this time there is no disciplinary action being taken. We just wanted to inform you of our concerns and ask for feedback if you have any insight on the behavior and if there is any way we can help better support " + student.getFirstName() + " " + student.getLastName() +
                    ". We appreciate your assistance and will continue to work to help your child reach their full potential.");

//            String textMessage = "There are some concerns with " + student.getFirstName() + " " + student.getLastName() +
//                    "'s academic progress." + replaceString(punishment.getInfractionDescription().get(0)) +
//                    ".\n " +
//                    "No disciplinary action is being taken at this time.\n" +
//                    "\n" +
//                    "Please check your email for details and respond to the teacher directly with any questions, as this is an automated text.";
//
//            Message.creator(new PhoneNumber(student.getParentPhoneNumber()),
//                    new PhoneNumber("+18437900073"), textMessage).create();

            emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                    punishmentResponse.getTeacherToEmail(),
                    punishmentResponse.getStudentToEmail(),
                    punishmentResponse.getSubject(),
                    punishmentResponse.getMessage());
        }
        return punishmentResponse;
    }

    private void sendTextAndEmail(Punishment punishment, EmailService emailService, Student student, Infraction infraction, PunishmentResponse punishmentResponse) throws MessagingException {
        punishmentResponse.setMessage(createEmailText(student.getFirstName(), student.getLastName(), infraction.getInfractionLevel(), infraction.getInfractionName(), replaceString(punishment.getInfractionDescription().get(0)), student.getStudentEmail()));

//        Message.creator(new PhoneNumber(student.getParentPhoneNumber()),
//                new PhoneNumber("+18437900073"), createTextMessage(student.getFirstName(), student.getLastName(), infraction.getInfractionLevel(), infraction.getInfractionName(), replaceString(punishment.getInfractionDescription().get(0)))).create();

        emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                punishmentResponse.getTeacherToEmail(),
                punishmentResponse.getStudentToEmail(),
                punishmentResponse.getSubject(),
                punishmentResponse.getMessage());
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

    public PunishmentResponse createNewPunishForm(PunishmentFormRequest formRequest) throws MessagingException, IllegalArgumentException {
// Ensure the description is provided
        if (formRequest.getInfractionDescription() == null || formRequest.getInfractionDescription().isEmpty()) {
            throw new IllegalArgumentException("Infraction description is required.");
        }

        Twilio.init(twilioUsername, twilioPassword);
        LocalDate now = LocalDate.now();

        Student findMe = studentRepository.findByStudentEmailIgnoreCase(formRequest.getStudentEmail());
        School ourSchool = schoolRepository.findSchoolBySchoolName(findMe.getSchool());
        int maxLevel = ourSchool.getMaxPunishLevel();
        List<Punishment> closedPunishments = punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(formRequest.getStudentEmail(), formRequest.getInfractionName(), "CLOSED");

        List<Integer> closedTimes = new ArrayList<>();
        for (Punishment punishment : closedPunishments) {
            closedTimes.add(punishment.getClosedTimes());
        }

        String level = levelCheck(closedTimes, maxLevel);

        Infraction infraction;
        if (!formRequest.getInfractionName().equals("Positive Behavior Shout Out!")
                && !formRequest.getInfractionName().equals("Behavioral Concern")
                && !formRequest.getInfractionName().equals("Failure to Complete Work")
                && !formRequest.getInfractionName().equals("Teacher Guidance Referral")
                && !formRequest.getInfractionName().equals("Student Guidance Referral")
                && !formRequest.getInfractionName().equals("Academic Concern")
                && !formRequest.isAdminReferral()) {
            infraction = infractionRepository.findByInfractionNameAndInfractionLevel(formRequest.getInfractionName(), level);
        } else {
            infraction = infractionRepository.findByInfractionName(formRequest.getInfractionName());
        }
        Punishment punishment = new Punishment();
        ArrayList<String> description = new ArrayList<>();
        if (!formRequest.getPhoneLogDescription().isEmpty()) {
            List<ThreadEvent> phoneCalls = findMe.getNotesArray();
            ThreadEvent phoneLog = new ThreadEvent();
            phoneLog.setCreatedBy(formRequest.getTeacherEmail());
            phoneLog.setDate(now);
            phoneLog.setContent(formRequest.getPhoneLogDescription());
            phoneLog.setEvent("Phone");

            phoneCalls.add(phoneLog);

            findMe.setNotesArray(phoneCalls);
            studentRepository.save(findMe);
        }
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

        if (level.equals("4")) {
            OfficeReferralCode code = new OfficeReferralCode();
            code.setCodeKey(42);
            code.setCodeName("Failure to Comply with Disciplinary Actions");

            ArrayList<String> referralDescription = new ArrayList<>();
            referralDescription.add(formRequest.getInfractionDescription());

            OfficeReferralRequest referralRequest = new OfficeReferralRequest();
            referralRequest.setTeacherEmail(formRequest.getTeacherEmail());
            referralRequest.setStudentEmail(formRequest.getStudentEmail());
            referralRequest.setCurrency(0);
            referralRequest.setClassPeriod(formRequest.getInfractionPeriod());
            referralRequest.setReferralDescription(referralDescription);
            referralRequest.setReferralCode(code);
            officeReferralService.createNewOfficeReferral(referralRequest);

            punishment.setStatus("CLOSED");
            punishment.setClosedTimes(punishment.getClosedTimes() + 1);
        }

        List<Punishment> fetchPunishmentData = punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(formRequest.getStudentEmail(), formRequest.getInfractionName(), "OPEN");
        List<Punishment> pendingPunishmentData = punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(formRequest.getStudentEmail(), formRequest.getInfractionName(), "PENDING");
        fetchPunishmentData.addAll(pendingPunishmentData);
        var findOpen = fetchPunishmentData.stream()
                .filter(x -> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list

        // If It is an admin referral, set to open and make sure send email is correct
        if (formRequest.isAdminReferral()) {
            punishment.setStatus("OPEN");
            punishment.setTimeClosed(now);
            Punishment punishmentRecord = punishRepository.save(punishment);

            if (!formRequest.getGuidanceDescription().isEmpty()) {
                guidanceService.LinkAssignmentToGuidance(findMe, formRequest, punishmentRecord);
            }

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return sendEmailBasedOnType(formRequest, punishment, punishRepository, studentRepository, infractionRepository, emailService, schoolRepository);
        }
        if (infraction.getInfractionName().equals("Positive Behavior Shout Out!")) {
            //save Points if more then zero
            if (formRequest.getCurrency() > 0) {
                employeeService.transferCurrency(new CurrencyTransferRequest(formRequest.getTeacherEmail(), formRequest.getStudentEmail(), formRequest.getCurrency()));
            }
            punishment.setStatus("SO");
            punishment.setTimeClosed(now);
            Punishment punishmentRecord = punishRepository.save(punishment);
            if (!formRequest.getGuidanceDescription().isEmpty()) {
                guidanceService.LinkAssignmentToGuidance(findMe, formRequest, punishmentRecord);
            }

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
//            filePositiveWithState(formRequest);
            return sendEmailBasedOnType(formRequest, punishment, punishRepository, studentRepository, infractionRepository, emailService, schoolRepository);
        }
        if (infraction.getInfractionName().equals("Behavioral Concern")) {
            punishment.setStatus("BC");
            punishment.setTimeClosed(now);
            Punishment punishmentRecord = punishRepository.save(punishment);

            if (!formRequest.getGuidanceDescription().isEmpty()) {
                guidanceService.LinkAssignmentToGuidance(findMe, formRequest, punishmentRecord);
            }

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return sendEmailBasedOnType(formRequest, punishment, punishRepository, studentRepository, infractionRepository, emailService, schoolRepository);
        }
        if (infraction.getInfractionName().equals("Academic Concern")) {
            punishment.setStatus("AC");
            punishment.setTimeClosed(now);
            Punishment punishmentRecord = punishRepository.save(punishment);

            if (!formRequest.getGuidanceDescription().isEmpty()) {
                guidanceService.LinkAssignmentToGuidance(findMe, formRequest, punishmentRecord);
            }

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return sendEmailBasedOnType(formRequest, punishment, punishRepository, studentRepository, infractionRepository, emailService, schoolRepository);
        }
        if (infraction.getInfractionName().equals("Failure to Complete Work")) {
            punishment.setStatus("PENDING");
            punishment.setTimeClosed(now);
            Punishment punishmentRecord = punishRepository.save(punishment);

            if (!formRequest.getGuidanceDescription().isEmpty()) {
                guidanceService.LinkAssignmentToGuidance(findMe, formRequest, punishmentRecord);
            }

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return sendEmailBasedOnType(formRequest, punishment, punishRepository, studentRepository, infractionRepository, emailService, schoolRepository);
        }

        if (findOpen.isEmpty() && !level.equals("4")) {
            punishment.setStatus("OPEN");
            Punishment punishmentRecord = punishRepository.save(punishment);

            if (!formRequest.getGuidanceDescription().isEmpty()) {
                guidanceService.LinkAssignmentToGuidance(findMe, formRequest, punishmentRecord);
            }

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return sendEmailBasedOnType(formRequest, punishment, punishRepository, studentRepository, infractionRepository, emailService, schoolRepository);


        } else {
            punishment.setStatus("CFR");
            punishment.setTimeClosed(LocalDate.now());
            Punishment punishmentRecord = punishRepository.save(punishment);

            if (!formRequest.getGuidanceDescription().isEmpty()) {
                guidanceService.LinkAssignmentToGuidance(findMe, formRequest, punishmentRecord);
            }

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return sendCFREmailBasedOnType(punishment, studentRepository, infractionRepository, schoolRepository);

        }


    }

    public List<PunishmentResponse> createNewPunishFormBulk(List<PunishmentFormRequest> listRequest) throws MessagingException {
        List<PunishmentResponse> punishmentResponse = new ArrayList<>();
        for (PunishmentFormRequest punishmentFormRequest : listRequest) {
            punishmentResponse.add(createNewPunishForm(punishmentFormRequest));
        }
        return punishmentResponse;
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
            punishmentResponse.setMessage(studentClose.getParentEmail() + ", " + studentClose.getStudentEmail() +
                    " Hello, \n" +
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

        String message = studentReject.getParentEmail() + ", " + studentReject.getStudentEmail() +
                "Hello, \n" +
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

    public PunishmentResponse closeByPunishmentId(String punishmentId, String closureReason) throws ResourceNotFoundException, MessagingException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        Punishment findMe = punishRepository.findByPunishmentId(punishmentId);
        Student studentClose = studentRepository.findByStudentEmailIgnoreCase(findMe.getStudentEmail());
        Infraction infractionClose = infractionRepository.findByInfractionId(findMe.getInfractionId());

        // Ensure closureReason is a clean string without brackets/quotes
        if (closureReason.startsWith("[") && closureReason.endsWith("]")) {
            closureReason = closureReason.substring(1, closureReason.length() - 1); // Remove brackets
        }

        closureReason = closureReason.replace("\"", ""); // Remove double quotes if present

        findMe.setStatus("CLOSED");
        findMe.setClosedTimes(findMe.getClosedTimes() + 1);
        findMe.setTimeClosed(LocalDate.now());
        punishRepository.save(findMe);
        PunishmentResponse punishmentResponse = new PunishmentResponse();
        punishmentResponse.setPunishment(findMe);
        punishmentResponse.setMessage(studentClose.getParentEmail() + ", " + studentClose.getStudentEmail() +
                " Hello," +
                " Your child, " + studentClose.getFirstName() + " " + studentClose.getLastName() +
                " has had their assignment removed for the infraction: " + infractionClose.getInfractionName() + ". " +
                "The assignment has been removed for the following reason: \n" + closureReason + " " +
                "If you have any questions or concerns feel free to respond to this email by clicking reply all or contact the school directly");
        punishmentResponse.setSubject(studentClose.getSchool() + " assignment removed for " + studentClose.getFirstName() + " " + studentClose.getLastName());
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

        String deleteMessage = student.getParentEmail() + ", " + student.getStudentEmail() +
                "Hello,\n" +
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

        String restoreMessage = student.getParentEmail() + ", " + student.getStudentEmail() +
                "Hello,\n" +
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

