package com.reps.demogcloud.services;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.email.ClassAnnouncementRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.contactus.ContactUsRequest;
import com.reps.demogcloud.services.email.EmailNotificationService;
import com.reps.demogcloud.services.email.EmailSenderService;
import com.reps.demogcloud.services.email.EmailTemplateBuilderService;
import com.twilio.Twilio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailNotificationService emailNotificationService;
    private final EmailSenderService emailSenderService;
    private final EmailTemplateBuilderService emailTemplateBuilderService;

    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    private final SpringTemplateEngine springTemplateEngine;
    JavaMailSender javaMailSender;
    @Value("${sm://RepsDiscipline-twilio_username}")
    private String twilioUsername;
    @Value("${sm://RepsDiscipline-twilio_password}")
    private String twilioPassword;


    public static String adjustString(String input) {
        // Use regex to match the part before the number and the number itself
        String regex = "(.*?)(\\d+)$";
        return input.replaceAll(regex, "$1 $2").trim();
    }

    public void createEmailAndSend(String parentEmail, String teacherEmail, String studentEmail, List<String> spotters, String msg, String subject) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        message.setSubject(subject);
        MimeMessageHelper helper;
        helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(parentEmail);

        helper.addCc(teacherEmail);
        helper.addCc(studentEmail);
        for (String email : spotters) {
            helper.addBcc(email);
        }
        helper.setText(msg, true);
        javaMailSender.send(message);
    }

    public void sendHtmlEmail(String templateName, String toEmail, String subject, Map<String, Object> templateModel) throws MessagingException {
        Context context = new Context();
        context.setVariables(templateModel);

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setSubject(subject);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(toEmail);
        String htmlContent = springTemplateEngine.process(templateName, context);
        helper.setText(htmlContent, true);
        javaMailSender.send(mimeMessage);
    }

    @Async
    public void sendEmail(String toEmail, String subject, String msg) throws MessagingException {

        MimeMessage message = javaMailSender.createMimeMessage();
        message.setSubject(subject);
        MimeMessageHelper helper;
        helper = new MimeMessageHelper(message, true);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(toEmail);
        helper.setText(msg, true);

        javaMailSender.send(message);
    }

    @Async
    public void sendPtsEmail(String parentEmail,
                             String teacherEmail,
                             String studentEmail,
                             String subject,
                             String msg) throws MessagingException {
        Student findMe = studentRepository.findByStudentEmailIgnoreCase(studentEmail);

        // Check if getSpotters() is null
        List<String> spotters = new ArrayList<>();
        if (findMe == null) {
            spotters = new ArrayList<>(); // Substitute with an empty array
            throw new IllegalArgumentException("Student not found for given email");
        } else {
            spotters = findMe.getSpotters();
        }

        createEmailAndSend(parentEmail, teacherEmail, studentEmail, spotters, msg, subject);
    }

    @Async
    public void sendContactUsMail(ContactUsRequest request) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(request.getEmail());
        mailMessage.setCc("REPS.DMS@GMAIL.COM");
        mailMessage.setSubject(request.getSubject());
        mailMessage.setText(request.getMessage());
        mailMessage.setFrom("REPS.DMS@GMAIL.COM");
        javaMailSender.send(mailMessage);
    }

    public void sendAlertEmail(String detention, Punishment punishment) throws MessagingException {
        if (detention.equals("DETENTION")) {

            Student findMe = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
            String msg = "Hello, This message is to inform you that " + findMe.getFirstName() + " " + findMe.getLastName() +
                    " has an assignment that they have yet to complete in REPS. If they do not complete this assignment by the beginning of the school day tomorrow" +
                    " they will receive lunch detention and must complete it during that time. If the assignment is completed before then you will receive a confirmation" +
                    " email and can disregard this message. If you have any questions you can hit REPLY ALL and communicate with the teacher who created the original parent contact.";

            createEmailAndSend(findMe.getParentEmail(), punishment.getTeacherEmail(), findMe.getStudentEmail(), findMe.getSpotters(), msg, "DETENTION REMINDER");


        } else if (detention.equals("ISS")) {
            Student findMe = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
            String msg = "Hello, This message is to inform you that " + findMe.getFirstName() + " " + findMe.getLastName() +
                    " has an assignment that they have yet to complete in REPS. If they do not complete this assignment by the beginning of the school day tomorrow" +
                    " they will receive ISS and must complete it during that time. If the assignment is completed before then you will receive a confirmation" +
                    " email and can disregard this message. If you have any questions you can hit REPLY ALL and communicate with the teacher who created the original parent contact.";

            createEmailAndSend(findMe.getParentEmail(), punishment.getTeacherEmail(), findMe.getStudentEmail(), findMe.getSpotters(), msg, "ISS REMINDER");
        }
    }

    @Async
    public void sendEmailGeneric(ArrayList<String> ccEmails,
                                 String recipientEmail,
                                 String subject,
                                 String msg) throws MessagingException {

        MimeMessage message = javaMailSender.createMimeMessage();
        message.setSubject(subject);
        MimeMessageHelper helper;
        helper = new MimeMessageHelper(message, true);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(recipientEmail);
        for (String email : ccEmails) {
            helper.addBcc(email);
        }
        helper.setText(msg, true);

        javaMailSender.send(message);
    }

    @Async
    public void sendClassAnnouncement(ClassAnnouncementRequest request) throws MessagingException {

        Employee teacher = employeeRepository.findByEmailIgnoreCase(request.getTeacherEmail());
        List<Employee.ClassRoster> roster = teacher.getClasses();

        Optional<Employee.ClassRoster> announceClass = roster.stream().filter(name -> name.getClassName().equals(request.getClassName())).findFirst();

        MimeMessage classAnnouncement = javaMailSender.createMimeMessage();

        classAnnouncement.setSubject(request.getSubject());
        MimeMessageHelper helper;
        helper = new MimeMessageHelper(classAnnouncement, true);
        helper.setFrom(request.getTeacherEmail());
        if(announceClass.isPresent()) {
            for (String email : announceClass.get().getClassRoster()) {
                helper.addCc(email);
            }
        }
        helper.setText(request.getMsg(), true);

        javaMailSender.send(classAnnouncement);

    }

    @Async
    public void sendPositiveShoutOut(String toEmail, String studentName) {
        Map<String, Object> positiveTemplateData = new HashMap<>();
        positiveTemplateData.put("studentName", studentName);

        try {
            sendHtmlEmail("positive-shout-out", toEmail, "Positive Shout out for " + studentName, positiveTemplateData);
        } catch (MailException | MessagingException e) {
            log.error("Exception occurred while sending email: {}", e.getMessage());
        }
    }

    public PunishmentResponse sendEmailBasedOnType(PunishmentFormRequest formRequest, Punishment punishment,
                                                   PunishRepository punishRepository,
                                                   StudentRepository studentRepository,
                                                   InfractionRepository infractionRepository,
                                                   EmailService emailService,
                                                   SchoolRepository schoolRepository) throws MessagingException {

        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());
        PunishmentResponse punishmentResponse = setUpPunishmentResponse(punishment, student);

        if (twilioUsername != null && !twilioUsername.isEmpty() &&
                twilioPassword != null && !twilioPassword.isEmpty()) {
            Twilio.init(twilioUsername, twilioPassword);
        }


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
                    "        <p>Your child, <strong>" + student.getFirstName() + " " + student.getLastName() + "</strong>, has received a shout out from their teacher for the following:</p>\n" +
                    "        <p>" + replaceString(punishment.getInfractionDescription().get(0)) + "</p>\n" +
                    "        <p>" + pointsStatement + "</p>\n" +
                    "        <p>If you have any questions or concerns, you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response.</p>\n" +
                    "    </div>\n" +
                    "</body>\n" +
                    "</html>";

            String textMessage = student.getFirstName() + " " + student.getLastName() +
                    " has received a positive shout out from their teacher for the following: " + replaceString(punishment.getInfractionDescription().get(0)) +
                    ".\n " +
                    "Please check your email for additional details and respond to the teacher directly with any comments, as this is an automated text.";

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

            punishmentResponse.setMessage(" Hello, \n" +
                    " Your child, " + student.getFirstName() + " " + student.getLastName() +
                    ", demonstrated some concerning behavior during " + adjustString(punishment.getClassPeriod()) + ". " + replaceString(punishment.getInfractionDescription().get(0)) + ". \n" +
                    " At this time there is no disciplinary action being taken. We just wanted to inform you of our concerns and ask for feedback if you have any insight on the behavior and if there is any way" + student.getSchool() + " can help better support " + student.getFirstName() + " " + student.getLastName() +
                    ". We appreciate your assistance and will continue to work to help your child reach their full potential.");

            String textMessage = student.getFirstName() + " " + student.getLastName() +
                    " exhibited concerning behavior." + replaceString(punishment.getInfractionDescription().get(0)) +
                    ".\n " +
                    "No disciplinary action is being taken at this time.\n" +
                    "\n" +
                    "Please check your email for details and respond to the teacher directly with any questions, as this is an automated text.";

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

            punishmentResponse.setMessage(" Hello, \n" +
                    " There are some concerns with " + student.getFirstName() + " " + student.getLastName() +
                    "’s academic progress in their " + adjustString(punishment.getClassPeriod()) + " class. " + replaceString(punishment.getInfractionDescription().get(0)) + "\n" +
                    " At this time there is no disciplinary action being taken. We just wanted to inform you of our concerns and ask for feedback if you have any insight on the behavior and if there is any way we can help better support " + student.getFirstName() + " " + student.getLastName() +
                    ". We appreciate your assistance and will continue to work to help your child reach their full potential.");

            String textMessage = "There are some concerns with " + student.getFirstName() + " " + student.getLastName() +
                    "'s academic progress." + replaceString(punishment.getInfractionDescription().get(0)) +
                    ".\n " +
                    "No disciplinary action is being taken at this time.\n" +
                    "\n" +
                    "Please check your email for details and respond to the teacher directly with any questions, as this is an automated text.";

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

    @Async
    public void sendTextAndEmail(Punishment punishment, EmailService emailService, Student student, Infraction infraction, PunishmentResponse punishmentResponse) throws MessagingException {
        punishmentResponse.setMessage(createEmailText(student.getFirstName(), student.getLastName(), infraction.getInfractionLevel(), infraction.getInfractionName(), replaceString(punishment.getInfractionDescription().get(0)), student.getStudentEmail()));

//        Message.creator(new PhoneNumber(student.getParentPhoneNumber()),
//                new PhoneNumber("+18437900073"), createTextMessage(student.getFirstName(), student.getLastName(), infraction.getInfractionLevel(), infraction.getInfractionName(), replaceString(punishment.getInfractionDescription().get(0)))).create();

        emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
                punishmentResponse.getTeacherToEmail(),
                punishmentResponse.getStudentToEmail(),
                punishmentResponse.getSubject(),
                punishmentResponse.getMessage());
    }

    public String replaceString(String description) {
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

    public String createEmailText(String studentFirstName, String studentLastName, String infractionLevel, String infractionName, String description, String studentEmail) {
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

    public String createTextMessage(String studentFirstName, String studentLastName, String infractionLevel, String infractionName, String description) {
        String textMessage = " Your child, " + studentFirstName + " " + studentLastName +
                " has received offense number " + infractionLevel + " for " + infractionName + ". " + description +
                ". " +
                "They have an assignment which is due by the end of the school day tomorrow and if the assignment is not done they will receive a failure to comply with disciplinary action referral which is an office managed referral." +
                "Check your email for additional details, including login info. This is an automated text—please reply to the email or contact the school directly with any questions.";

        return replaceString(textMessage);
    }

    public String createCFRMessage(String studentFirstName, String studentLastName, String infractionName, String teacherEmail, String studentEmail) {
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

    public PunishmentResponse sendCFREmailBasedOnType(Punishment punishment, StudentRepository studentRepository, InfractionRepository infractionRepository, SchoolRepository schoolRepository) {
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
}
