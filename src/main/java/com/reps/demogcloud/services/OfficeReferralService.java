package com.reps.demogcloud.services;

import com.reps.demogcloud.data.OfficeReferralRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.officeReferral.OfficeReferralRequest;
import com.reps.demogcloud.models.officeReferral.OfficeReferralResponse;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
@Service
@Slf4j
@RequiredArgsConstructor
public class OfficeReferralService {

    private final StudentRepository studentRepository;
    private final SchoolRepository schoolRepository;
    private final OfficeReferralRepository officeReferralRepository;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public List<OfficeReferral> createNewAdminReferralBulk(List<OfficeReferralRequest> officeReferralRequests) throws MessagingException, IOException, InterruptedException {
        List<OfficeReferral> punishmentResponse = new ArrayList<>();
        for(OfficeReferralRequest officeReferralRequest : officeReferralRequests) {
            punishmentResponse.add(createNewOfficeReferral(officeReferralRequest));
        } return  punishmentResponse;
    }

    private OfficeReferral createNewOfficeReferral(OfficeReferralRequest officeReferralRequest) {
        System.out.println(officeReferralRequest + " THE REQUEST ");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
        LocalDate now = LocalDate.now();

        Student findMe = studentRepository.findByStudentEmailIgnoreCase(officeReferralRequest.getStudentEmail());
        School ourSchool = schoolRepository.findSchoolBySchoolName(findMe.getSchool());

        ArrayList<String> description = new ArrayList<>();
        description.add(officeReferralRequest.getInfractionDescription());

        OfficeReferral request = new OfficeReferral();
        request.setAdminEmail(findMe.getAdminEmail());
        request.setStudentEmail(findMe.getStudentEmail());
        request.setTeacherEmail(officeReferralRequest.getTeacherEmail());
        request.setClassPeriod(officeReferralRequest.getClassPeriod());
        request.setSchoolName(ourSchool.getSchoolName());
        request.setStatus("OPEN");
        request.setTimeCreated(now);
        request.setInfractionDescription(description);
        request.setReferralCode(officeReferralRequest.getReferralCode());

        return officeReferralRepository.save(request);
    }
}
