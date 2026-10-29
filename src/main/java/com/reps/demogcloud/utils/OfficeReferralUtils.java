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
    @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.data.mongodb.core.MongoTemplate mongo;

    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalWorkflowAccess(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess value){canonical=value;}
    public List<OfficeReferral> fetchOfficeReferralsByArchivedAndSchool(boolean archived)
            throws ResourceNotFoundException {

        if(canonical!=null){
            return mongo.find(canonical.query().addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("archived").is(archived)),OfficeReferral.class).stream().filter(r->canonical.canReadRecord(r,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.OFFICE)).toList();
        }
        String schoolName = schoolUtils.fetchSchoolName();

        List<OfficeReferral> results =
                officeReferralRepository.findByArchivedAndSchool(archived, schoolName);

        return results != null ? results : Collections.emptyList();
    }
}