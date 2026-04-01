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

    public PunishmentResponse createNewPunishForm(PunishmentFormRequest formRequest) throws MessagingException {
        punishmentUtils.validateFormRequest(formRequest);
        LocalDate now = LocalDate.now();

        Student student = punishmentUtils.fetchStudent(formRequest.getStudentEmail());
        Optional<School> schoolOpt = punishmentUtils.fetchSchool(student.getSchool());

        School school = schoolOpt.orElseThrow(() ->
                new IllegalStateException("School not found: " + student.getSchool())
        );
        int maxLevel = school.getMaxPunishLevel();

        Infraction infraction = punishmentUtils.resolveInfraction(formRequest, school, maxLevel);
        Punishment punishment = punishmentUtils.buildPunishment(formRequest, infraction, student, maxLevel, now);

        try {
            int level;
            try {
                level = Integer.parseInt(punishment.getInfractionLevel());
            } catch (NumberFormatException e) {
                level = maxLevel;
            }

            String teacherEmail = punishment.getTeacherEmail();
            String schoolIdOrName = student.getSchool();
            String infractionName = infraction.getInfractionName();

            AssignmentTemplate template = assignmentService.resolveTemplateForInfraction(
                    teacherEmail,
                    schoolIdOrName,
                    infractionName,
                    level
            );

            punishment.setAssignmentTemplateId(template.getId());
        } catch (Exception e) {
            log.info(
                    "No assignment template resolved for infraction={} level={} teacher={}",
                    infraction.getInfractionName(),
                    punishment.getInfractionLevel(),
                    punishment.getTeacherEmail(),
                    e
            );
        }

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
        List<PunishmentResponse> punishmentResponses = new ArrayList<>();
        for (PunishmentFormRequest punishmentFormRequest : listRequest) {
            punishmentResponses.add(createNewPunishForm(punishmentFormRequest));
        }
        return punishmentResponses;
    }
}
