package com.reps.demogcloud.controllers;

import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.models.school.SchoolRequest;
import com.reps.demogcloud.models.school.SchoolResponse;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentResponse;
import com.reps.demogcloud.services.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = {
        "http//localhost:3000",
        "http://localhost:3000/"})

@RestController
@RequiredArgsConstructor
@RequestMapping("/school/v1")
public class schoolController {
    @Autowired
    SchoolService schoolService;

    @PostMapping("/newSchool")
    public ResponseEntity<SchoolResponse> createSchool (@RequestBody SchoolRequest schoolRequest) {
        SchoolResponse schoolResponse = schoolService.createNewSchool(schoolRequest);
        return schoolResponse.getSchool() == null
                ? new ResponseEntity<>(schoolResponse, HttpStatus.BAD_REQUEST)
                : new ResponseEntity<>(schoolResponse, HttpStatus.CREATED);
    }
}
