package com.reps.demogcloud.controllers;


import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.services.DTOService;
import com.reps.demogcloud.services.PunishmentService;
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
    @Autowired
    DTOService dtoService;

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
    public ResponseEntity<TeacherOverviewDTO> getAllTeacherOverview() {
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
        var message = dtoService.getStudentOverData();
        return ResponseEntity
                .accepted()
                .body(message);


    }
}
