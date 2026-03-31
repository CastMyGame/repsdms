package com.reps.demogcloud.services;

import com.mongodb.DuplicateKeyException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcernResult;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.school.SchoolResponse;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolServiceTest {

    @Mock
    private SchoolRepository schoolRepository;

    @InjectMocks
    private SchoolService schoolService;

    @Test
    void createNewSchool_shouldReturnError_whenRequestIsNull() {
        SchoolResponse result = schoolService.createNewSchool(null);

        assertNotNull(result);
        assertNull(result.getSchool());
        assertEquals("Request body is required.", result.getError());

        verifyNoInteractions(schoolRepository);
    }

    @Test
    void createNewSchool_shouldReturnError_whenSchoolNameMissing() {
        School request = new School();
        request.setCurrency("Points");
        request.setCity("Charleston");
        request.setState("SC");
        request.setZip("29407");

        SchoolResponse result = schoolService.createNewSchool(request);

        assertNull(result.getSchool());
        assertEquals("schoolName is required", result.getError());

        verifyNoInteractions(schoolRepository);
    }

    @Test
    void createNewSchool_shouldReturnError_whenCurrencyMissing() {
        School request = new School();
        request.setSchoolName("Burke High");
        request.setCity("Charleston");
        request.setState("SC");
        request.setZip("29407");

        SchoolResponse result = schoolService.createNewSchool(request);

        assertNull(result.getSchool());
        assertEquals("currency is required", result.getError());

        verifyNoInteractions(schoolRepository);
    }

    @Test
    void createNewSchool_shouldReturnError_whenCityMissing() {
        School request = new School();
        request.setSchoolName("Burke High");
        request.setCurrency("Points");
        request.setState("SC");
        request.setZip("29407");

        SchoolResponse result = schoolService.createNewSchool(request);

        assertNull(result.getSchool());
        assertEquals("city is required", result.getError());

        verifyNoInteractions(schoolRepository);
    }

    @Test
    void createNewSchool_shouldReturnError_whenStateMissing() {
        School request = new School();
        request.setSchoolName("Burke High");
        request.setCurrency("Points");
        request.setCity("Charleston");
        request.setZip("29407");

        SchoolResponse result = schoolService.createNewSchool(request);

        assertNull(result.getSchool());
        assertEquals("state is required", result.getError());

        verifyNoInteractions(schoolRepository);
    }

    @Test
    void createNewSchool_shouldReturnError_whenZipMissing() {
        School request = new School();
        request.setSchoolName("Burke High");
        request.setCurrency("Points");
        request.setCity("Charleston");
        request.setState("SC");

        SchoolResponse result = schoolService.createNewSchool(request);

        assertNull(result.getSchool());
        assertEquals("zip is required", result.getError());

        verifyNoInteractions(schoolRepository);
    }

    @Test
    void createNewSchool_shouldReturnError_whenStateLengthInvalid() {
        School request = new School();
        request.setSchoolName("Burke High");
        request.setCurrency("Points");
        request.setCity("Charleston");
        request.setState("South Carolina");
        request.setZip("29407");

        SchoolResponse result = schoolService.createNewSchool(request);

        assertNull(result.getSchool());
        assertEquals("state must be 2 letters", result.getError());

        verifyNoInteractions(schoolRepository);
    }

    @Test
    void createNewSchool_shouldReturnError_whenZipLengthInvalid() {
        School request = new School();
        request.setSchoolName("Burke High");
        request.setCurrency("Points");
        request.setCity("Charleston");
        request.setState("SC");
        request.setZip("2940");

        SchoolResponse result = schoolService.createNewSchool(request);

        assertNull(result.getSchool());
        assertEquals("zip must be 5 digits", result.getError());

        verifyNoInteractions(schoolRepository);
    }

    @Test
    void createNewSchool_shouldReturnExistingSchool_whenNormalizedKeyAlreadyExists() {
        School request = new School();
        request.setSchoolName("Burke High");
        request.setCurrency("Points");
        request.setCity("Charleston");
        request.setState("sc");
        request.setZip("29407");

        School existing = new School();
        existing.setSchoolName("Burke High");

        when(schoolRepository.findByNormalizedKey("burke high|charleston|SC|29407"))
                .thenReturn(Optional.of(existing));

        SchoolResponse result = schoolService.createNewSchool(request);

        assertNotNull(result);
        assertSame(existing, result.getSchool());
        assertNull(result.getError());

        verify(schoolRepository).findByNormalizedKey("burke high|charleston|SC|29407");
        verify(schoolRepository, never()).save(any(School.class));
        verifyNoMoreInteractions(schoolRepository);
    }

    @Test
    void createNewSchool_shouldNormalizeAndSaveSchool_whenValid() {
        School request = new School();
        request.setSchoolName("  Burke High  ");
        request.setCurrency("Points");
        request.setCity(" Charleston ");
        request.setState("sc");
        request.setZip("29407");

        when(schoolRepository.findByNormalizedKey("burke high|charleston|SC|29407"))
                .thenReturn(Optional.empty());

        School saved = new School();
        saved.setSchoolName("Burke High");
        saved.setCity("Charleston");
        saved.setState("SC");
        saved.setZip("29407");
        saved.setCurrency("Points");
        saved.setMaxPunishLevel(4);

        when(schoolRepository.save(any(School.class))).thenReturn(saved);

        SchoolResponse result = schoolService.createNewSchool(request);

        assertNotNull(result);
        assertNotNull(result.getSchool());
        assertNull(result.getError());
        assertEquals("Burke High", result.getSchool().getSchoolName());

        ArgumentCaptor<School> captor = ArgumentCaptor.forClass(School.class);
        verify(schoolRepository).save(captor.capture());

        School toSave = captor.getValue();
        assertNotNull(toSave.getSchoolIdNumber());
        assertEquals("Burke High", toSave.getSchoolName());
        assertEquals("Charleston", toSave.getCity());
        assertEquals("SC", toSave.getState());
        assertEquals("29407", toSave.getZip());
        assertEquals("Points", toSave.getCurrency());
        assertEquals("burke high|charleston|SC|29407", toSave.getNormalizedKey());
        assertEquals(4, toSave.getMaxPunishLevel());

        verify(schoolRepository).findByNormalizedKey("burke high|charleston|SC|29407");
        verifyNoMoreInteractions(schoolRepository);
    }

    @Test
    void createNewSchool_shouldReturnDuplicateError_whenDuplicateKeyExceptionThrown() {
        School request = new School();
        request.setSchoolName("Burke High");
        request.setCurrency("Points");
        request.setCity("Charleston");
        request.setState("SC");
        request.setZip("29407");

        when(schoolRepository.findByNormalizedKey("burke high|charleston|SC|29407"))
                .thenReturn(Optional.empty());
        when(schoolRepository.save(any(School.class)))
                .thenThrow(new DuplicateKeyException(
                        new BsonDocument(),
                        new ServerAddress(),
                        WriteConcernResult.acknowledged(1, true, null)
                ));

        SchoolResponse result = schoolService.createNewSchool(request);

        assertNull(result.getSchool());
        assertEquals("A school with the same name/location already exists.", result.getError());

        verify(schoolRepository).findByNormalizedKey("burke high|charleston|SC|29407");
        verify(schoolRepository).save(any(School.class));
        verifyNoMoreInteractions(schoolRepository);
    }

    @Test
    void createNewSchool_shouldReturnGenericError_whenUnexpectedExceptionThrown() {
        School request = new School();
        request.setSchoolName("Burke High");
        request.setCurrency("Points");
        request.setCity("Charleston");
        request.setState("SC");
        request.setZip("29407");

        when(schoolRepository.findByNormalizedKey("burke high|charleston|SC|29407"))
                .thenReturn(Optional.empty());
        when(schoolRepository.save(any(School.class)))
                .thenThrow(new RuntimeException("boom"));

        SchoolResponse result = schoolService.createNewSchool(request);

        assertNull(result.getSchool());
        assertEquals("Failed to create school.", result.getError());

        verify(schoolRepository).findByNormalizedKey("burke high|charleston|SC|29407");
        verify(schoolRepository).save(any(School.class));
        verifyNoMoreInteractions(schoolRepository);
    }

    @Test
    void editSchool_shouldReturnError_whenSchoolNotFound() {
        when(schoolRepository.findBySchoolNameIgnoreCase("Missing School")).thenReturn(Optional.empty());

        SchoolResponse result = schoolService.editSchool("Missing School", Map.of("city", "Charleston"));

        assertNull(result.getSchool());
        assertEquals("School not found", result.getError());

        verify(schoolRepository).findBySchoolNameIgnoreCase("Missing School");
        verify(schoolRepository, never()).save(any(School.class));
        verifyNoMoreInteractions(schoolRepository);
    }

    @Test
    void editSchool_shouldUpdateStringIntAndBooleanFields_andIgnoreProtectedAndUnknownFields() {
        School school = new School();
        school.setSchoolIdNumber("ID-1");
        school.setSchoolName("Burke High");
        school.setCity("Old City");
        school.setState("SC");
        school.setZip("29407");
        school.setCurrency("Points");
        school.setMaxPunishLevel(4);
        school.setNormalizedKey("original-key");

        when(schoolRepository.findBySchoolNameIgnoreCase("Burke High")).thenReturn(Optional.of(school));
        when(schoolRepository.save(school)).thenReturn(school);

        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("city", "Charleston");
        updates.put("maxPunishLevel", "6");
        updates.put("schoolIdNumber", "SHOULD-NOT-CHANGE");
        updates.put("normalizedKey", "SHOULD-NOT-CHANGE");
        updates.put("fakeField", "ignored");

        SchoolResponse result = schoolService.editSchool("Burke High", updates);

        assertNotNull(result);
        assertNotNull(result.getSchool());
        assertNull(result.getError());
        assertEquals("Charleston", school.getCity());
        assertEquals(6, school.getMaxPunishLevel());
        assertEquals("ID-1", school.getSchoolIdNumber());
        assertEquals("original-key", school.getNormalizedKey());

        verify(schoolRepository).findBySchoolNameIgnoreCase("Burke High");
        verify(schoolRepository).save(school);
        verifyNoMoreInteractions(schoolRepository);
    }

    @Test
    void editSchool_shouldContinueWhenFieldParsingFails() {
        School school = new School();
        school.setSchoolName("Burke High");
        school.setMaxPunishLevel(4);

        when(schoolRepository.findBySchoolNameIgnoreCase("Burke High")).thenReturn(Optional.of(school));
        when(schoolRepository.save(school)).thenReturn(school);

        Map<String, String> updates = Map.of("maxPunishLevel", "not-a-number");

        SchoolResponse result = schoolService.editSchool("Burke High", updates);

        assertNotNull(result);
        assertNotNull(result.getSchool());
        assertEquals(4, school.getMaxPunishLevel());

        verify(schoolRepository).findBySchoolNameIgnoreCase("Burke High");
        verify(schoolRepository).save(school);
        verifyNoMoreInteractions(schoolRepository);
    }

    @Test
    void findSchoolByName_shouldDelegateToRepository() {
        School school = new School();

        when(schoolRepository.findBySchoolNameIgnoreCase("Burke High")).thenReturn(Optional.of(school));

        Optional<School> result = schoolService.findSchoolByName("Burke High");

        assertTrue(result.isPresent());
        assertSame(school, result.get());

        verify(schoolRepository).findBySchoolNameIgnoreCase("Burke High");
        verifyNoMoreInteractions(schoolRepository);
    }

    @Test
    void getAllSchools_shouldDelegateToRepository() {
        List<School> schools = List.of(new School(), new School());

        when(schoolRepository.findAll()).thenReturn(schools);

        List<School> result = schoolService.getAllSchools();

        assertSame(schools, result);

        verify(schoolRepository).findAll();
        verifyNoMoreInteractions(schoolRepository);
    }

    @Test
    void getSchoolsByCityState_shouldReturnEmpty_whenCityIsNull() {
        List<School> result = schoolService.getSchoolsByCityState(null, "SC");

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verifyNoInteractions(schoolRepository);
    }

    @Test
    void getSchoolsByCityState_shouldReturnEmpty_whenCityIsBlank() {
        List<School> result = schoolService.getSchoolsByCityState("   ", "SC");

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verifyNoInteractions(schoolRepository);
    }

    @Test
    void getSchoolsByCityState_shouldReturnEmpty_whenStateIsNull() {
        List<School> result = schoolService.getSchoolsByCityState("Charleston", null);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verifyNoInteractions(schoolRepository);
    }

    @Test
    void getSchoolsByCityState_shouldReturnEmpty_whenStateIsBlank() {
        List<School> result = schoolService.getSchoolsByCityState("Charleston", "   ");

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verifyNoInteractions(schoolRepository);
    }

    @Test
    void getSchoolsByCityState_shouldTrimInputsAndDelegateToRepository() {
        List<School> schools = List.of(new School());

        when(schoolRepository.findByCityIgnoreCaseAndStateIgnoreCaseOrderBySchoolNameAsc("Charleston", "SC"))
                .thenReturn(schools);

        List<School> result = schoolService.getSchoolsByCityState(" Charleston ", " SC ");

        assertSame(schools, result);

        verify(schoolRepository).findByCityIgnoreCaseAndStateIgnoreCaseOrderBySchoolNameAsc("Charleston", "SC");
        verifyNoMoreInteractions(schoolRepository);
    }
}