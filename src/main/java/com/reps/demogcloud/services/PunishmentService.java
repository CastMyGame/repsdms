package com.reps.demogcloud.services;


import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.ResourceNotFoundException;
//import com.twilio.Twilio;
//import com.twilio.rest.api.v2010.account.Message;
//import com.twilio.type.PhoneNumber;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.punishment.PunishmentRequest;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.student.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class PunishmentService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final StudentRepository studentRepository;
    private final InfractionRepository infractionRepository;
    private final PunishRepository punishRepository;
    private final EmailService emailService;



    // -----------------------------------------FIND BY METHODS-----------------------------------------
    public List<Punishment> findByStudentEmailAndInfraction(String email,String infractionName) throws ResourceNotFoundException {
        var findMe = punishRepository.findByStudentStudentEmailAndInfractionInfractionName(email,infractionName);

        if (findMe.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }
        logger.debug(String.valueOf(findMe));
        System.out.println(findMe);
        return findMe;


    }

    public List<Punishment> findByStudent(PunishmentRequest punishmentRequest) throws ResourceNotFoundException {
        var findMe = punishRepository.findByStudent(punishmentRequest.getStudent());

        if (findMe.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }
        logger.debug(String.valueOf(findMe));
        return findMe;
    }

    public List<Punishment> findAll() {
        return punishRepository.findAll();
    }

    public List<Punishment> findByInfractionName(String infractionName) throws ResourceNotFoundException {
        List<Punishment> findMe = punishRepository.findByInfractionInfractionName(infractionName);

        if (findMe.isEmpty()) {
            throw new ResourceNotFoundException("No students with that Infraction exist");
        }
        logger.debug(String.valueOf(findMe));
        return findMe;
    }

    public List<Punishment> findByStatus(String status) throws ResourceNotFoundException {
        var findMe = punishRepository.findByStatus(status);

        if (findMe.isEmpty()) {
            throw new ResourceNotFoundException("No punishments with that status exist");
        }
        logger.debug(String.valueOf(findMe));
        return findMe;
    }

    public Punishment findByPunishmentId(Punishment punishment) throws ResourceNotFoundException {
        var findMe = punishRepository.findByPunishmentId(punishment.getPunishmentId());

        if (findMe == null) {
            throw new ResourceNotFoundException("No punishments with that ID exist");
        }
        logger.debug(String.valueOf(findMe));
        return findMe;
    }

    //-----------------------------------------------CREATE METHODS-------------------------------------------

    public PunishmentResponse createNewPunish(PunishmentRequest punishmentRequest) {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        System.out.println("REP CREATED");

        Punishment punishment = new Punishment();
        punishment.setStudent(punishmentRequest.getStudent());
        punishment.setInfraction(punishmentRequest.getInfraction());
        punishment.setPunishmentId(UUID.randomUUID().toString());
//        punishment.setTimeCreated(now.toString());
        punishment.setStatus("OPEN");

        punishRepository.save(punishment);

        PunishmentResponse punishmentResponse = sendEmailBasedOnType(punishment, punishRepository, emailService);

        emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
        emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

//        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

        return punishmentResponse;
    }

    public PunishmentResponse createNewPunishForm(PunishmentFormRequest formRequest) {

        System.out.println(formRequest);
        System.out.println(formRequest.getInfractionName());
        System.out.println(formRequest.getInfractionDescription());
//        Twilio.init(secretClient.getSecret("TWILIO-ACCOUNT-SID").toString(), secretClient.getSecret("TWILIO-AUTH-TOKEN").toString());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        Student findMe = studentRepository.findByStudentEmail(formRequest.getStudentEmail());
        List<Punishment> closedPunishments = punishRepository.findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatus(formRequest.getStudentEmail(), formRequest.getInfractionName(), "CLOSED");
        List<Integer> closedTimes = new ArrayList<>();
        for(Punishment punishments : closedPunishments) {
//            if (punishments.getInfraction().getInfractionName().equals("Failure to Complete Work") | punishments.getInfraction().getInfractionName().equals("Behavioral Concern")
//                    | punishments.getInfraction().getInfractionName().equals("Positive Behavior Shout Out!")) {
//                Infraction findInf = infractionRepository.findByInfractionNameAndInfractionLevel(formRequest.getInfractionName(), "1");
//                findInf.setInfractionDescription(formRequest.getInfractionDescription());
//                punishments.setInfraction(findInf);
//            } else {
                closedTimes.add(punishments.getClosedTimes());
            }

        String level = levelCheck(closedTimes);
        System.out.println(level);
        Punishment punishment = new Punishment();
        punishment.setStudent(findMe);
        punishment.setClassPeriod(formRequest.getInfractionPeriod());
        punishment.setPunishmentId(UUID.randomUUID().toString());
        punishment.setTimeCreated(now);
        punishment.setClosedTimes(Integer.parseInt(level));
        punishment.setTeacherEmail(formRequest.getTeacherEmail());

            Infraction findInf = infractionRepository.findByInfractionNameAndInfractionLevel(formRequest.getInfractionName(), level);
            findInf.setInfractionDescription(formRequest.getInfractionDescription());
            System.out.println(findInf);
            punishment.setInfraction(findInf);
        List<Punishment> findOpen = punishRepository.findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatus(punishment.getStudent().getStudentEmail(),
                punishment.getInfraction().getInfractionName(), "OPEN");
        System.out.println(findOpen);
        if(punishment.getInfraction().getInfractionName().equals("Positive Behavior Shout Out!")) {
            punishment.setStatus("SO");
            punishment.setTimeClosed(now);
            punishRepository.save(punishment);

            PunishmentResponse punishmentResponse = sendEmailBasedOnType(punishment, punishRepository, emailService);
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if(punishment.getInfraction().getInfractionName().equals("Behavioral Concern")) {
            punishment.setStatus("CFR");
            punishment.setTimeClosed(now);
            punishRepository.save(punishment);

            PunishmentResponse punishmentResponse = sendEmailBasedOnType(punishment, punishRepository, emailService);
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if(punishment.getInfraction().getInfractionName().equals("Failure to Complete Work")) {
            punishment.setStatus("CFR");
            punishment.setTimeClosed(now);
            punishRepository.save(punishment);

            PunishmentResponse punishmentResponse = sendEmailBasedOnType(punishment, punishRepository, emailService);

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }

        if (findOpen.isEmpty()) {
            punishment.setStatus("OPEN");
            punishRepository.save(punishment);

            PunishmentResponse punishmentResponse = sendEmailBasedOnType(punishment, punishRepository, emailService);
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;


        } else {
            punishment.setStatus("CFR");
            punishment.setTimeClosed(LocalDateTime.now());
            punishRepository.save(punishment);

            PunishmentResponse punishmentResponse = sendCFREmailBasedOnType(punishment);
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
    }

    //--------------------------------------------------CLOSE AND DELETE PUNISHMENTS--------------------------------------
    public PunishmentResponse closePunishment(String infractionName, String studentEmail) throws ResourceNotFoundException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        List<Punishment> findOpen = punishRepository.findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatus(studentEmail,
                infractionName, "OPEN");

        Punishment findMe = findOpen.get(0);
        findMe.setStatus("CLOSED");
        System.out.println(findMe.getClosedTimes());
        findMe.setClosedTimes(findMe.getClosedTimes() + 1);
        System.out.println(findMe.getClosedTimes());
        System.out.println(findMe.getTeacherEmail());
        findMe.setTimeClosed(LocalDateTime.now());
        punishRepository.save(findMe);
        System.out.println(findMe);
        if (findMe != null) {
            PunishmentResponse punishmentResponse = new PunishmentResponse();
            punishmentResponse.setPunishment(findMe);
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName() +
                    " has successfully completed the assignment given to them in response to the infraction: " + findMe.getInfraction().getInfractionName() + ". As a result, no further action is required. Thank you for your support during this process and we appreciate " +
                    findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName() + "'s effort in completing the assignment. " +
                            "Do not respond to this message. Call the school at (843) 579-4815 or email the teacher directly at " + findMe.getTeacherEmail() + " if you have any questions or concerns.");
            punishmentResponse.setSubject("Burke High School referral for " + findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName());
            punishmentResponse.setParentToEmail(findMe.getStudent().getParentEmail());
            punishmentResponse.setStudentToEmail(findMe.getStudent().getStudentEmail());
            punishmentResponse.setTeacherToEmail(findMe.getTeacherEmail());

            emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

//            Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//                    new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return punishmentResponse;
        } else {
            throw new ResourceNotFoundException("That infraction does not exist");
        }
    }

    public PunishmentResponse closeFailureToComplete(String infractionName, String studentEmail, String teacherEmail) throws ResourceNotFoundException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        List<Punishment> findOpen = punishRepository.findByStatusAndTeacherEmailAndStudentStudentEmailAndInfractionInfractionName("CFR", teacherEmail, studentEmail,
                 infractionName);

        Punishment findMe = findOpen.get(0);
        findMe.setStatus("COMPLETED");
        System.out.println(findMe.getClosedTimes());
        findMe.setClosedTimes(findMe.getClosedTimes());
        findMe.setTimeClosed(LocalDateTime.now());
        punishRepository.save(findMe);
        System.out.println(findMe);
        if (findMe != null) {
            PunishmentResponse punishmentResponse = new PunishmentResponse();
            punishmentResponse.setPunishment(findMe);
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName() +
                    " has successfully completed the assignment given to them in response to the infraction: " + findMe.getInfraction().getInfractionName() + " for " + findMe.getTeacherEmail() + ". As a result, no further action is required. Thank you for your support during this process and we appreciate " +
                    findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName() + "'s effort in completing the assignment. " +
                    "Do not respond to this message. Call the school at (843) 579-4815 if you have any questions or concerns.");
            punishmentResponse.setSubject("Burke High School referral for " + findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName());
            punishmentResponse.setParentToEmail(findMe.getStudent().getParentEmail());
            punishmentResponse.setStudentToEmail(findMe.getStudent().getStudentEmail());
            punishmentResponse.setTeacherToEmail(findMe.getTeacherEmail());

            emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

//            Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//                    new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return punishmentResponse;
        } else {
            throw new ResourceNotFoundException("That infraction does not exist");
        }

    }

    public PunishmentResponse closeByPunishmentId(String punishmentId) throws ResourceNotFoundException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        Punishment findMe = punishRepository.findByPunishmentId(punishmentId);

        findMe.setStatus("CLOSED");
        findMe.setTimeClosed(LocalDateTime.now());
        System.out.println(findMe.getClosedTimes());
        System.out.println(findMe.getClosedTimes());
        System.out.println(findMe.getTeacherEmail());
        punishRepository.save(findMe);
        System.out.println(findMe);
        if (findMe != null) {
            PunishmentResponse punishmentResponse = new PunishmentResponse();
            punishmentResponse.setPunishment(findMe);
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName() +
                    " has successfully completed the assignment given to them in response to the infraction: " + findMe.getInfraction().getInfractionName() + ". As a result, no further action is required. Thank you for your support during this process and we appreciate " +
                    findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName() + "'s effort in completing the assignment. " +
                    "Do not respond to this message. Call the school at (843) 579-4815 or email the teacher directly at " + findMe.getTeacherEmail() + " if you have any questions or concerns.");
            punishmentResponse.setSubject("Burke High School referral for " + findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName());
            punishmentResponse.setParentToEmail(findMe.getStudent().getParentEmail());
            punishmentResponse.setStudentToEmail(findMe.getStudent().getStudentEmail());
            punishmentResponse.setTeacherToEmail(findMe.getTeacherEmail());

            emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

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
        LocalDateTime now = LocalDateTime.now();
        String subject = "Burke High School Open Referrals";

        List<Punishment> open = punishRepository.findByStatusAndTimeCreatedBefore("OPEN", now);

        List<String> names = new ArrayList<>();

        for(Punishment punishment: open) {
            names.add(punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " " + punishment.getInfraction().getInfractionName());
        }
        Set<String> openNames = new HashSet<String>(names);
        String email = "Here is the list of students who have open assignments" + openNames;

        emailService.sendEmail("jiverson22@gmail.com", subject, email);

        return open;
    }

    public List<Punishment> getAllOpenForADay() {
        String subject = "Burke High School Open Referrals";
        List<Punishment> open = punishRepository.findByStatus("OPEN");
        List<Punishment> names = new ArrayList<>();
        System.out.println(LocalDateTime.now());
        for(Punishment punishment: open) {
            String timeCreated = String.valueOf(punishment.getTimeCreated());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
            LocalDateTime timestamp = LocalDateTime.parse(timeCreated, formatter);
            LocalDateTime now = LocalDateTime.now();


            Duration duration = Duration.between(timestamp, now);
            long hours = duration.toHours();
            if (hours >= 24) {
                names.add(punishment);
            }
        }
        String email = "Here is the list of students who have open assignments" + names;

        emailService.sendEmail("castmygameinc@gmail.com", subject, email);

        return open;
    }
//    @Scheduled(cron = "0 0 15 * * MON-FRI")
//    public void TeacherWriteUpEmail() {
//        List<Punishment> open = punishRepository.findByStatus("OPEN");
//        System.out.println(LocalDateTime.now());
//        for(Punishment punishment: open) {
//            String timeCreated = String.valueOf(punishment.getTimeCreated());
//            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
//            LocalDateTime timestamp = LocalDateTime.parse(timeCreated, formatter);
//            LocalDateTime now = LocalDateTime.now();
//
//
//            Duration duration = Duration.between(timestamp, now);
//            long hours = duration.toHours();
//            if (hours >= 6) {
//                String subject = "Failure to Comply with Disciplinary Action for " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName();
//                String email = "Thank you for using the teacher managed referral. Because " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
//                "has not completed the teacher managed restorative assignment they will need to receive an office referral. Please Complete an office managed referral for Failure to Comply with Disciplinary Action. Copy and paste the following into “behavior description”. || " +
//                        "During " + punishment.getClassPeriod() + " on " + punishment.getTimeCreated().toLocalDate() + " " + " " + punishment.getTimeCreated().toLocalTime() + " " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
//                        " received offense number " + punishment.getInfraction().getInfractionLevel() + " for " + punishment.getInfraction().getInfractionName() + ".  For the following incident: "
//                        + punishment.getInfraction().getInfractionDescription() + ". Student failed to complete the restorative assignment by the following day which is why the student is receiving this office referral for Failure to Comply with disciplinary action. Parent was emailed on "
//                        + punishment.getTimeCreated().toLocalDate() + " " + " " + punishment.getTimeCreated().toLocalTime();
//                emailService.sendEmail(punishment.getTeacherEmail(), subject, email);
//            }
//        }
//    }

    @Scheduled(cron = "0 00 11 * * MON-FRI")
    public void getAllOpenAssignmentsBeforeNow() {
        List<Punishment> open = punishRepository.findByStatus("OPEN");

        List<String> names = new ArrayList<>();

        for (Punishment punishment : open) {
            String timeCreated = String.valueOf(punishment.getTimeCreated());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
            LocalDateTime timestamp = LocalDateTime.parse(timeCreated, formatter);
            LocalDateTime now = LocalDateTime.now();

            Duration duration = Duration.between(timestamp, now);
            long hours = duration.toHours();

            if (hours >= 3) {
                names.add("Student: " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + "  |  " + punishment.getTeacherEmail() + " |  Infraction: " + punishment.getInfraction().getInfractionName() + " " +  punishment.getInfraction().getInfractionLevel()
                        + " " + punishment.getInfraction().getInfractionDescription() + " " + punishment.getTimeCreated() + " " + punishment.getInfraction().getInfractionAssign() + " \n");
            }
        }
        Set<String> openNames = new HashSet<String>(names);

        String subject = "Burke High School Open Referrals";
        String email = "Here is the list of students who have open assignments \n" + openNames.toString();

        emailService.sendEmail("jiverson@saga.org", subject, email);
    }

    private static String levelCheck(List<Integer> levels) {
        int level = 1;
        for (Integer lev : levels) {
            if (lev>level) {
                level = lev;
            }
            if(level >= 4) {
                level = 4;
            }
        }
        return String.valueOf(level);
    }

    private static PunishmentResponse sendEmailBasedOnType(Punishment punishment, PunishRepository punishRepository, EmailService emailService) {
        PunishmentResponse punishmentResponse = new PunishmentResponse();
        punishmentResponse.setParentToEmail(punishment.getStudent().getParentEmail());
        punishmentResponse.setStudentToEmail(punishment.getStudent().getStudentEmail());
        punishmentResponse.setTeacherToEmail(punishment.getTeacherEmail());
        punishmentResponse.setPunishment(punishment);
        punishmentResponse.setSubject("Burke High School referral for " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName());
        if(punishment.getClosedTimes() == 4) {
            List<Punishment> punishments = punishRepository.findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatus(
                    punishment.getStudent().getStudentEmail(), punishment.getInfraction().getInfractionName(), "CLOSED"
            );
            List<Punishment> referrals = punishRepository.findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatus(
                    punishment.getStudent().getStudentEmail(), punishment.getInfraction().getInfractionName(), "REFERRAL"
            );
            List<Punishment> cfr = punishRepository.findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatus(
                    punishment.getStudent().getStudentEmail(), punishment.getInfraction().getInfractionName(), "CFR"
            );
            System.out.println(referrals);
            System.out.println(cfr);
            punishments.addAll(referrals);
            punishments.addAll(cfr);
            List<String> message = new ArrayList<>();
            for(Punishment closed : punishments) {
                message.add("Infraction took place on" + closed.getTimeCreated().toLocalDate() + " " + closed.getTimeCreated().toLocalTime() + " the description of the event is as follows: " + closed.getInfraction().getInfractionDescription() + ". The student received a restorative assignment to complete. The restorative assignment was completed on " + closed.getTimeClosed().toLocalDate() + " " + closed.getTimeClosed().toLocalTime() + ". ");
            }
            punishment.setTimeClosed(LocalDateTime.now());
            punishment.setStatus("REFERRAL");
            punishRepository.save(punishment);
            punishmentResponse.setSubject("Burke High School Office Referral for " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName());
            punishmentResponse.setMessage(
                    " Thank you for using the teacher managed referral. Because " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                            " has received their fourth or greater offense for " + punishment.getInfraction().getInfractionName() + " they will need to receive an office referral. Please Complete an office managed referral for Failure to Comply with Disciplinary Action. Copy and paste the following into “behavior description”. " +
                            punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +  " received their 4th offense for " + punishment.getInfraction().getInfractionName() + " on " + punishment.getTimeCreated() +
                            "A description of the event is as follows: " + punishment.getInfraction().getInfractionDescription() + " . A summary of their previous infractions is listed below." +
                            message);

            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

        }
        if(punishment.getInfraction().getInfractionName().equals("Tardy") && !(punishment.getClosedTimes() == 4)) {
                punishmentResponse.setMessage(" Hello," +
                        " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                        " has been written up for being " + punishment.getInfraction().getInfractionName() + ". " + punishment.getInfraction().getInfractionDescription() +
                        ". " + "As a result they have received the following assignment, "
                        + punishment.getInfraction().getInfractionAssign() + " and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                        " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential." +
                        " " +
                        "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");

                //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
                //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

        }
        if(punishment.getInfraction().getInfractionName().equals("Unauthorized Device/Cell Phone") & !(punishment.getClosedTimes() == 4)) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for using an " + punishment.getInfraction().getInfractionName() + " and this is offense # " + punishment.getInfraction().getInfractionLevel() + ". " + punishment.getInfraction().getInfractionDescription() +
                    ". " + "As a result they have received the following assignment, "
                    + punishment.getInfraction().getInfractionAssign() + " and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential." +
                    " " +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

        }
        if(punishment.getInfraction().getInfractionName().equals("Disruptive Behavior") & !(punishment.getClosedTimes() == 4)) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for " + punishment.getInfraction().getInfractionName() + " and this is offense # " + punishment.getInfraction().getInfractionLevel() + " . " + punishment.getInfraction().getInfractionDescription() +
                    ". " + "As a result they have received the following assignment, "
                    + punishment.getInfraction().getInfractionAssign() + " and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential." +
                    " " +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

        }
        if(punishment.getInfraction().getInfractionName().equals("Horseplay") & !(punishment.getClosedTimes() == 4)) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for " + punishment.getInfraction().getInfractionName() + " and this is offense # " + punishment.getInfraction().getInfractionLevel() + ". " + punishment.getInfraction().getInfractionDescription() +
                    ". " + "As a result they have received the following assignment, "
                    + punishment.getInfraction().getInfractionAssign() + " and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential." +
                    " " +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

        }
        if(punishment.getInfraction().getInfractionName().equals("Dress Code") & !(punishment.getClosedTimes() == 4)) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for a violation of the school " + punishment.getInfraction().getInfractionName() + " and this is offense # " + punishment.getInfraction().getInfractionLevel() + ". " + punishment.getInfraction().getInfractionDescription() +
                    ". " + "As a result they have received the following assignment, "
                    + punishment.getInfraction().getInfractionAssign() + " and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential." +
                    " " +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

        }
        if(punishment.getInfraction().getInfractionName().equals("Failure to Complete Work")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has received the infraction " + punishment.getInfraction().getInfractionName() +
                    " " + "As a result they must complete the following assignment, "
                    + punishment.getInfraction().getInfractionDescription() + " during lunch detention tomorrow. If the assignment is completed prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive confirmation from the teacher the assignment is done. We believe that consistency in completing assignments will have a profound impact on their grade and understanding. Please continue to encourage them to finish the assignment. " +
                    " " +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

        }
        if(punishment.getInfraction().getInfractionName().equals("Positive Behavior Shout Out!")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has received a shout out from their teacher for the following: " + punishment.getInfraction().getInfractionDescription());
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

        }
        if(punishment.getInfraction().getInfractionName().equals("Behavioral Concern")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    ", demonstrated some concerning behavior during " + punishment.getClassPeriod() + ". " + punishment.getInfraction().getInfractionDescription() +
                    " At this time there is no disciplinary action being taken. We just wanted to inform you of our concerns and ask for feedback if you have any insight on the behavior and if there is any way Burke can help better support " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    ". We appreciate your assistance and will continue to work to help your child reach their full potential. Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail());
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getTeacherToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

        }

        return punishmentResponse;
    }

    private static PunishmentResponse sendCFREmailBasedOnType(Punishment punishment) {
        PunishmentResponse punishmentResponse = new PunishmentResponse();
        punishmentResponse.setParentToEmail(punishment.getStudent().getParentEmail());
        punishmentResponse.setStudentToEmail(punishment.getStudent().getStudentEmail());
        punishmentResponse.setTeacherToEmail(punishment.getTeacherEmail());
        punishmentResponse.setPunishment(punishment);
        punishment.setTimeClosed(LocalDateTime.now());
        punishmentResponse.setSubject("Burke High School referral for " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName());
        if (punishment.getInfraction().getInfractionName().equals("Tardy")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for being " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "They currently have this Open assignment: " + punishment.getInfraction().getInfractionAssign() + " they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening." +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (punishment.getInfraction().getInfractionName().equals("Unauthorized Device/Cell Phone")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for using an " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "They currently have this Open assignment: " + punishment.getInfraction().getInfractionAssign() + " they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening." +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
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
                    " has been written up for " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "They currently have this Open assignment: " + punishment.getInfraction().getInfractionAssign() + " they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening." +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (punishment.getInfraction().getInfractionName().equals("Dress Code")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for a violation of the school " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "They currently have this Open assignment: " + punishment.getInfraction().getInfractionAssign() + " they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening." +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (punishment.getInfraction().getInfractionName().equals("Failure to Complete Work")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has received an infraction for " + punishment.getInfraction().getInfractionName() +
                    " " + "They currently have this Open assignment: " + punishment.getInfraction().getInfractionAssign() + " they need to complete for this type of offense so they will not be receiving another. " + punishment.getInfraction().getInfractionDescription() + ". Record of this offense will be kept and this email is to inform you of this happening." +
                    "Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at " + punishment.getTeacherEmail() + " if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if(punishment.getInfraction().getInfractionName().equals("Positive Behavior Shout Out!")) {
        punishmentResponse.setMessage(" Hello," +
                " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                " has received a shout out from their teacher for the following: " + punishment.getInfraction().getInfractionDescription());
        //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
        //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

    }
        if(punishment.getInfraction().getInfractionName().equals("Behavioral Concern")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child, " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    ", demonstrated some concerning behavior during " + punishment.getClassPeriod() + ". " + punishment.getInfraction().getInfractionDescription() +
                    "At this time there is no disciplinary action being taken. We just wanted to inform you of our concerns and ask for feedback if you have any insight on the behavior and if there is any way Burke can help better support " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    ". We appreciate your assistance and will continue to work to help your child reach their full potential. Do not respond to this message. Please contact the school at (843) 579-4815 or email the teacher directly at" + punishment.getTeacherEmail());
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

        }
        return punishmentResponse;
    }

    private static PunishmentResponse sendDisciplineEmail(Punishment punishment, PunishRepository punishRepostory) {
        PunishmentResponse punishmentResponse = new PunishmentResponse();
        punishmentResponse.setParentToEmail(punishment.getStudent().getParentEmail());
        punishmentResponse.setStudentToEmail(punishment.getStudent().getStudentEmail());
        punishmentResponse.setTeacherToEmail(punishment.getTeacherEmail());
        punishmentResponse.setPunishment(punishment);
        List<Punishment> likeWise = punishRepostory.findByInfractionInfractionName(punishment.getInfraction().getInfractionName());
        List<String> message = new ArrayList<>();
        for(Punishment punishments : likeWise) {
            message.add("First infraction took place on" + punishments.getTimeCreated().toLocalDate() + " " + punishments.getTimeCreated().toLocalTime() + " the description of the event is as follows: " + punishments.getInfraction().getInfractionDescription() + ". The student received a restorative assignment to complete. The restorative assignment was completed on " + punishments.getTimeClosed().toLocalDate() + " " + punishments.getTimeClosed().toLocalTime() + ". ");
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
}
