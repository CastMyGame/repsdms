package com.reps.demogcloud.controllers;


import com.reps.demogcloud.models.dto.AdminOverviewDTO;
import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.dto.StudentOverviewDTO;
import com.reps.demogcloud.models.dto.TeacherOverviewDTO;
import com.reps.demogcloud.services.DTOService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;


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
        try {
            // Call the service method to fetch teacher overview data
            TeacherOverviewDTO message = dtoService.getTeacherOverData();

            // Log the fetched message
            System.out.println(message + " Teacher Overview DTO ");

            // If the method succeeds, return the response
            return ResponseEntity
                    .accepted()
                    .body(message);

        } catch (Exception e) {
            // Log the exception
            String currentUserEmail = getCurrentUserEmail();
            System.err.println("Error occurred while fetching teacher overview for: " + currentUserEmail);
            e.printStackTrace();  // Log the full stack trace for debugging

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
    public ResponseEntity<StudentOverviewDTO> getAllStudentOverview(String studentEmail) throws Exception {
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
