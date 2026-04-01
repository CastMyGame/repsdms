package com.reps.demogcloud.services;

import com.reps.demogcloud.models.dto.AdminOverviewDTO;
import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.dto.StudentOverviewDTO;
import com.reps.demogcloud.models.dto.TeacherOverviewDTO;
import com.reps.demogcloud.services.dto.AdminDtoService;
import com.reps.demogcloud.services.dto.PunishmentDtoService;
import com.reps.demogcloud.services.dto.StudentDtoService;
import com.reps.demogcloud.services.dto.TeacherDtoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
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
