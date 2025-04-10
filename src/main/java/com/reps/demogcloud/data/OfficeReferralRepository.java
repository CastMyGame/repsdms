package com.reps.demogcloud.data;

import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OfficeReferralRepository extends MongoRepository<OfficeReferral, String> {
    List<OfficeReferral> findByAdminEmail(String adminEmail);
    List<OfficeReferral> findByIsArchivedAndSchoolName(Boolean archived, String schoolName);

    List<OfficeReferral> findByStudentEmailIgnoreCase(String studentEmail);
    OfficeReferral findByOfficeReferralId(String officeReferralId);
}
