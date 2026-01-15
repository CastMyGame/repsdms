package com.reps.demogcloud.services;

import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.school.SchoolResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SchoolService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final SchoolRepository schoolRepository;
    public SchoolResponse createNewSchool (School schoolRequest) {
        try {
            return new SchoolResponse (schoolRepository.save(schoolRequest), "");
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage());
            return new SchoolResponse(null, e.getMessage());
        }
    }

    public SchoolResponse editSchool(String schoolName, Map<String, String> updates) {
        School school = schoolRepository.findSchoolBySchoolName(schoolName);
        if (school == null) {
            return new SchoolResponse(null, "School not found");
        }
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            try {
                Field field = School.class.getDeclaredField(entry.getKey());
                field.setAccessible(true);
                Class<?> fieldType = field.getType();
                Object convertedValue;
                if (fieldType == int.class || fieldType == Integer.class) {
                    convertedValue = Integer.parseInt(entry.getValue());
                } else if (fieldType == boolean.class || fieldType == Boolean.class) {
                    convertedValue = Boolean.parseBoolean(entry.getValue());
                } else {
                    convertedValue = entry.getValue();
                }
                field.set(school, convertedValue);
            } catch (NoSuchFieldException e) {
                log.warn("No such field: " + entry.getKey(), e);
            } catch (IllegalAccessException e) {
                log.error("Could not access field: " + entry.getKey(), e);
            } catch (Exception e) {
                log.error("Failed to update field: " + entry.getKey(), e);
            }
        }
        return new SchoolResponse(schoolRepository.save(school), "");
    }

    public School findSchoolByName (String school) {
        return schoolRepository.findSchoolBySchoolName(school);
    }

    public java.util.List<School> getAllSchools() {
        return schoolRepository.findAll();
    }
}
