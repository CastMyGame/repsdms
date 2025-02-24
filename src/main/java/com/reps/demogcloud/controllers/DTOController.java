package com.reps.demogcloud.controllers;


import com.reps.demogcloud.models.dto.AdminOverviewDTO;
import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.dto.StudentOverviewDTO;
import com.reps.demogcloud.models.dto.TeacherOverviewDTO;
import com.reps.demogcloud.services.DTOService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(
        origins = {
                "http://localhost:3000/",
                "https://repsdiscipline.vercel.app",
                "https://repsdev.vercel.app"
        }
)
@RequestMapping("/DTO/v1")
public class DTOController {
    private static final Logger logger = LoggerFactory.getLogger(DTOController.class);
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
    public ResponseEntity<TeacherOverviewDTO> getAllTeacherOverview() {
        String currentUserEmail = getCurrentUserEmail(); // Fetch the logged-in user

        try {
            // Call the service method to fetch teacher overview data
            TeacherOverviewDTO message = dtoService.getTeacherOverData();

            // If the method succeeds, return the response
            return ResponseEntity
                    .accepted()
                    .body(message);

        } catch (Exception e) {
            // Log the exception with context
            logger.error("Error occurred while fetching teacher overview for: {}", currentUserEmail, e);


            // Return a detailed error message in the response
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new TeacherOverviewDTO(null, null, null, null, null, null));  // Optionally, return an empty DTO or a custom error DTO
        }
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
    public ResponseEntity<StudentOverviewDTO> getAllStudentOverview(@PathVariable String studentEmail) throws Exception {
        var message = dtoService.getStudentOverData(studentEmail);
        return ResponseEntity
                .accepted()
                .body(message);


    }

    // Helper method to get the current user's email from authentication context
    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "Unknown";
    }
}
