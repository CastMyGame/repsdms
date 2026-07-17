package com.reps.demogcloud.controllers;

import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.officeReferral.OfficeReferralCloseRequest;
import com.reps.demogcloud.models.officeReferral.OfficeReferralRequest;
import com.reps.demogcloud.models.officeReferral.OfficeReferralResponse;
import com.reps.demogcloud.services.OfficeReferralService;
import com.reps.demogcloud.services.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.mail.MessagingException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(
        origins = {
                "http://localhost:3000/",
                "https://repsdiscipline.vercel.app",
                "https://repsdev.vercel.app"
        }
)
@RequestMapping("/officeReferral/v1")
public class OfficeReferralController {

    private final OfficeReferralService officeReferralService;
    private final UserContextService userContextService;

    @GetMapping("/punishments")
    public ResponseEntity<List<OfficeReferral>> getAll() {
        userContextService.requireAnyRole("TEACHER", "GUIDANCE", "ADMIN");
        var message = officeReferralService.findAll().stream()
                .filter(referral -> referral.getSchool() != null
                        && referral.getSchool().equalsIgnoreCase(userContextService.getCurrentUserSchool()))
                .toList();
        return ResponseEntity
                .accepted()
                .body(message);
    }
    @GetMapping("/id/{id}")
    public ResponseEntity<OfficeReferral> getByReferralId(@PathVariable String id) throws ResourceNotFoundException {
        var message = officeReferralService.findByReferralId(id);
        userContextService.requireStaffAccessToStudent(message.getStudentEmail());

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/admin/{email}")
    public ResponseEntity<List<OfficeReferral>> getByAdminEmail(@PathVariable String email) throws ResourceNotFoundException {
        userContextService.requireAnyRole("TEACHER", "GUIDANCE", "ADMIN");
        var message = officeReferralService.findByAdminEmail(email).stream()
                .filter(referral -> referral.getSchool() != null
                        && referral.getSchool().equalsIgnoreCase(userContextService.getCurrentUserSchool()))
                .toList();

        return ResponseEntity
                .accepted()
                .body(message);
    }
    @PostMapping("/startPunish/adminReferral")
    public ResponseEntity<List<OfficeReferral>> createNewAdminReferralBulk(@RequestBody List<OfficeReferralRequest> officeReferralListRequest) {
        prepareOfficeReferralRequests(officeReferralListRequest);
        var message = officeReferralService.createNewAdminReferralBulk(officeReferralListRequest);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PostMapping("/closeId")
    public ResponseEntity<OfficeReferralResponse> closeByReferralId(@RequestBody OfficeReferralCloseRequest request) throws ResourceNotFoundException, MessagingException {
        requireOfficeReferralAccess(request.getId());
        var message = officeReferralService.closeByReferralId(request);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PostMapping("/submit/{id}")
    public ResponseEntity<OfficeReferralResponse> submitByReferralId(@PathVariable String id) throws ResourceNotFoundException, MessagingException {
        requireOfficeReferralAccess(id);
        var message = officeReferralService.submitByReferralId(id);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PutMapping("/{id}/index/{index}")
    public ResponseEntity<OfficeReferral> updateMapIndex(@PathVariable String id, @PathVariable int index) throws ResourceNotFoundException {
        requireOfficeReferralAccess(id);
        var message = officeReferralService.updateMapIndex(id,index);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PutMapping("/rejected/{punishmentId}")
    public ResponseEntity<OfficeReferral> rejectAnswers(@PathVariable String punishmentId) throws MessagingException {
        requireOfficeReferralAccess(punishmentId);
        OfficeReferral response = officeReferralService.rejectAnswers(punishmentId);
        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PutMapping("/descriptions")
    public ResponseEntity<List<OfficeReferral>> updateAllDescriptions() {
        List<OfficeReferral> response = officeReferralService.updateDescriptions();

        return ResponseEntity
                .accepted()
                .body(response);
    }

    private void prepareOfficeReferralRequests(List<OfficeReferralRequest> officeReferralListRequest) {
        userContextService.requireAnyRole("TEACHER", "ADMIN");
        String submittingStaffEmail = userContextService.getCurrentUserEmail();
        for (OfficeReferralRequest request : officeReferralListRequest) {
            userContextService.requireStaffAccessToStudent(request.getStudentEmail());
            request.setTeacherEmail(submittingStaffEmail);
        }
    }

    private void requireOfficeReferralAccess(String referralId) {
        OfficeReferral referral = officeReferralService.findByReferralId(referralId);
        userContextService.requireStaffAccessToStudent(referral.getStudentEmail());
    }
}
