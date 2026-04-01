package com.reps.demogcloud.services.dto;

import com.reps.demogcloud.models.dto.StudentOverviewDTO;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.OfficeReferralService;
import com.reps.demogcloud.services.PunishmentService;
import com.reps.demogcloud.services.SchoolService;
import com.reps.demogcloud.services.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentDtoService {

    private final PunishmentService punishmentService;
    private final StudentService studentService;
    private final OfficeReferralService officeReferralService;
    private final SchoolService schoolService;

    public StudentOverviewDTO getLoggedInStudentOverData() throws Exception {
        List<Punishment> punishmentList = punishmentService.findAllPunishmentsByStudentEmail();
        List<OfficeReferral> referralList = officeReferralService.findByLoggedInStudent();
        Student student = studentService.findByLoggedInStudent();

        if (student == null) {
            throw new IllegalStateException("Logged in student not found");
        }

        Optional<School> schoolOpt = studentService.getStudentSchool();

        School school = schoolOpt.orElseThrow(() ->
                new IllegalStateException("School not found: " + student.getSchool())
        );

        return new StudentOverviewDTO(punishmentList, referralList, school, student);
    }

    public StudentOverviewDTO getStudentOverData(String studentEmail) throws Exception {
        List<Punishment> punishmentList = punishmentService.getAllPunishmentByStudentEmail(studentEmail);
        List<OfficeReferral> referralList = officeReferralService.findByStudentEmail(studentEmail);
        Student student = studentService.findByStudentEmail(studentEmail);

        if (student == null) {
            throw new IllegalStateException("Student not found: " + studentEmail);
        }

        Optional<School> schoolOpt = schoolService.findSchoolByName(student.getSchool());

        School ourSchool = schoolOpt.orElseThrow(() ->
                new IllegalStateException("School not found: " + student.getSchool())
        );

        return new StudentOverviewDTO(punishmentList, referralList, ourSchool, student);
    }
}