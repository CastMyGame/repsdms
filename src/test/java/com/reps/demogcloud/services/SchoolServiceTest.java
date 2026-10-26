package com.reps.demogcloud.services;

import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.models.school.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolServiceTest {
    @Mock SchoolRepository schoolRepository;
    @Mock WorkflowAccessService access;
    @Mock UserContextService users;
    @Mock MongoTemplate mongo;
    @InjectMocks SchoolService schoolService;
    @Test void provisioningIsDisabledWithoutAnyPersistence() {
        var failure=assertThrows(ResponseStatusException.class,()->schoolService.createNewSchool(new School()));
        assertEquals(403,failure.getStatusCode().value());verifyNoInteractions(schoolRepository,mongo);
    }
    void admin() {when(access.sameSchool("Testing")).thenReturn(true);when(users.getCurrentUserSchool()).thenReturn("Testing");}
    @Test void onlyExplicitFieldsAreUpdatedWithoutStaleWholeDocumentSave() {
        admin();var school=new School();school.setSchoolName("Testing");school.setSchoolIdNumber("trusted");
        when(schoolRepository.findBySchoolNameIgnoreCase("Testing")).thenReturn(Optional.of(school));
        when(mongo.findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(School.class))).thenReturn(school);
        assertSame(school,schoolService.editSchool("Testing",Map.of("currency"," Points ","maxPunishLevel","4")).getSchool());
        var query=ArgumentCaptor.forClass(Query.class);var update=ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(query.capture(),update.capture(),any(FindAndModifyOptions.class),eq(School.class));
        assertEquals("trusted",query.getValue().getQueryObject().getString("_id"));
        assertEquals("Testing",query.getValue().getQueryObject().getString("schoolName"));
        assertEquals(new org.bson.Document("$set",new org.bson.Document("currency","Points").append("maxPunishLevel",4)),update.getValue().getUpdateObject());
        verify(schoolRepository,never()).save(any());
    }
    @Test void missingSchoolCannotBeCreatedBySettings() {
        admin();when(schoolRepository.findBySchoolNameIgnoreCase("Testing")).thenReturn(Optional.empty());
        assertEquals("School not found",schoolService.editSchool("Testing",Map.of("currency","Points")).getError());verifyNoInteractions(mongo);
    }
    @Test void staleOrAmbiguousSchoolFailsClosed() {
        admin();when(schoolRepository.findBySchoolNameIgnoreCase("Testing")).thenThrow(new org.springframework.dao.IncorrectResultSizeDataAccessException(1,2));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->schoolService.editSchool("Testing",Map.of("currency","Points"))).getStatusCode().value());verifyNoInteractions(mongo);
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
