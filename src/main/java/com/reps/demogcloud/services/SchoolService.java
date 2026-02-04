package com.reps.demogcloud.services;

import com.mongodb.DuplicateKeyException;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.school.SchoolResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SchoolService {
    private final SchoolRepository schoolRepository;
    public SchoolResponse createNewSchool(School schoolRequest) {
        try {
            if (schoolRequest == null) {
                return new SchoolResponse(null, "Request body is required.");
            }

            // ---- validate required fields ----
            String name = safeTrim(schoolRequest.getSchoolName());
            String currency = safeTrim(schoolRequest.getCurrency());
            String city = safeTrim(schoolRequest.getCity());
            String state = safeTrim(schoolRequest.getState());
            String zip = safeTrim(schoolRequest.getZip());

            if (!StringUtils.hasText(name)) return new SchoolResponse(null, "schoolName is required");
            if (!StringUtils.hasText(currency)) return new SchoolResponse(null, "currency is required");
            if (!StringUtils.hasText(city)) return new SchoolResponse(null, "city is required");
            if (!StringUtils.hasText(state)) return new SchoolResponse(null, "state is required");
            if (!StringUtils.hasText(zip)) return new SchoolResponse(null, "zip is required");

            state = state.toUpperCase(Locale.US);
            zip = zip.replaceAll("[^0-9]", ""); // keep digits only

            if (state.length() != 2) return new SchoolResponse(null, "state must be 2 letters");
            if (zip.length() != 5) return new SchoolResponse(null, "zip must be 5 digits");

            // ---- normalize + dedupe ----
            String normalizedKey = buildNormalizedKey(name, city, state, zip);

            // if already exists, return it (no duplicate creation)
            var existing = schoolRepository.findByNormalizedKey(normalizedKey);
            if (existing.isPresent()) {
                return new SchoolResponse(existing.get(), null);
            }

            // ---- construct new school (server-side truth) ----
            School toSave = new School();
            toSave.setSchoolIdNumber(UUID.randomUUID().toString());
            toSave.setSchoolName(name);
            toSave.setCurrency(currency);

            toSave.setCity(city);
            toSave.setState(state);
            toSave.setZip(zip);

            toSave.setNormalizedKey(normalizedKey);

            // default (or keep passed value if you want)
            toSave.setMaxPunishLevel(4);

            School saved = schoolRepository.save(toSave);
            return new SchoolResponse(saved, null);

        } catch (DuplicateKeyException dke) {
            // if you add a unique index later, this catches races cleanly
            return new SchoolResponse(null, "A school with the same name/location already exists.");
        } catch (Exception e) {
            log.error("createNewSchool failed", e);
            return new SchoolResponse(null, "Failed to create school.");
        }
    }

    private String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private String buildNormalizedKey(String name, String city, String state, String zip) {
        return normalize(name) + "|" + normalize(city) + "|" + state.toUpperCase(Locale.US) + "|" + zip;
    }

    private String normalize(String s) {
        if (s == null) return "";
        // lowercase, trim, collapse whitespace, remove punctuation-ish
        String v = s.trim().toLowerCase(Locale.US);
        v = v.replaceAll("[^a-z0-9\\s]", " ");  // punctuation -> space
        v = v.replaceAll("\\s+", " ");         // collapse spaces
        return v;
    }

    public SchoolResponse editSchool(String schoolName, Map<String, String> updates) {
        Optional<School> optionalSchool = schoolRepository.findBySchoolNameIgnoreCase(schoolName);
        if (optionalSchool.isEmpty()) {
            return new SchoolResponse(null, "School not found");
        }

        School school = optionalSchool.get();

        for (Map.Entry<String, String> entry : updates.entrySet()) {

            // ---- block unsafe fields from being edited via reflection ----
            String key = entry.getKey();
            if (key == null) continue;

            // denylist: never allow updating identity/derived fields
            if (key.equals("schoolIdNumber") || key.equals("normalizedKey")) {
                log.warn("Blocked attempt to update protected field: {}", key);
                continue;
            }

            // (optional) also block zip/city/state edits if you want those immutable:
            // if (key.equals("zip") || key.equals("city") || key.equals("state")) continue;

            try {
                Field field = School.class.getDeclaredField(key);
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
        return new SchoolResponse(schoolRepository.save(school), null);
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
