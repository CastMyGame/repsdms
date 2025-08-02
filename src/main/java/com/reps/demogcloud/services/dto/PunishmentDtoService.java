package com.reps.demogcloud.services.dto;

import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.PunishmentService;
import com.reps.demogcloud.services.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PunishmentDtoService {

    private final PunishmentService punishmentService;
    private final StudentService studentService;

    public List<PunishmentDTO> getDTOPunishments() throws Exception {
        List<PunishmentDTO> punishmentDTOList = new ArrayList<>();
        List<Punishment> punishments = punishmentService.findAllSchool();
        for (Punishment punishment : punishments) {
            PunishmentDTO punishmentDTO = new PunishmentDTO();
            punishmentDTO.setPunishment(punishment);
            punishmentDTO.setStudentEmail(punishment.getStudentEmail());

            //get student info
            Student student = studentService.findByStudentEmail(punishment.getStudentEmail());
            punishmentDTO.setStudentFirstName(student.getFirstName());
            punishmentDTO.setStudentLastName(student.getLastName());
            punishmentDTOList.add(punishmentDTO);

        }

        return punishmentDTOList;

    }
}
