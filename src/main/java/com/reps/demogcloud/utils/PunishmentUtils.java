package com.reps.demogcloud.utils;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.CurrencyTransferRequest;
import com.reps.demogcloud.models.enums.InfractionType;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.officeReferral.OfficeReferralCode;
import com.reps.demogcloud.models.officeReferral.OfficeReferralRequest;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PunishmentUtils {

    private final StudentRepository studentRepository;
    private final InfractionRepository infractionRepository;
    private final PunishRepository punishRepository;
    private final SchoolRepository schoolRepository;
    private final EmailService emailService;
    private final EmployeeService employeeService;
    private final GuidanceService guidanceService;
    private final OfficeReferralService officeReferralService;

    public void validateFormRequest(PunishmentFormRequest formRequest) {
        if (formRequest.getInfractionDescription() == null || formRequest.getInfractionDescription().isEmpty()) {
            throw new IllegalArgumentException("Infraction description is required.");
        }
    }

    public Student fetchStudent(String email) {
        return studentRepository.findByStudentEmailIgnoreCase(email);
    }

    public Optional<School> fetchSchool(String schoolName) {
        return schoolRepository.findBySchoolNameIgnoreCase(schoolName);
    }

    public String getClosedLevel(String email, String name, int maxLevel) {
        List<Punishment> closed = punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(email, name, "CLOSED");
        int highest = closed.stream().mapToInt(Punishment::getClosedTimes).max().orElse(0);
        return String.valueOf(Math.min(Math.max(highest, 1), maxLevel));
    }


    public Infraction resolveInfraction(PunishmentFormRequest formRequest, School school, int maxLevel) {
        String name = formRequest.getInfractionName();
        String level = getClosedLevel(formRequest.getStudentEmail(), name, maxLevel);

        if (!InfractionType.isSpecialCase(name) && !formRequest.isAdminReferral()) {
            return infractionRepository.findByInfractionNameAndInfractionLevel(name, level);
        }
        return infractionRepository.findByInfractionName(name);
    }

    public Punishment buildPunishment(PunishmentFormRequest formRequest, Infraction infraction, Student student, int maxLevel, LocalDate now) {
        Punishment p = new Punishment();
        p.setStudentEmail(formRequest.getStudentEmail());
        p.setTeacherEmail(formRequest.getTeacherEmail());
        p.setClassPeriod(formRequest.getInfractionPeriod());
        p.setInfractionId(infraction.getInfractionId());
        p.setInfractionName(infraction.getInfractionName());
        p.setInfractionLevel(infraction.getInfractionLevel());
        p.setSchool(student.getSchool());
        p.setPunishmentId(UUID.randomUUID().toString());
        p.setTimeCreated(now);
        p.setClosedTimes(Integer.parseInt(getClosedLevel(student.getStudentEmail(), infraction.getInfractionName(), maxLevel)));

        List<String> descriptions = new ArrayList<>();
        descriptions.add(formRequest.getInfractionDescription());
        p.setInfractionDescription(descriptions);
        return p;
    }

    public void savePhoneLogIfNeeded(PunishmentFormRequest formRequest, Student student, LocalDate now) {
        if (formRequest.getPhoneLogDescription() != null && !formRequest.getPhoneLogDescription().isEmpty()) {
            ThreadEvent log = new ThreadEvent();
            log.setCreatedBy(formRequest.getTeacherEmail());
            log.setDate(now);
            log.setContent(formRequest.getPhoneLogDescription());
            log.setEvent("Phone");

            student.getNotesArray().add(log);
            studentRepository.save(student);
        }
    }

    public void linkGuidanceIfNeeded(PunishmentFormRequest request, Student student, Punishment punishment) {
        if (request.getGuidanceDescription() != null && !request.getGuidanceDescription().isEmpty()) {
            guidanceService.LinkAssignmentToGuidance(student, request, punishment);
        }
    }


    public PunishmentResponse handlePositiveShoutout(PunishmentFormRequest formRequest, Punishment punishment, Student student, LocalDate now) throws MessagingException {
        if (formRequest.getCurrency() > 0) {
            employeeService.transferCurrency(new CurrencyTransferRequest(
                    formRequest.getTeacherEmail(),
                    formRequest.getStudentEmail(),
                    formRequest.getCurrency()));
        }

        punishment.setStatus("SO");
        punishment.setTimeClosed(now);
        Punishment saved = punishRepository.save(punishment);

        linkGuidanceIfNeeded(formRequest, student, saved);

        return emailService.sendEmailBasedOnType(
                formRequest, saved, emailService);
    }

    public PunishmentResponse handleBasicClose(String status, PunishmentFormRequest formRequest, Punishment punishment, Student student, LocalDate now) throws MessagingException {
        punishment.setStatus(status);
        punishment.setTimeClosed(now);
        Punishment saved = punishRepository.save(punishment);

        linkGuidanceIfNeeded(formRequest, student, saved);

        return emailService.sendEmailBasedOnType(
                formRequest, saved, emailService);
    }

    public PunishmentResponse handleLevelFourReferral(PunishmentFormRequest formRequest, Punishment punishment, Student student) throws MessagingException {
        OfficeReferralCode code = new OfficeReferralCode();
        code.setCodeKey(42);
        code.setCodeName("Failure to Comply with Disciplinary Actions");

        OfficeReferralRequest referral = new OfficeReferralRequest();
        referral.setReferralCode(code);
        referral.setReferralDescription(new ArrayList<>(List.of(formRequest.getInfractionDescription())));
        referral.setStudentEmail(formRequest.getStudentEmail());
        referral.setTeacherEmail(formRequest.getTeacherEmail());
        referral.setCurrency(0);
        referral.setClassPeriod(formRequest.getInfractionPeriod());

        officeReferralService.createNewOfficeReferral(referral);

        punishment.setStatus("CLOSED");
        punishment.setTimeClosed(LocalDate.now());
        punishment.setClosedTimes(punishment.getClosedTimes() + 1);
        Punishment saved = punishRepository.save(punishment);

        linkGuidanceIfNeeded(formRequest, student, saved);

        return emailService.sendEmailBasedOnType(
                formRequest, saved, emailService);
    }

    public PunishmentResponse handleAdminReferral(PunishmentFormRequest formRequest, Punishment punishment, Student student, LocalDate now) throws MessagingException {
        punishment.setStatus("OPEN");
        punishment.setTimeClosed(now);
        Punishment saved = punishRepository.save(punishment);

        linkGuidanceIfNeeded(formRequest, student, saved);

        return emailService.sendEmailBasedOnType(
                formRequest, saved, emailService);
    }

    public PunishmentResponse handleDefaultOpen(PunishmentFormRequest formRequest, Punishment punishment, Student student) throws MessagingException {
        List<Punishment> openOrPending = new ArrayList<>();
        openOrPending.addAll(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(punishment.getStudentEmail(), punishment.getInfractionName(), "OPEN"));
        openOrPending.addAll(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(punishment.getStudentEmail(), punishment.getInfractionName(), "PENDING"));

        var existing = openOrPending.stream()
                .filter(p -> !p.isArchived())
                .toList();

        if (existing.isEmpty()) {
            punishment.setStatus("OPEN");
        } else {
            punishment.setStatus("CFR"); // Closed for Repeat
            punishment.setTimeClosed(LocalDate.now());
        }

        Punishment saved = punishRepository.save(punishment);

        linkGuidanceIfNeeded(formRequest, student, saved);

        if ("CFR".equals(punishment.getStatus())) {
            return emailService.sendCFREmailBasedOnType(saved);
        }

        return emailService.sendEmailBasedOnType(
                formRequest, saved, emailService);
    }

    public Punishment fetchOpenPunishment(String studentEmail, String infractionName) {
        return punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(studentEmail, infractionName, "OPEN")
                .stream()
                .filter(p -> !p.isArchived())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No open punishments found for " + studentEmail));
    }

    public void appendStudentAnswers(Punishment punishment, List<StudentAnswer> answers) {
        List<String> existing = punishment.getInfractionDescription();
        for (StudentAnswer answer : answers) {
            existing.add(answer.toString());
        }
        punishment.setInfractionDescription(existing);
    }
}
