package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.officeReferral.OfficeReferralRequest;
import com.reps.demogcloud.models.officeReferral.OfficeReferralResponse;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.services.OfficeReferralService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.mail.MessagingException;
import java.io.IOException;
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

    OfficeReferralService officeReferralService;

    @Autowired
    public OfficeReferralController(OfficeReferralService officeReferralService) {
        this.officeReferralService = officeReferralService;
    }

    @GetMapping("/punishments")
    public ResponseEntity<List<OfficeReferral>> getAll() {
        var message = officeReferralService.findAll();
        return ResponseEntity
                .accepted()
                .body(message);
    }
    @GetMapping("/{id}")
    public ResponseEntity<OfficeReferral> getByReferralId(@PathVariable String id) throws ResourceNotFoundException {
        var message = officeReferralService.findByReferralId(id);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/{email}")
    public ResponseEntity<List<OfficeReferral>> getByAdminEmail(@PathVariable String email) throws ResourceNotFoundException {
        var message = officeReferralService.findByAdminEmail(email);

        return ResponseEntity
                .accepted()
                .body(message);
    }
    @PostMapping("/startPunish/adminReferral")
    public ResponseEntity<List<OfficeReferral>> createNewAdminReferralBulk(@RequestBody List<OfficeReferralRequest> officeReferralListRequest) throws MessagingException, IOException, InterruptedException {
        var message = officeReferralService.createNewAdminReferralBulk(officeReferralListRequest);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PostMapping("/closeId")
    public ResponseEntity<OfficeReferralResponse> closeByReferralId(@RequestBody String id) throws ResourceNotFoundException, MessagingException {
        var message = officeReferralService.closeByReferralId(id);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PostMapping("/submit/{id}")
    public ResponseEntity<OfficeReferralResponse> submitByReferralId(@PathVariable String id) throws ResourceNotFoundException, MessagingException {
        var message = officeReferralService.submitByReferralId(id);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PutMapping("/{id}/index/{index}")
    public ResponseEntity<OfficeReferral> updateMapIndex(@PathVariable String id, @PathVariable int index) throws ResourceNotFoundException {
        var message = officeReferralService.updateMapIndex(id,index);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PutMapping("/rejected/{punishmentId}")
    public ResponseEntity<OfficeReferral> rejectAnswers(@PathVariable String punishmentId,
                                                       @RequestBody String description) throws MessagingException {
        OfficeReferral response = officeReferralService.rejectAnswers(punishmentId, description);
        return ResponseEntity
                .accepted()
                .body(response);
    }
}
