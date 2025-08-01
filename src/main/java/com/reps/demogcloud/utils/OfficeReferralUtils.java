package com.reps.demogcloud.utils;

import com.reps.demogcloud.data.OfficeReferralRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OfficeReferralUtils {
    private final OfficeReferralRepository officeReferralRepository;
    private final SchoolUtils schoolUtils;

    public List<OfficeReferral> FetchOfficeReferralsByIsArchivedAndSchool(boolean bool) throws ResourceNotFoundException {
        List<OfficeReferral> archivedRecords = officeReferralRepository.findByIsArchivedAndSchoolName(bool, schoolUtils.fetchSchoolName());
        if (archivedRecords.isEmpty()) {
            return new ArrayList<>();
        }
        return archivedRecords;
    }
}
