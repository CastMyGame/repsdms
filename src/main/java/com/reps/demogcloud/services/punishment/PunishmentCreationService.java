package com.reps.demogcloud.services.punishment;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.enums.InfractionType;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.*;
import com.reps.demogcloud.utils.PunishmentUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PunishmentCreationService {
    private final PunishmentUtils punishmentUtils;

    public PunishmentResponse createNewPunishForm(PunishmentFormRequest formRequest) throws MessagingException {
        punishmentUtils.validateFormRequest(formRequest);
        LocalDate now = LocalDate.now();

        Student student = punishmentUtils.fetchStudent(formRequest.getStudentEmail());
        School school = punishmentUtils.fetchSchool(student.getSchool());
        int maxLevel = school.getMaxPunishLevel();

        Infraction infraction = punishmentUtils.resolveInfraction(formRequest, school, maxLevel);
        Punishment punishment = punishmentUtils.buildPunishment(formRequest, infraction, student, maxLevel, now);

        // Save phone log if present
        punishmentUtils.savePhoneLogIfNeeded(formRequest, student, now);

        // Handle specific infraction behaviors
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
        List<PunishmentResponse> punishmentResponse = new ArrayList<>();
        for (PunishmentFormRequest punishmentFormRequest : listRequest) {
            punishmentResponse.add(createNewPunishForm(punishmentFormRequest));
        }
        return punishmentResponse;
    }
}
