package com.reps.demogcloud.services;

import com.reps.demogcloud.data.OfficeReferralRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.data.filters.CustomFilters;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.officeReferral.OfficeReferralCloseRequest;
import com.reps.demogcloud.models.officeReferral.OfficeReferralRequest;
import com.reps.demogcloud.models.officeReferral.OfficeReferralResponse;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

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

    private final MongoTemplate mongoTemplate;

    public List<OfficeReferral> createNewAdminReferralBulk(List<OfficeReferralRequest> officeReferralRequests) {
        List<OfficeReferral> punishmentResponse = new ArrayList<>();
        for(OfficeReferralRequest officeReferralRequest : officeReferralRequests) {
            punishmentResponse.add(createNewOfficeReferral(officeReferralRequest));
        } return  punishmentResponse;
    }

    public OfficeReferral createNewOfficeReferral(OfficeReferralRequest officeReferralRequest) {
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

    public OfficeReferral rejectAnswers(String referralId) throws MessagingException {
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
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment.";

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

    public OfficeReferralResponse closeByReferralId(OfficeReferralCloseRequest request) throws ResourceNotFoundException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        OfficeReferral findMe = officeReferralRepository.findByOfficeReferralId(request.getId());

        if (findMe == null) {
            throw new ResourceNotFoundException("Office Referral not found for ID: " + request.getId());
        }

        findMe.setStatus("CLOSED");
        findMe.setTimeClosed(LocalDate.now());

        // Ensure referralDescription is initialized before modifying it
        if (findMe.getReferralDescription() == null) {
            System.out.println("Referral Description is Null for referral " + findMe.getOfficeReferralId());
            findMe.setReferralDescription(new ArrayList<>()); // Initialize if null
        }

        // Add comment if one is there
        if (!request.getComment().isEmpty()) {
            ArrayList<String> description = new ArrayList<>(findMe.getReferralDescription());
            description.add(request.getComment());
            findMe.setReferralDescription(description);
        }

        officeReferralRepository.save(findMe);
        OfficeReferralResponse referralResponse = new OfficeReferralResponse();
        referralResponse.setOfficeReferral(findMe);

        return referralResponse;
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

    public OfficeReferralResponse submitByReferralId(String referralId) throws ResourceNotFoundException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        OfficeReferral findMe = officeReferralRepository.findByOfficeReferralId(referralId);

        findMe.setStatus("PENDING");
        findMe.setTimeClosed(LocalDate.now());
        officeReferralRepository.save(findMe);
        OfficeReferralResponse referralResponse = new OfficeReferralResponse();
        referralResponse.setOfficeReferral(findMe);

        return referralResponse;
    }

    public List<TeacherDTO> getTeacherResponse(List<OfficeReferral> referralList) {
        // Extract student emails from the given punishmentList
        List<String> studentEmails = referralList.stream()
                .map(OfficeReferral::getStudentEmail)
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
                        .and("timeClosed").as("timeClosed")
                        .and("referralDescription").as("infractionDescription")
                        .and("classPeriod").as("classPeriod")
                        .and("teacherEmail").as("teacherEmail")
                        .and("status").as("status")
                        .and("infractionLevel").as("infractionLevel")
                        .andExclude("_id")
        );

        AggregationResults<TeacherDTO> results =
                mongoTemplate.aggregate(aggregation, "OfficeReferrals", TeacherDTO.class);

        return results.getMappedResults();
    }
}
