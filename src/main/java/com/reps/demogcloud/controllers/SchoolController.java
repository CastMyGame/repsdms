package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.school.SchoolResponse;
import com.reps.demogcloud.services.SchoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@CrossOrigin(origins = {
        "http://localhost:3000",
        "https://repsdiscipline.vercel.app",
        "https://repsdev.vercel.app"
})
@RestController
@RequiredArgsConstructor
@RequestMapping("/school/v1")
public class SchoolController {

    private final SchoolService schoolService;

    @GetMapping("/all")
    public ResponseEntity<?> getAllSchools() {
        return ResponseEntity.ok(schoolService.getAllSchools());
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchSchools(
            @RequestParam String city,
            @RequestParam String state
    ) {
        return ResponseEntity.ok(schoolService.getSchoolsByCityState(city, state));
    }

    @PostMapping("/newSchool")
    public ResponseEntity<SchoolResponse> createSchool(@RequestBody School schoolRequest) {
        log.info("POST /school/v1/newSchool payload: schoolName='{}', currency='{}'",
                schoolRequest.getSchoolName(), schoolRequest.getCurrency());

        SchoolResponse schoolResponse = schoolService.createNewSchool(schoolRequest);

        if (schoolResponse.getSchool() == null) {
            log.warn("Create school failed: error='{}'", schoolResponse.getError());
            return new ResponseEntity<>(schoolResponse, HttpStatus.BAD_REQUEST);
        }

        log.info("Create school succeeded: id='{}', name='{}'",
                schoolResponse.getSchool().getSchoolIdNumber(),
                schoolResponse.getSchool().getSchoolName());

        return new ResponseEntity<>(schoolResponse, HttpStatus.CREATED);
    }

    @PutMapping("/{schoolName}")
    public ResponseEntity<SchoolResponse> editSchool(
            @PathVariable String schoolName,
            @RequestParam Map<String, String> update
    ) {
        SchoolResponse schoolResponse = schoolService.editSchool(schoolName, update);

        return schoolResponse.getSchool() == null
                ? new ResponseEntity<>(schoolResponse, HttpStatus.BAD_REQUEST)
                : new ResponseEntity<>(schoolResponse, HttpStatus.OK);
    }
}