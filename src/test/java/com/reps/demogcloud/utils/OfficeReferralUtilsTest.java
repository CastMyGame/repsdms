package com.reps.demogcloud.utils;

import com.reps.demogcloud.data.OfficeReferralRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfficeReferralUtilsTest {

    @Mock
    private OfficeReferralRepository officeReferralRepository;

    @Mock
    private SchoolUtils schoolUtils;

    @InjectMocks
    private OfficeReferralUtils officeReferralUtils;

    private OfficeReferral referral1;
    private OfficeReferral referral2;

    @BeforeEach
    void setUp() {
        referral1 = new OfficeReferral();
        referral2 = new OfficeReferral();
    }

    @Test
    void fetchOfficeReferralsByArchivedAndSchool_shouldReturnResults_whenDataExists() throws Exception {
        String schoolName = "Test School";

        when(schoolUtils.fetchSchoolName()).thenReturn(schoolName);
        when(officeReferralRepository.findByArchivedAndSchool(true, schoolName))
                .thenReturn(List.of(referral1, referral2));

        List<OfficeReferral> result =
                officeReferralUtils.fetchOfficeReferralsByArchivedAndSchool(true);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(referral1));
        assertTrue(result.contains(referral2));

        verify(schoolUtils).fetchSchoolName();
        verify(officeReferralRepository).findByArchivedAndSchool(true, schoolName);
    }

    @Test
    void fetchOfficeReferralsByArchivedAndSchool_shouldReturnEmptyList_whenNoResults() throws Exception {
        String schoolName = "Test School";

        when(schoolUtils.fetchSchoolName()).thenReturn(schoolName);
        when(officeReferralRepository.findByArchivedAndSchool(false, schoolName))
                .thenReturn(List.of());

        List<OfficeReferral> result =
                officeReferralUtils.fetchOfficeReferralsByArchivedAndSchool(false);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(officeReferralRepository).findByArchivedAndSchool(false, schoolName);
    }

    @Test
    void fetchOfficeReferralsByArchivedAndSchool_shouldReturnEmptyList_whenRepositoryReturnsNull() throws Exception {
        String schoolName = "Test School";

        when(schoolUtils.fetchSchoolName()).thenReturn(schoolName);
        when(officeReferralRepository.findByArchivedAndSchool(true, schoolName))
                .thenReturn(null);

        List<OfficeReferral> result =
                officeReferralUtils.fetchOfficeReferralsByArchivedAndSchool(true);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void fetchOfficeReferralsByArchivedAndSchool_shouldThrowException_whenSchoolUtilsFails() throws Exception {
        when(schoolUtils.fetchSchoolName())
                .thenThrow(new ResourceNotFoundException("School not found"));

        assertThrows(ResourceNotFoundException.class,
                () -> officeReferralUtils.fetchOfficeReferralsByArchivedAndSchool(true));

        verify(officeReferralRepository, never())
                .findByArchivedAndSchool(anyBoolean(), anyString());
    }

    @Test
    void fetchOfficeReferralsByArchivedAndSchool_shouldCallRepositoryWithCorrectParams() throws Exception {
        String schoolName = "My School";

        when(schoolUtils.fetchSchoolName()).thenReturn(schoolName);
        when(officeReferralRepository.findByArchivedAndSchool(false, schoolName))
                .thenReturn(List.of(referral1));

        officeReferralUtils.fetchOfficeReferralsByArchivedAndSchool(false);

        verify(officeReferralRepository, times(1))
                .findByArchivedAndSchool(false, schoolName);
    }
}
