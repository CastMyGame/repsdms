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
import com.reps.demogcloud.services.email.EmailSenderService;
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
    private final UserContextService userContextService;
    private final EmailSenderService emailSenderService;
    private final com.reps.demogcloud.services.email.RequiredNotificationService requiredNotifications;

    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalWorkflowAccess(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess value){canonical=value;}
    private List<OfficeReferral> scoped(Criteria criteria){return mongoTemplate.find(canonical.query().addCriteria(criteria),OfficeReferral.class).stream().filter(r->canonical.canReadRecord(r,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.OFFICE)).toList();}
    private OfficeReferral managedChange(String id, String event, String comment) {
        OfficeReferral referral=canonical==null?officeReferralRepository.findByOfficeReferralId(id):canonical.record(id,OfficeReferral.class,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.OFFICE);
        if(referral==null) throw new ResourceNotFoundException("Case not found");
        if(("CLOSED".equals(event) && "CLOSED".equals(referral.getStatus()))
                || ("REJECTED".equals(event) && "OPEN".equals(referral.getStatus()))
                || ("SUBMITTED".equals(event) && "PENDING".equals(referral.getStatus()))) {
            requiredNotifications.requireActionAccess(referral,event); return referral;
        }
        if("SUBMITTED".equals(event) && "CLOSED".equals(referral.getStatus())) throw new IllegalStateException("Closed cases cannot be submitted again");
        if(canonical!=null)canonical.action(referral,event);
        if(!requiredNotifications.prepare(referral,event)) return referral;
        if("CLOSED".equals(event)) {
            referral.setStatus("CLOSED"); referral.setTimeClosed(LocalDate.now());
            if(comment!=null && !comment.isBlank()) {
                var description=new ArrayList<>(referral.getReferralDescription()==null?List.<String>of():referral.getReferralDescription());
                description.add(comment); referral.setReferralDescription(description);
            }
        } else if("SUBMITTED".equals(event)) { referral.setStatus("PENDING"); referral.setTimeClosed(LocalDate.now()); }
        else if("REJECTED".equals(event)) {
            var description=referral.getReferralDescription();
            if(description==null || description.isEmpty()) throw new IllegalArgumentException("Submitted answers are missing");
            referral.setAnswerHistory(new Date(),new ArrayList<>(description.subList(1,description.size())));
            referral.setReferralDescription(new ArrayList<>(List.of(description.get(0)))); referral.setStatus("OPEN"); referral.setMapIndex(0);
        }
        requiredNotifications.captureContent(referral);
        return officeReferralRepository.save(referral);
    }
    public List<OfficeReferral> createNewAdminReferralBulk(List<OfficeReferralRequest> officeReferralRequests) {
        if(canonical!=null)return canonical.atomic(()->officeReferralRequests.stream().map(this::prepareCreation).toList().stream().map(this::persistCreation).toList());
        List<OfficeReferral> punishmentResponse = new ArrayList<>();
        // Prepare the whole batch before persisting any referral or notification.
        List<OfficeReferral> prepared = officeReferralRequests.stream().map(this::prepareCreation).toList();
        for (OfficeReferral referral : prepared) {
            punishmentResponse.add(persistCreation(referral));
        }
        return punishmentResponse;
    }

    public OfficeReferral createNewOfficeReferral(OfficeReferralRequest officeReferralRequest) {
        if(canonical!=null)return canonical.atomic(()->persistCreation(prepareCreation(officeReferralRequest)));
        return persistCreation(prepareCreation(officeReferralRequest));
    }

    private OfficeReferral prepareCreation(OfficeReferralRequest officeReferralRequest) {
        LocalDate now = LocalDate.now();

        Student findMe = canonical==null?studentRepository.findByStudentEmailIgnoreCase(officeReferralRequest.getStudentEmail()):canonical.student(officeReferralRequest.getStudentEmail());
        if (findMe == null) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        String actor = userContextService.getCurrentStaffEmail();
        userContextService.requireStaffInStudentSchool(actor, findMe);
        if (!userContextService.hasIndependentStudentAccess(actor, findMe))
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        if(canonical!=null){canonical.staff();canonical.prepare(com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.OFFICE,findMe);if(officeReferralRequest.getTeacherEmail()!=null && !officeReferralRequest.getTeacherEmail().isBlank() && !actor.equalsIgnoreCase(officeReferralRequest.getTeacherEmail()))throw new org.springframework.security.access.AccessDeniedException("Access denied");}
        officeReferralRequest.setTeacherEmail(actor);

        Optional<School> ourSchoolOpt = canonical==null?schoolRepository.findBySchoolNameIgnoreCase(findMe.getSchool()):Optional.of(canonical.school());

        School ourSchool = ourSchoolOpt.orElseThrow(() ->
                new IllegalStateException("School not found: " + findMe.getSchool())
        );

        String managedId = null;
        if(canonical!=null || requiredNotifications.isEnabled()) {
            managedId = requiredNotifications.creationId("office", findMe.getStudentEmail(),java.util.Arrays.asList(
                    officeReferralRequest.getReferralCode(),officeReferralRequest.getClassPeriod(),officeReferralRequest.getReferralDescription(),officeReferralRequest.getCurrency()));
        }
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

        if (managedId != null) request.setOfficeReferralId(managedId);
        if(canonical!=null){var existing=requiredNotifications.existingCreation(request.getOfficeReferralId(),OfficeReferral.class);if(existing!=null)return existing;canonical.bindNew(request);}
        return request;
    }

    private OfficeReferral persistCreation(OfficeReferral request) {
        if (canonical!=null || requiredNotifications.isEnabled()) {
            OfficeReferral existing = requiredNotifications.existingCreation(request.getOfficeReferralId(), OfficeReferral.class);
            if (existing != null) return existing;
            return (OfficeReferral) requiredNotifications.saveCreated(request);
        }
        return officeReferralRepository.save(request);
    }

    public OfficeReferral updateMapIndex(String id, int index) {
        throw new IllegalArgumentException("Office referrals are handled by the principal and have no student assignment");
    }

    public OfficeReferral rejectAnswers(String referralId) throws MessagingException {
        throw new IllegalArgumentException("Office referrals have no student assignment to reject; review the referral with the principal");
    }

    public List<OfficeReferral> findAll() {
        return canonical==null?officeReferralRepository.findAll():scoped(new Criteria());
    }

    // Methods that Need Global Filters Due for schools
    public List<OfficeReferral> findAllSchool() {
        return officeReferralUtils.fetchOfficeReferralsByArchivedAndSchool(false);
    }

    public List<OfficeReferral> findByAdminEmail(String adminEmail) {
        return canonical==null?officeReferralRepository.findByAdminEmail(adminEmail):scoped(Criteria.where("adminEmail").regex(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess.exact(adminEmail)));
    }

    public List<OfficeReferral> findByStudentEmail(String studentEmail) {
        return canonical==null?officeReferralRepository.findByStudentEmailIgnoreCase(studentEmail):scoped(Criteria.where("studentEmail").regex(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess.exact(studentEmail)));
    }

    public List<OfficeReferral> findByLoggedInStudent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("No authenticated user found");
        }

        Student findMe = canonical==null?studentRepository.findByStudentEmailIgnoreCase(authentication.getName()):canonical.student(authentication.getName());
        if (findMe == null) {
            throw new IllegalStateException("Student not found: " + authentication.getName());
        }

        return canonical==null?officeReferralRepository.findByStudentEmailIgnoreCase(findMe.getStudentEmail()):scoped(Criteria.where("studentEmail").regex(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess.exact(findMe.getStudentEmail())));
    }

    public OfficeReferral findByReferralId(String referralId) throws ResourceNotFoundException {
        OfficeReferral fetchData = canonical==null?officeReferralRepository.findByOfficeReferralId(referralId):canonical.record(referralId,OfficeReferral.class,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.OFFICE);
        if (fetchData == null || fetchData.isArchived()) {
            throw new ResourceNotFoundException("No referrals with that ID exist");
        }

        log.debug(String.valueOf(fetchData));
        return fetchData;
    }

    public OfficeReferralResponse closeByReferralId(OfficeReferralCloseRequest request) throws ResourceNotFoundException {
        if(canonical!=null){canonical.admin();return canonical.atomic(()->{
            var checked=canonical.record(request.getId(),OfficeReferral.class,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.OFFICE);canonical.student(checked.getStudentEmail());
            if(checked.isArchived())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Archived referrals cannot be closed");
            var response=new OfficeReferralResponse();response.setOfficeReferral(managedChange(request.getId(),"CLOSED",request.getComment()));return response;
        });}
        OfficeReferral checked=canonical==null?officeReferralRepository.findByOfficeReferralId(request.getId()):canonical.record(request.getId(),OfficeReferral.class,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.OFFICE);
        if(checked==null) throw new ResourceNotFoundException("Case not found");
        userContextService.requireAdministratorInSchool(checked.getSchool());
        requiredNotifications.requireActionAccess(checked,"CLOSED");
        if("CLOSED".equals(checked.getStatus())) {
            var response=new OfficeReferralResponse(); response.setOfficeReferral(checked); return response;
        }
        if(requiredNotifications.isEnabled()) {
            OfficeReferralResponse response=new OfficeReferralResponse(); response.setOfficeReferral(managedChange(request.getId(),"CLOSED",request.getComment())); return response;
        }
        OfficeReferral findMe = checked;

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
        if(canonical!=null)throw new org.springframework.security.access.AccessDeniedException("Legacy maintenance is unavailable");
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
        throw new IllegalArgumentException("Office referrals have no student assignment; contact the principal");
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
                match(canonical==null?Criteria.where("studentEmail").in(studentEmails):Criteria.where("tenantSchoolId").is(canonical.tenant()).and("_id").in(referralList.stream().filter(r->canonical.canReadRecord(r,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.OFFICE)).map(OfficeReferral::getOfficeReferralId).toList())),
                canonical==null?lookup("students", "studentEmail", "studentEmail", "studentInfo"):canonical.studentLookup("studentInfo"),
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
