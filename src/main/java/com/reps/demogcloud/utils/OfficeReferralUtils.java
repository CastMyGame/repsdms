package com.reps.demogcloud.utils;

import com.reps.demogcloud.data.OfficeReferralRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OfficeReferralUtils {

    private final OfficeReferralRepository officeReferralRepository;
    private final SchoolUtils schoolUtils;

    public List<OfficeReferral> fetchOfficeReferralsByArchivedAndSchool(boolean archived)
            throws ResourceNotFoundException {

        String schoolName = schoolUtils.fetchSchoolName();

        List<OfficeReferral> results =
                officeReferralRepository.findByArchivedAndSchool(archived, schoolName);

        return results != null ? results : Collections.emptyList();
    }
}