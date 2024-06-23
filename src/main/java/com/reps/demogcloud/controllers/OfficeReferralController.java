package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.officeReferral.OfficeReferralRequest;
import com.reps.demogcloud.models.officeReferral.OfficeReferralResponse;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.services.OfficeReferralService;
import lombok.RequiredArgsConstructor;
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
                "https://repsdiscipline.vercel.app"
        }
)
@RequestMapping("/officeReferral/v1")
public class OfficeReferralController {

    OfficeReferralService officeReferralService;
    @PostMapping("/startPunish/adminReferral")
    public ResponseEntity<List<OfficeReferral>> createNewAdminReferralBulk(@RequestBody List<OfficeReferralRequest> officeReferralListRequest) throws MessagingException, IOException, InterruptedException {
        var message = officeReferralService.createNewAdminReferralBulk(officeReferralListRequest);

        return ResponseEntity
                .accepted()
                .body(message);
    }
}
