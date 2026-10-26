package com.reps.demogcloud.services;

import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.school.SchoolResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SchoolService {
    private final SchoolRepository schoolRepository;
    private final WorkflowAccessService access;
    private final UserContextService users;
    private final org.springframework.data.mongodb.core.MongoTemplate mongo;

    /** Provisioning is an approved maintenance operation, never a public registration side effect. */
    public SchoolResponse createNewSchool(School schoolRequest) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "School activation requires approval from the REPS team.");
    }

    public SchoolResponse editSchool(String schoolName, Map<String, String> updates) {
        access.requireAdmin();
        if (!access.sameSchool(schoolName))
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        // Validate the complete allowlist before any mutation. Identity/location remain maintenance-only.
        if (updates == null || updates.isEmpty() || !java.util.Set.of("currency", "maxPunishLevel").containsAll(updates.keySet()))
            throw new IllegalArgumentException("Only currency and maxPunishLevel may be edited.");
        var update = new org.springframework.data.mongodb.core.query.Update();
        for (var entry : updates.entrySet()) {
            if ("currency".equals(entry.getKey())) {
                String label = entry.getValue() == null ? "" : entry.getValue().trim();
                if (label.isEmpty() || label.length() > 80 || label.chars().anyMatch(Character::isISOControl))
                    throw new IllegalArgumentException("Currency label must contain 1 to 80 printable characters.");
                update.set("currency", label);
            } else {
                int level;
                try { level = Integer.parseInt(entry.getValue()); }
                catch (Exception invalid) { throw new IllegalArgumentException("maxPunishLevel must be a whole number from 1 to 4."); }
                if (level < 1 || level > 4) throw new IllegalArgumentException("maxPunishLevel must be a whole number from 1 to 4.");
                update.set("maxPunishLevel", level);
            }
        }
        School school;
        try { school = schoolRepository.findBySchoolNameIgnoreCase(users.getCurrentUserSchool()).orElse(null); }
        catch (org.springframework.dao.IncorrectResultSizeDataAccessException ambiguous) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "School identity requires REPS support review.");
        }
        if (school == null) return new SchoolResponse(null, "School not found");
        if (!access.sameSchool(school.getSchoolName())) throw new org.springframework.security.access.AccessDeniedException("Access denied");
        var query = org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria
                .where("_id").is(school.getSchoolIdNumber()).and("schoolName").is(school.getSchoolName()));
        School saved = mongo.findAndModify(query, update,
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), School.class);
        if (saved == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                "School changed. Reload before retrying.");
        return new SchoolResponse(saved, null);
    }

    public Optional<School> findSchoolByName (String school) {
        return schoolRepository.findBySchoolNameIgnoreCase(school);
    }

    public java.util.List<School> getAllSchools() {
        return schoolRepository.findAll();
    }

    public List<School> getSchoolsByCityState(String city, String state) {
        if (city == null || city.trim().isEmpty() || state == null || state.trim().isEmpty()) {
            return List.of();
        }
        return schoolRepository.findByCityIgnoreCaseAndStateIgnoreCaseOrderBySchoolNameAsc(
                city.trim(),
                state.trim()
        );
    }
}
