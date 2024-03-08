package com.reps.demogcloud.services;

import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.school.SchoolResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SchoolService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final SchoolRepository schoolRepository;

    public SchoolService(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    public SchoolResponse createNewSchool (School schoolRequest) {
        try {
            return new SchoolResponse (schoolRepository.save(schoolRequest), "");
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage());
            return new SchoolResponse(null, e.getMessage());
        }
    }
}
