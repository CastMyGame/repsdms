package com.reps.demogcloud.controllers;


import com.reps.demogcloud.models.dto.AdminOverviewDTO;
import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.dto.StudentOverviewDTO;
import com.reps.demogcloud.models.dto.TeacherOverviewDTO;
import com.reps.demogcloud.services.DTOService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@CrossOrigin(
        origins = {
                "http://localhost:3000/",
                "https://repsdiscipline.vercel.app"
        }
)
@RequestMapping("/DTO/v1")
public class DTOController {
    DTOService dtoService;

    @Autowired
    public DTOController(DTOService dtoService) {
        this.dtoService = dtoService;
    }

    //-------------------------------------GET Controllers-------------------------------
    @GetMapping("/AdminOverviewData")
    public ResponseEntity<AdminOverviewDTO> getAll() throws Exception {
        var message = dtoService.getAdminOverData();
        return ResponseEntity
                .accepted()
                .body(message);
    }


    //Uses Logged In User
    @GetMapping("/TeacherOverviewData")
    public ResponseEntity<TeacherOverviewDTO> getAllTeacherOverview() throws Exception {
        var message = dtoService.getTeacherOverData();
        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/punishmentsDTO")
    public ResponseEntity<List<PunishmentDTO>> getAllPunishmentDTO() throws Exception {
        var message = dtoService.getDTOPunishments();
        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/StudentOverviewData")
    public ResponseEntity<StudentOverviewDTO> getAllStudentOverview() throws Exception {
        var message = dtoService.getLoggedInStudentOverData();
        return ResponseEntity
                .accepted()
                .body(message);


    }

    @GetMapping("/StudentOverviewData/{studentEmail}")
    public ResponseEntity<StudentOverviewDTO> getAllStudentOverview(String studentEmail) throws Exception {
        var message = dtoService.getStudentOverData(studentEmail);
        return ResponseEntity
                .accepted()
                .body(message);


    }
}
