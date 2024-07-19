package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.school.SchoolResponse;
import com.reps.demogcloud.services.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = {
        "http//localhost:3000",
        "https://repsdiscipline.vercel.app",
        "https://repsdev.vercel.app"})

@RestController
@RequiredArgsConstructor
@RequestMapping("/school/v1")
public class SchoolController {
    SchoolService schoolService;

    @Autowired
    public SchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @PostMapping("/newSchool")
    public ResponseEntity<SchoolResponse> createSchool (@RequestBody School schoolRequest) {
        SchoolResponse schoolResponse = schoolService.createNewSchool(schoolRequest);
        return schoolResponse.getSchool() == null
                ? new ResponseEntity<>(schoolResponse, HttpStatus.BAD_REQUEST)
                : new ResponseEntity<>(schoolResponse, HttpStatus.CREATED);
    }

    @PutMapping("/{schoolName}")
    public ResponseEntity<SchoolResponse> editSchool (@PathVariable String schoolName, @RequestParam String update) {
        SchoolResponse schoolResponse = schoolService.editSchool(schoolName, update);
        return schoolResponse.getSchool() == null
                ? new ResponseEntity<>(schoolResponse, HttpStatus.BAD_REQUEST)
                : new ResponseEntity<>(schoolResponse, HttpStatus.OK);
    }
}
