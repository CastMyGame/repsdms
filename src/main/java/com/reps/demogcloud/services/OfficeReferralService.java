package com.reps.demogcloud.services;

import com.reps.demogcloud.data.OfficeReferralRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.officeReferral.OfficeReferralCloseRequest;
import com.reps.demogcloud.models.officeReferral.OfficeReferralRequest;
import com.reps.demogcloud.models.officeReferral.OfficeReferralResponse;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.utils.OfficeReferralUtils;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final OfficeReferralUtils officeReferralUtils;
    private final MongoTemplate mongoTemplate;

    public List<OfficeReferral> createNewAdminReferralBulk(List<OfficeReferralRequest> officeReferralRequests) {
        List<OfficeReferral> punishmentResponse = new ArrayList<>();
        for (OfficeReferralRequest officeReferralRequest : officeReferralRequests) {
            punishmentResponse.add(createNewOfficeReferral(officeReferralRequest));
        }
        return punishmentResponse;
    }

    public OfficeReferral createNewOfficeReferral(OfficeReferralRequest officeReferralRequest) {
        LocalDate now = LocalDate.now();

        Student findMe = studentRepository.findByStudentEmailIgnoreCase(officeReferralRequest.getStudentEmail());
        if (findMe == null) {
            throw new IllegalStateException("Student not found: " + officeReferralRequest.getStudentEmail());
        }

        Optional<School> ourSchoolOpt = schoolRepository.findBySchoolNameIgnoreCase(findMe.getSchool());

        School ourSchool = ourSchoolOpt.orElseThrow(() ->
                new IllegalStateException("School not found: " + findMe.getSchool())
        );

        OfficeReferral request = new OfficeReferral();
        request.setAdminEmail(findMe.getAdminEmail());
        request.setStudentEmail(findMe.getStudentEmail());
        request.setTeacherEmail(officeReferralRequest.getTeacherEmail());
        request.setClassPeriod(officeReferralRequest.getClassPeriod());
        request.setSchool(ourSchool.getSchoolName());
        request.setStatus("OPEN");
        request.setTimeCreated(now);
        request.setReferralDescription(officeReferralRequest.getReferralDescription());
        request.setInfractionLevel("4");
        request.setReferralCode(officeReferralRequest.getReferralCode());

        return officeReferralRepository.save(request);
    }

    public OfficeReferral updateMapIndex(String id, int index) {
        OfficeReferral referral = officeReferralRepository.findByOfficeReferralId(id);
        if (referral != null) {
            referral.setMapIndex(index);
            officeReferralRepository.save(referral);
            return referral;
        } else {
            throw new ResourceNotFoundException("No Punishment with Id " + id + " number exist");
        }
    }

    public OfficeReferral rejectAnswers(String referralId) throws MessagingException {
        OfficeReferral referral = officeReferralRepository.findByOfficeReferralId(referralId);
        if (referral == null) {
            throw new ResourceNotFoundException("Office Referral not found for ID: " + referralId);
        }

        Student studentReject = studentRepository.findByStudentEmailIgnoreCase(referral.getStudentEmail());
        if (studentReject == null) {
            throw new IllegalStateException("Student not found: " + referral.getStudentEmail());
        }

        List<String> infractionContext = referral.getReferralDescription();
        if (infractionContext == null || infractionContext.isEmpty()) {
            throw new IllegalStateException("Referral description is missing for referral: " + referralId);
        }

        String resetContext = infractionContext.get(0);
        List<String> contextToStore = infractionContext.size() > 1
                ? new ArrayList<>(infractionContext.subList(1, infractionContext.size()))
                : new ArrayList<>();

        List<String> studentAnswer = new ArrayList<>();
        studentAnswer.add(resetContext);
        Date currentDate = new Date();

        if (referral.getAnswerHistory() != null) {
            Map<Date, List<String>> answers = referral.getAnswerHistory();
            answers.put(currentDate, new ArrayList<>(contextToStore));
        } else {
            referral.setAnswerHistory(currentDate, new ArrayList<>(contextToStore));
        }

        referral.setReferralDescription(studentAnswer);
        referral.setStatus("OPEN");

        String message = "Hello, \n" +
                "Unfortunately your answers provided to the open ended questions were unacceptable and you must resubmit with acceptable answers to close this out. A description of why your answers were not accepted is:  \n" +
                " \n" +
                contextToStore + " \n" +
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment.";

        String subject = "Level Three Answers not accepted for " + studentReject.getFirstName() + " " + studentReject.getLastName();

        emailService.sendPtsEmail(
                studentReject.getParentEmail(),
                referral.getTeacherEmail(),
                studentReject.getStudentEmail(),
                message,
                subject,
                studentReject.getPreferredLanguage()
        );

        referral.setMapIndex(0);
        officeReferralRepository.save(referral);

        return referral;
    }

    public List<OfficeReferral> findAll() {
        return officeReferralRepository.findAll();
    }

    // Methods that Need Global Filters Due for schools
    public List<OfficeReferral> findAllSchool() {
        return officeReferralUtils.FetchOfficeReferralsByArchivedAndSchool(false);
    }

    public List<OfficeReferral> findByAdminEmail(String adminEmail) {
        return officeReferralRepository.findByAdminEmail(adminEmail);
    }

    public List<OfficeReferral> findByStudentEmail(String studentEmail) {
        return officeReferralRepository.findByStudentEmailIgnoreCase(studentEmail);
    }

    public List<OfficeReferral> findByLoggedInStudent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("No authenticated user found");
        }

        Student findMe = studentRepository.findByStudentEmailIgnoreCase(authentication.getName());
        if (findMe == null) {
            throw new IllegalStateException("Student not found: " + authentication.getName());
        }

        return officeReferralRepository.findByStudentEmailIgnoreCase(findMe.getStudentEmail());
    }

    public OfficeReferral findByReferralId(String referralId) throws ResourceNotFoundException {
        OfficeReferral fetchData = officeReferralRepository.findByOfficeReferralId(referralId);
        if (fetchData == null || fetchData.isArchived()) {
            throw new ResourceNotFoundException("No referrals with that ID exist");
        }

        log.debug(String.valueOf(fetchData));
        return fetchData;
    }

    public OfficeReferralResponse closeByReferralId(OfficeReferralCloseRequest request) throws ResourceNotFoundException {
        OfficeReferral findMe = officeReferralRepository.findByOfficeReferralId(request.getId());

        if (findMe == null) {
            throw new ResourceNotFoundException("Office Referral not found for ID: " + request.getId());
        }

        findMe.setStatus("CLOSED");
        findMe.setTimeClosed(LocalDate.now());

        // Ensure referralDescription is initialized before modifying it
        if (findMe.getReferralDescription() == null) {
            log.debug("Referral Description is Null for referral {}", findMe.getOfficeReferralId());
            findMe.setReferralDescription(new ArrayList<>());
        }

        // Add comment if one is there
        if (request.getComment() != null && !request.getComment().isEmpty()) {
            List<String> description = new ArrayList<>(findMe.getReferralDescription());
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
            if (referral.getReferralDescription() != null && referral.getReferralDescription().size() > 1) {
                referral.getReferralDescription().remove(0);
                officeReferralRepository.save(referral);
                saved.add(referral);
            }
        }
        return saved;
    }

    public OfficeReferralResponse submitByReferralId(String referralId) throws ResourceNotFoundException {
        OfficeReferral findMe = officeReferralRepository.findByOfficeReferralId(referralId);

        if (findMe == null) {
            throw new ResourceNotFoundException("Office Referral not found for ID: " + referralId);
        }

        findMe.setStatus("PENDING");
        findMe.setTimeClosed(LocalDate.now());
        officeReferralRepository.save(findMe);
        OfficeReferralResponse referralResponse = new OfficeReferralResponse();
        referralResponse.setOfficeReferral(findMe);

        return referralResponse;
    }

    public List<TeacherDTO> getTeacherResponse(List<OfficeReferral> referralList) {
        if (referralList == null || referralList.isEmpty()) {
            return new ArrayList<>();
        }

        // Extract student emails from the given punishmentList
        List<String> studentEmails = referralList.stream()
                .map(OfficeReferral::getStudentEmail)
                .collect(Collectors.toList());

        Aggregation aggregation = newAggregation(
                match(Criteria.where("studentEmail").in(studentEmails)),
                lookup("students", "studentEmail", "studentEmail", "studentInfo"),
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