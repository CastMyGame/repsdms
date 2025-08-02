package com.reps.demogcloud.services;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.models.dto.*;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.dto.AdminDtoService;
import com.reps.demogcloud.services.dto.PunishmentDtoService;
import com.reps.demogcloud.services.dto.StudentDtoService;
import com.reps.demogcloud.services.dto.TeacherDtoService;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class DTOService {
    private final AdminDtoService adminDtoService;
    private final TeacherDtoService teacherDtoService;
    private final StudentDtoService studentDtoService;
    private final PunishmentDtoService punishmentDtoService;

    public AdminOverviewDTO getAdminOverData() throws Exception {
        return adminDtoService.getAdminOverData();
    }

    public TeacherOverviewDTO getTeacherOverData() throws Exception {
        return teacherDtoService.getTeacherOverData();
    }

    public StudentOverviewDTO getLoggedInStudentOverData() throws Exception {
        return studentDtoService.getLoggedInStudentOverData();
    }

    public StudentOverviewDTO getStudentOverData(String studentEmail) throws Exception {
        return studentDtoService.getStudentOverData(studentEmail);
    }

    public List<PunishmentDTO> getDTOPunishments() throws Exception {
        return punishmentDtoService.getDTOPunishments();

    }
}
