package com.reps.demogcloud.services;

import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.school.SchoolRequest;
import com.reps.demogcloud.models.school.SchoolResponse;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentResponse;
import com.reps.demogcloud.security.models.AuthenticationRequest;
import com.reps.demogcloud.security.models.RoleModel;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class SchoolService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final SchoolRepository schoolRepository;

    public SchoolService(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    public SchoolResponse createNewSchool (SchoolRequest schoolRequest) {
        try {
            return new SchoolResponse (schoolRepository.save(schoolRequest.getSchool()), "");
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage());
            return new SchoolResponse(null, e.getMessage());
        }
    }
}
