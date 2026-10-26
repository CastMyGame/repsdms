package com.reps.demogcloud.services.punishment;

import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import com.reps.demogcloud.models.enums.InfractionType;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.AssignmentService;
import com.reps.demogcloud.services.UserContextService;
import com.reps.demogcloud.services.email.EmailSenderService;
import com.reps.demogcloud.utils.PunishmentUtils;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PunishmentCreationService {

    private final PunishmentUtils punishmentUtils;
    private final AssignmentService assignmentService;
    private final UserContextService userContextService;
    private final EmailSenderService emailSenderService;
    private final com.reps.demogcloud.services.email.RequiredNotificationService requiredNotifications;

    private final com.reps.demogcloud.services.currency.CurrencyService currency;

    public record Prepared(PunishmentFormRequest request,Student student,Punishment punishment,Infraction infraction,LocalDate now) {}

    public PunishmentResponse createNewPunishForm(PunishmentFormRequest formRequest) throws MessagingException {
        if (formRequest!=null && InfractionType.POSITIVE.getLabel().equals(formRequest.getInfractionName()))
            return award(java.util.List.of(formRequest)).get(0);
        return persist(prepare(formRequest));
    }
    private Prepared prepare(PunishmentFormRequest formRequest) {
        punishmentUtils.validateFormRequest(formRequest);
        LocalDate now = LocalDate.now();

        Student student = punishmentUtils.fetchStudent(formRequest.getStudentEmail());
        String actor = userContextService.getCurrentStaffEmail();
        userContextService.requireStaffInStudentSchool(actor, student);
        formRequest.setTeacherEmail(actor);
        String managedId = null;
        if (requiredNotifications.isEnabled() || InfractionType.POSITIVE.getLabel().equals(formRequest.getInfractionName())) {
            managedId = requiredNotifications.creationId("punishment", student.getStudentEmail(),java.util.Arrays.asList(
                    formRequest.getInfractionName(),formRequest.getInfractionPeriod(),formRequest.getInfractionDescription(),formRequest.getCurrency(),
                    formRequest.getGuidanceDescription(),formRequest.getPhoneLogDescription(),formRequest.isAdminReferral(),formRequest.isGuidanceReferral()));
            Punishment existing = requiredNotifications.existingCreation(managedId, Punishment.class);
            if (existing != null && !InfractionType.POSITIVE.getLabel().equals(formRequest.getInfractionName()))
                return new Prepared(formRequest,student,existing,null,now);
        }
        Optional<School> schoolOpt = punishmentUtils.fetchSchool(student.getSchool());

        School school = schoolOpt.orElseThrow(() ->
                new IllegalStateException("School not found: " + student.getSchool())
        );
        int maxLevel = school.getMaxPunishLevel();

        Infraction infraction = punishmentUtils.resolveInfraction(formRequest, school, maxLevel);
        Punishment punishment = punishmentUtils.buildPunishment(formRequest, infraction, student, maxLevel, now);
        if (managedId != null) punishment.setPunishmentId(managedId);

        if (!InfractionType.isSpecialCase(infraction.getInfractionName()) && !formRequest.isAdminReferral()
                && punishment.getClosedTimes()<4) try {
            int level = Integer.parseInt(punishment.getInfractionLevel());
            if(level<1) throw new IllegalArgumentException("Invalid assignment level");

            String teacherEmail = punishment.getTeacherEmail();
            String schoolIdOrName = student.getSchool();
            String infractionName = infraction.getInfractionName();

            AssignmentTemplate template = assignmentService.resolveForCase(
                    teacherEmail,
                    schoolIdOrName,
                    infractionName,
                    level
            );

            punishment.setAssignmentTemplateId(template.getId());
            punishment.setAssignmentWork(com.reps.demogcloud.models.assignments.AssignmentWork.assigned(template));
        } catch (Exception e) {
            punishment.setAssignmentWork(com.reps.demogcloud.models.assignments.AssignmentWork.unavailable(
                    "No usable assignment could be attached. Staff must review templates and explicitly attach an assignment."));
            log.info("Assignment could not be attached; case creation continues with assignment unavailable");
        }

        return new Prepared(formRequest,student,punishment,infraction,now);
    }
    private PunishmentResponse persist(Prepared prepared) throws MessagingException {
        var formRequest=prepared.request();var student=prepared.student();var punishment=prepared.punishment();
        var infraction=prepared.infraction();var now=prepared.now();
        if(infraction==null) return requiredNotifications.response(punishment);
        punishmentUtils.savePhoneLogIfNeeded(formRequest, student, now);

        String infractionName = infraction.getInfractionName();
        boolean isAdminReferral = formRequest.isAdminReferral();

        if (InfractionType.POSITIVE.getLabel().equals(infractionName)) {
            return punishmentUtils.handlePositiveShoutout(formRequest, punishment, student, now);
        }

        if (InfractionType.BEHAVIORAL.getLabel().equals(infractionName)) {
            return punishmentUtils.handleBasicClose("BC", formRequest, punishment, student, now);
        }

        if (InfractionType.ACADEMIC.getLabel().equals(infractionName)) {
            return punishmentUtils.handleBasicClose("AC", formRequest, punishment, student, now);
        }

        if (InfractionType.INCOMPLETE_WORK.getLabel().equals(infractionName)) {
            return punishmentUtils.handleBasicClose("PENDING", formRequest, punishment, student, now);
        }

        if (punishment.getClosedTimes() >= 4) {
            return punishmentUtils.handleLevelFourReferral(formRequest, punishment, student);
        }

        if (isAdminReferral) {
            return punishmentUtils.handleAdminReferral(formRequest, punishment, student, now);
        }

        return punishmentUtils.handleDefaultOpen(formRequest, punishment, student);
    }

    public List<PunishmentResponse> createNewPunishFormBulk(List<PunishmentFormRequest> listRequest) throws MessagingException {
        if(listRequest!=null && listRequest.stream().anyMatch(r->r!=null && InfractionType.POSITIVE.getLabel().equals(r.getInfractionName())))
            return award(listRequest);
        List<PunishmentResponse> punishmentResponses = new ArrayList<>();
        for (PunishmentFormRequest punishmentFormRequest : listRequest) {
            punishmentResponses.add(createNewPunishForm(punishmentFormRequest));
        }
        return punishmentResponses;
    }
    private List<PunishmentResponse> award(List<PunishmentFormRequest> requests) {
        if(requests.stream().anyMatch(r->r==null || !InfractionType.POSITIVE.getLabel().equals(r.getInfractionName())
                || r.isAdminReferral() || r.isGuidanceReferral()))
            throw new IllegalArgumentException("Positive recognition cannot be mixed with referrals or other case types");
        String actor=userContextService.getCurrentStaffEmail();
        requests.forEach(request->request.setTeacherEmail(actor));
        // Hash trusted actor identity; frontend sender overrides cannot affect the receipt.
        return currency.award(requests,()->requests.stream().map(this::prepare).toList(),item->{
            try {persist(item);} catch(MessagingException impossible) {throw new IllegalStateException("Recognition persistence failed",impossible);}
        });
    }

}
