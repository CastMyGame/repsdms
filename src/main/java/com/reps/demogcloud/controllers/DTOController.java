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
    public ResponseEntity<AdminOverviewDTO> getAll() {
        var message = dtoService.getAdminOverData();
        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/TeacherOverviewData/{email}")
    public ResponseEntity<TeacherOverviewDTO> getAllTeacherOverview(@PathVariable String email) {
        var message = dtoService.getTeacherOverData(email);
        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/StudentOverviewData/{studentEmail}")
    public ResponseEntity<StudentOverviewDTO> getAllStudentOverview(@PathVariable String studentEmail) throws Exception {
        var message = dtoService.getStudentOverData(studentEmail);
        return ResponseEntity
                .accepted()
                .body(message);
    }


}
