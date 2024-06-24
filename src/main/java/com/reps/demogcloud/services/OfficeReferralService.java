package com.reps.demogcloud.services;

import com.reps.demogcloud.data.OfficeReferralRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.officeReferral.OfficeReferralRequest;
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
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OfficeReferralService {

    private final StudentRepository studentRepository;
    private final SchoolRepository schoolRepository;
    private final OfficeReferralRepository officeReferralRepository;
    private final EmailService emailService;
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

    public OfficeReferral updateMapIndex(String id, int index) {
        OfficeReferral referral = officeReferralRepository.findByOfficeReferralId(id);
        if(referral !=null){
            referral.setMapIndex(index);
            officeReferralRepository.save(referral);
            return referral;

        }else{
            throw new ResourceNotFoundException("No Punishment with Id " +id +" number exist");

        }


    }

    public OfficeReferral rejectAnswers(String referralId, String description) throws MessagingException {
//        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        //get punishment
        OfficeReferral referral = officeReferralRepository.findByOfficeReferralId(referralId);
        Student studentReject = studentRepository.findByStudentEmailIgnoreCase(referral.getStudentEmail());
        ArrayList<String> infractionContext = referral.getInfractionDescription();
        String resetContext = infractionContext.get(1);
        List<String> contextToStore = infractionContext.subList(1, infractionContext.size());

        ArrayList<String> studentAnswer = new ArrayList<>();
        studentAnswer.add("");
        studentAnswer.add(resetContext);
        Date currentDate = new Date();
        if(referral.getAnswerHistory() !=null){
            Map<Date,List<String>> answers = referral.getAnswerHistory();
            answers.put(currentDate,new ArrayList<>(contextToStore));
        }else {
            referral.setAnswerHistory(currentDate, new ArrayList<>(contextToStore));

        }
        referral.setInfractionDescription(studentAnswer);

        referral.setStatus("OPEN");

        String message =  "Hello, \n" +
                "Unfortunately your answers provided to the open ended questions were unacceptable and you must resubmit with acceptable answers to close this out. A description of why your answers were not accepted is:  \n" +
                " \n" +
                contextToStore + " \n" +
                "If you have any questions or concerns you can contact the teacher who wrote the referral directly by clicking reply all to this message and typing a response. Please include any extenuating circumstances that may have led to this behavior, or will prevent the completion of the assignment. You can also call the school directly at (843) 579-4815.";

        String subject = "Level Three Answers not accepted for " + studentReject.getFirstName() + " " + studentReject.getLastName();

        emailService.sendPtsEmail(studentReject.getParentEmail(),
                referral.getTeacherEmail(),
                studentReject.getStudentEmail(),
                subject,
                message);
        referral.setMapIndex(0);
        officeReferralRepository.save(referral);

        return referral;
    }

    public List<OfficeReferral> findAll() {
        return officeReferralRepository.findAll();
    }

    public List<OfficeReferral> findByAdminEmail(String adminEmail) {
        return officeReferralRepository.findByAdminEmail(adminEmail);
    }

    public OfficeReferral findByReferralId(String referralId) throws ResourceNotFoundException {
        var fetchData = officeReferralRepository.findByOfficeReferralId(referralId);
        if (fetchData == null) {
            throw new ResourceNotFoundException("No referrals with that ID exist");
        }

        if(fetchData.isArchived()){
            throw new ResourceNotFoundException("No referrals with that ID exist");

        }

        logger.debug(String.valueOf(fetchData));
        return fetchData;
    }
}
