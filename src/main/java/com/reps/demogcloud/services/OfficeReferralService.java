package com.reps.demogcloud.services;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.data.OfficeReferralRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.data.filters.CustomFilters;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.officeReferral.OfficeReferralCloseRequest;
import com.reps.demogcloud.models.officeReferral.OfficeReferralRequest;
import com.reps.demogcloud.models.officeReferral.OfficeReferralResponse;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.services.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OfficeReferralService {

    private final StudentRepository studentRepository;
    private final SchoolRepository schoolRepository;
    private final OfficeReferralRepository officeReferralRepository;
    private final EmailService emailService;
    private final CustomFilters customFilters;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public List<OfficeReferral> createNewAdminReferralBulk(List<OfficeReferralRequest> officeReferralRequests) {
        List<OfficeReferral> punishmentResponse = new ArrayList<>();
        for(OfficeReferralRequest officeReferralRequest : officeReferralRequests) {
            punishmentResponse.add(createNewOfficeReferral(officeReferralRequest));
        } return  punishmentResponse;
    }

    public OfficeReferral createNewOfficeReferral(OfficeReferralRequest officeReferralRequest) {
        System.out.println(officeReferralRequest + " THE REQUEST ");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
        LocalDate now = LocalDate.now();

        Student findMe = studentRepository.findByStudentEmailIgnoreCase(officeReferralRequest.getStudentEmail());
        School ourSchool = schoolRepository.findSchoolBySchoolName(findMe.getSchool());

        OfficeReferral request = new OfficeReferral();
        request.setAdminEmail(findMe.getAdminEmail());
        request.setStudentEmail(findMe.getStudentEmail());
        request.setTeacherEmail(officeReferralRequest.getTeacherEmail());
        request.setClassPeriod(officeReferralRequest.getClassPeriod());
        request.setSchoolName(ourSchool.getSchoolName());
        request.setStatus("OPEN");
        request.setTimeCreated(now);
        request.setReferralDescription(officeReferralRequest.getReferralDescription());
        request.setInfractionLevel("4");
        request.setReferralCode(officeReferralRequest.getReferralCode());

        return officeReferralRepository.save(request);
    }

    public OfficeReferral updateMapIndex(String id, int index) {
        OfficeReferral referral = officeReferralRepository.findByOfficeReferralId(id);
        if(referral !=null){
            referral.setMapIndex(index);
            officeReferralRepository.save(referral);
            return referral;

        }else{
            throw new ResourceNotFoundException("No Punishment with Id " +id +" number exist");

        }


    }

    public OfficeReferral rejectAnswers(String referralId, String description) throws MessagingException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        //get punishment
        OfficeReferral referral = officeReferralRepository.findByOfficeReferralId(referralId);
        Student studentReject = studentRepository.findByStudentEmailIgnoreCase(referral.getStudentEmail());
        ArrayList<String> infractionContext = referral.getReferralDescription();
        String resetContext = infractionContext.get(0);
        List<String> contextToStore = infractionContext.subList(1, infractionContext.size());

        ArrayList<String> studentAnswer = new ArrayList<>();
        studentAnswer.add(resetContext);
        Date currentDate = new Date();
        if(referral.getAnswerHistory() !=null){
            Map<Date,List<String>> answers = referral.getAnswerHistory();
            answers.put(currentDate,new ArrayList<>(contextToStore));
        }else {
            referral.setAnswerHistory(currentDate, new ArrayList<>(contextToStore));

        }
        referral.setReferralDescription(studentAnswer);

        referral.setStatus("OPEN");

        String message =  "Hello, \n" +
                "Unfortunately your answers provided to the open ended questions were unacceptable and you must resubmit with acceptable answers to close this out. A description of why your answers were not accepted is:  \n" +
                " \n" +
                contextToStore + " \n" +
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.";

        String subject = "Level Three Answers not accepted for " + studentReject.getFirstName() + " " + studentReject.getLastName();

        emailService.sendPtsEmail(studentReject.getParentEmail(),
                referral.getTeacherEmail(),
                studentReject.getStudentEmail(),
                subject,
                message);
        referral.setMapIndex(0);
        officeReferralRepository.save(referral);

        return referral;
    }

    public List<OfficeReferral> findAll() {
        return officeReferralRepository.findAll();
    }

    // Methods that Need Global Filters Due for schools
    public List<OfficeReferral> findAllSchool() {
        return customFilters.FetchOfficeReferralsByIsArchivedAndSchool(false);    }

    public List<OfficeReferral> findByAdminEmail(String adminEmail) {
        return officeReferralRepository.findByAdminEmail(adminEmail);
    }

    public List<OfficeReferral> findByStudentEmail(String studentEmail) {
        return officeReferralRepository.findByStudentEmailIgnoreCase(studentEmail);
    }

    public List<OfficeReferral> findByLoggedInStudent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var findMe = studentRepository.findByStudentEmailIgnoreCase(authentication.getName());

        return officeReferralRepository.findByStudentEmailIgnoreCase(findMe.getStudentEmail());
    }

    public OfficeReferral findByReferralId(String referralId) throws ResourceNotFoundException {
        var fetchData = officeReferralRepository.findByOfficeReferralId(referralId);
        if (fetchData == null) {
            throw new ResourceNotFoundException("No referrals with that ID exist");
        }

        if(fetchData.isArchived()){
            throw new ResourceNotFoundException("No referrals with that ID exist");

        }

        logger.debug(String.valueOf(fetchData));
        return fetchData;
    }

//    public OfficeReferralResponse closeReferral(String infractionName, String studentEmail, List<StudentAnswer> studentAnswers) throws ResourceNotFoundException, MessagingException {
////        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
//        List<OfficeReferral> fetchPunishmentData = officeReferralRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(studentEmail,
//                infractionName, "OPEN");
//
//        var findOpen = fetchPunishmentData.stream()
//                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
//                .toList();  // Collect the filtered punishments into a list
//
//
//
//        Punishment findMe = null;
//        if (!findOpen.isEmpty()) {
//            findMe = findOpen.get(0);
//
//        } else {
//            // Handle the case where findOpen is empty
//            throw new ResourceNotFoundException("No open punishments found for the given criteria.");
//        }
//
//        Student studentClose = studentRepository.findByStudentEmailIgnoreCase(findMe.getStudentEmail());
//        Infraction infractionClose = infractionRepository.findByInfractionId(findMe.getInfractionId());
//
//        if(!studentAnswers.isEmpty()) {
//            System.out.println(studentAnswers + " Not Null");
//            ArrayList<String> answers = findMe.getInfractionDescription();
//            for (StudentAnswer answer:studentAnswers
//            ) {
//                answers.add(answer.toString());
//            }
//
//            findMe.setInfractionDescription(answers);
//            findMe.setStatus("PENDING");
//
//            punishRepository.save(findMe);
//
//            PunishmentResponse response = new PunishmentResponse();
//            response.setPunishment(findMe);
//            return response;
//        } else {
//            findMe.setStatus("CLOSED");
//            findMe.setClosedTimes(findMe.getClosedTimes() + 1);
//            findMe.setTimeClosed(LocalDate.now());
//            punishRepository.save(findMe);
//            PunishmentResponse punishmentResponse = new PunishmentResponse();
//            punishmentResponse.setPunishment(findMe);
//            punishmentResponse.setMessage(" Hello, \n" +
//                    " Your child, " + studentClose.getFirstName() + " " + studentClose.getLastName() +
//                    " has successfully completed the assignment given to them in response to the infraction: " + infractionClose.getInfractionName() + ". As a result, no further action is required. Thank you for your support during this process and we appreciate " +
//                    studentClose.getFirstName() + " " + studentClose.getLastName() + "'s effort in completing the assignment. \n" +
//                    "Do not respond to this message. Call the school at (843) 579-4815 or email the teacher directly at " + findMe.getTeacherEmail() + " if you have any questions or concerns.");
//            punishmentResponse.setSubject("Burke High School referral for " + studentClose.getFirstName() + " " + studentClose.getLastName());
//            punishmentResponse.setParentToEmail(studentClose.getParentEmail());
//            punishmentResponse.setStudentToEmail(studentClose.getStudentEmail());
//            punishmentResponse.setTeacherToEmail(findMe.getTeacherEmail());
//
//            emailService.sendPtsEmail(punishmentResponse.getParentToEmail(),
//                    punishmentResponse.getTeacherToEmail(),
//                    punishmentResponse.getStudentToEmail(),
//                    punishmentResponse.getSubject(),
//                    punishmentResponse.getMessage());
//
////            Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
////                    new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();
//
//            return punishmentResponse;
//        }}

    public OfficeReferralResponse closeByReferralId(OfficeReferralCloseRequest request) throws ResourceNotFoundException, MessagingException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        OfficeReferral findMe = officeReferralRepository.findByOfficeReferralId(request.getId());

        findMe.setStatus("CLOSED");
        findMe.setTimeClosed(LocalDate.now());

        // Add comment if one is there
        if (!request.getComment().isEmpty()) {
            ArrayList<String> description = new ArrayList<>();
            description.addAll(findMe.getReferralDescription());
            description.add(request.getComment());
            findMe.setReferralDescription(description);
        }

        officeReferralRepository.save(findMe);
        if (findMe != null) {
            OfficeReferralResponse referralResponse = new OfficeReferralResponse();
            referralResponse.setOfficeReferral(findMe);
//            var message = " Hello," +
//                    " Your child, " + studentClose.getFirstName() + " " + studentClose.getLastName() +
//                    " has successfully completed the assignment given to them in response to the infraction: " + infractionClose.getInfractionName() + ". As a result, no further action is required. Thank you for your support during this process and we appreciate " +
//                    studentClose.getFirstName() + " " + studentClose.getLastName() + "'s effort in completing the assignment. \n" +
//                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. You can also call the school directly at (843) 579-4815.");
//            var subject =  studentClose.getSchool() + " High School assignment completion for " + studentClose.getFirstName() + " " + studentClose.getLastName());
//
//            emailService.sendPtsEmail(studentClose.getParentEmail(),
//                    findMe.getTeacherEmail(),
//                    studentClose.getStudentEmail(),
//                    subject,
//                    message);

//            Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//                    new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return referralResponse;
        } else {
            throw new ResourceNotFoundException("That referral does not exist");
        }
    }

    public List<OfficeReferral> updateDescriptions() {
        List<OfficeReferral> all = officeReferralRepository.findAll();
        List<OfficeReferral> saved = new ArrayList<>();
        for (OfficeReferral referral : all) {
            if (referral.getReferralDescription().size() > 1) {
                referral.getReferralDescription().remove(0);
                officeReferralRepository.save(referral);
                saved.add(referral);
            }
        }
        return saved;
    }

    public OfficeReferralResponse submitByReferralId(String referralId) throws ResourceNotFoundException, MessagingException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        OfficeReferral findMe = officeReferralRepository.findByOfficeReferralId(referralId);

        findMe.setStatus("PENDING");
        findMe.setTimeClosed(LocalDate.now());
        officeReferralRepository.save(findMe);
        if (findMe != null) {
            OfficeReferralResponse referralResponse = new OfficeReferralResponse();
            referralResponse.setOfficeReferral(findMe);
//            var message = " Hello," +
//                    " Your child, " + studentClose.getFirstName() + " " + studentClose.getLastName() +
//                    " has successfully completed the assignment given to them in response to their office referral for " + findMe.getInfractionDescription() + ". As a result, no further action is required. Thank you for your support during this process and we appreciate " +
//                    studentClose.getFirstName() + " " + studentClose.getLastName() + "'s effort in completing the assignment. \n" +
//                    "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. You can also call the school directly at (843) 579-4815.");
//            var subject =  studentClose.getSchool() + " High School assignment completion for " + studentClose.getFirstName() + " " + studentClose.getLastName());
//
//            emailService.sendPtsEmail(studentClose.getParentEmail(),
//                    findMe.getTeacherEmail(),
//                    studentClose.getStudentEmail(),
//                    subject,
//                    message);

//            Message.creator(new PhoneNumber(punishmentResponse.getPunishment().getStudent().getParentPhoneNumber()),
//                    new PhoneNumber("+18437900073"), punishmentResponse.getMessage()).create();

            return referralResponse;
        } else {
            throw new ResourceNotFoundException("That referral does not exist");
        }
    }
}
