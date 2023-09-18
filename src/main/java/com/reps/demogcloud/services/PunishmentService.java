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
import com.reps.demogcloud.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class PunishmentService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final StudentRepository studentRepository;
    private final InfractionRepository infractionRepository;
    private final PunishRepository punishRepository;
    private final EmailService emailService;

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

    public List<Punishment> findByInfraction(PunishmentRequest punishmentRequest) throws ResourceNotFoundException {
        var findMe = punishRepository.findByInfraction(punishmentRequest.getInfraction());

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

        PunishmentResponse punishmentResponse = sendEmailBasedOnType(punishment);

        emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
        emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

//        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

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

    public PunishmentResponse closePunishment(String infractionName, String studentEmail) throws ResourceNotFoundException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        List<Punishment> find = punishRepository.findByStatus("OPEN");
        Stream<Punishment> filteredStudents = find.stream().filter(x -> x.getStudent().getStudentEmail().equals(studentEmail));
//        Stream<Punishment> filteredInfraction = filteredStudents.filter(x -> x.getInfraction().getInfractionName().equals(infractionName));

        Punishment findMe = filteredStudents.toList().get(0);
        findMe.setStatus("CLOSED");
        System.out.println(findMe);
        if (findMe != null) {
            PunishmentResponse punishmentResponse = new PunishmentResponse();
            punishmentResponse.setPunishment(findMe);
            punishmentResponse.setMessage(" Hello," +
                    " This is to inform you that " + findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName() +
                    " has completed "
                    + findMe.getInfraction().getInfractionAssign() + " for the infraction: " + findMe.getInfraction().getInfractionName() + ". If you have any questions you may contact the school's main office." +
                    "This is an automated message DO NOT REPLY to this message.");
            punishmentResponse.setSubject("REP " + findMe.getPunishmentId() + " for " + findMe.getStudent().getFirstName() + " " + findMe.getStudent().getLastName() + " CLOSED");
            punishmentResponse.setParentToEmail(findMe.getStudent().getParentEmail());
            punishmentResponse.setStudentToEmail(findMe.getStudent().getStudentEmail());

            emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

//            Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//                    new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

//        int closedPunishments = punishment.getClosedInfraction();
//        punishment.setClosedInfraction(closedPunishments + 1);

            return punishmentResponse;
        } else {
            throw new ResourceNotFoundException("That infraction does not exist");
        }

    }

    //  -------------------CREATE PUNISHMENT WITH GOOGLE FORM SUBMISSION------------------------

    public PunishmentResponse createNewPunishForm(PunishmentFormRequest formRequest) {
        System.out.println(formRequest);
//        Twilio.init(secretClient.getSecret("TWILIO-ACCOUNT-SID").toString(), secretClient.getSecret("TWILIO-AUTH-TOKEN").toString());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        Student findMe = studentRepository.findByStudentEmail(formRequest.getStudentEmail());
        List<Punishment> closedPunishments = punishRepository.findByStudentStudentEmailAndInfractionInfractionNameAndStatus(formRequest.getStudentEmail(), formRequest.getInfractionName(), "CLOSED");
        List<Integer> closedTimes = new ArrayList<>();
        for(Punishment punishment : closedPunishments) {
            closedTimes.add(punishment.getClosedTimes());
        }

        String level = levelCheck(closedTimes);
        System.out.println(level);

        Infraction findInf = infractionRepository.findByInfractionNameAndInfractionLevel(formRequest.getInfractionName(), level);
        findInf.setInfractionDescription(formRequest.getInfractionDescription());

        Punishment punishment = new Punishment();
        punishment.setStudent(findMe);
        punishment.setInfraction(findInf);
        punishment.setClassPeriod(formRequest.getInfractionPeriod());
        punishment.setPunishmentId(UUID.randomUUID().toString());
        punishment.setTimeCreated(now);

        List<Punishment> findOpen = punishRepository.findByStudentStudentEmailAndInfractionInfractionNameAndStatus(punishment.getStudent().getStudentEmail(),
                punishment.getInfraction().getInfractionName(), "OPEN");
        System.out.println(findOpen);

        if (findOpen.isEmpty()) {
            punishment.setStatus("OPEN");
            punishRepository.save(punishment);

            PunishmentResponse punishmentResponse = sendEmailBasedOnType(punishment);

            emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;


        } else {
            punishment.setStatus("CFR");
            punishRepository.save(punishment);

            PunishmentResponse punishmentResponse = sendCFREmailBasedOnType(punishment);

            emailService.sendEmail(punishmentResponse.getParentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());
            emailService.sendEmail(punishmentResponse.getStudentToEmail(), punishmentResponse.getSubject(), punishmentResponse.getMessage());

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
    }

    private static String levelCheck(List<Integer> levels) {
        int level = 1;
        for (Integer lev : levels) {
            if (lev >= level) {
                level = lev + 1;
            }
            return String.valueOf(level);
        }
        return String.valueOf(level);
    }

    private static PunishmentResponse sendEmailBasedOnType(Punishment punishment) {
        PunishmentResponse punishmentResponse = new PunishmentResponse();
        punishmentResponse.setParentToEmail(punishment.getStudent().getParentEmail());
        punishmentResponse.setStudentToEmail(punishment.getStudent().getStudentEmail());
        punishmentResponse.setPunishment(punishment);
        punishmentResponse.setSubject("Burke High School referral for " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName());
        if(punishment.getInfraction().getInfractionName().equals("Tardy")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child," + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for being " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "As a result they have received the following assignment, "
                    + punishment.getInfraction().getInfractionAssign() + " and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential." +
                    " " +
                    "Do not respond to this message. Please contact the school/teacher if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if(punishment.getInfraction().getInfractionName().equals("Unauthorized Device/Cell Phone")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child," + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for using an " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "As a result they have received the following assignment, "
                    + punishment.getInfraction().getInfractionAssign() + " and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential." +
                    " " +
                    "Do not respond to this message. Please contact the school/teacher if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");

            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if(punishment.getInfraction().getInfractionName().equals("Disruptive Behavior")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child," + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "As a result they have received the following assignment, "
                    + punishment.getInfraction().getInfractionAssign() + " and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential." +
                    " " +
                    "Do not respond to this message. Please contact the school/teacher if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if(punishment.getInfraction().getInfractionName().equals("Horseplay")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child," + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "As a result they have received the following assignment, "
                    + punishment.getInfraction().getInfractionAssign() + " and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential." +
                    " " +
                    "Do not respond to this message. Please contact the school/teacher if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if(punishment.getInfraction().getInfractionName().equals("Dress Code")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child," + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for a violation of the school " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "As a result they have received the following assignment, "
                    + punishment.getInfraction().getInfractionAssign() + " and lunch detention for tomorrow. The goal of the assignment is to provide " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " with information about the infraction and ways to make beneficial decisions in the future. If " + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() + " completes the assignment prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive the assignment. We appreciate your assistance and will continue to work to help your child reach their full potential." +
                    " " +
                    "Do not respond to this message. Please contact the school/teacher if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if(punishment.getInfraction().getInfractionName().equals("Failure to Complete Work")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child," + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has received Offense # " + punishment.getInfraction().getInfractionLevel() + " for " + punishment.getInfraction().getInfractionName() +
                    " " + "As a result they must complete the following assignment, "
                    + punishment.getInfraction().getInfractionDescription() + " during lunch detention tomorrow. If the assignment is completed prior to lunch tomorrow they will no longer be required to attend lunch detention. We will send out an email confirming the completion of the assignment when we receive confirmation from the teacher the assignment is done. We believe that consistency in completing assignments will have a profound impact on their grade and understanding. Please continue to encourage them to finish the assignment. " +
                    " " +
                    "Do not respond to this message. Please contact the school/teacher if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }

        return punishmentResponse;
    }

    private static PunishmentResponse sendCFREmailBasedOnType(Punishment punishment) {
        PunishmentResponse punishmentResponse = new PunishmentResponse();
        punishmentResponse.setParentToEmail(punishment.getStudent().getParentEmail());
        punishmentResponse.setStudentToEmail(punishment.getStudent().getStudentEmail());
        punishmentResponse.setPunishment(punishment);
        if (punishment.getInfraction().getInfractionName().equals("Tardy")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child," + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for being " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "They currently have an Open assignment they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening." +
                    "Do not respond to this message. Please contact the school/teacher if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (punishment.getInfraction().getInfractionName().equals("Unauthorized Device/Cell Phone")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child," + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for using an " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "They currently have an Open assignment they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening." +
                    "Do not respond to this message. Please contact the school/teacher if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (punishment.getInfraction().getInfractionName().equals("Disruptive Behavior")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child," + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "They currently have an Open assignment they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening." +
                    "Do not respond to this message. Please contact the school/teacher if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (punishment.getInfraction().getInfractionName().equals("Horseplay")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child," + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "They currently have an Open assignment they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening." +
                    "Do not respond to this message. Please contact the school/teacher if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (punishment.getInfraction().getInfractionName().equals("Dress Code")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child," + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has been written up for a violation of the school " + punishment.getInfraction().getInfractionName() + ". " +
                    " " + "They currently have an Open assignment they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening." +
                    "Do not respond to this message. Please contact the school/teacher if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        if (punishment.getInfraction().getInfractionName().equals("Failure to Complete Work")) {
            punishmentResponse.setMessage(" Hello," +
                    " Your child," + punishment.getStudent().getFirstName() + " " + punishment.getStudent().getLastName() +
                    " has received Offense # " + punishment.getInfraction().getInfractionLevel() + " for " + punishment.getInfraction().getInfractionName() +
                    " " + "They currently have an Open assignment they need to complete for this type of offense so they will not be receiving another. Record of this offense will be kept and this email is to inform you of this happening." +
                    "Do not respond to this message. Please contact the school/teacher if there are any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment or if you have any questions or concerns.");
            //        Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
            //                new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
            return punishmentResponse;
        }
        return punishmentResponse;
    }
}
