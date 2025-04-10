package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.guidance.GuidanceReferral;
import com.reps.demogcloud.models.guidance.GuidanceRequest;
import com.reps.demogcloud.models.guidance.GuidanceResponse;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.ResourceUpdateRequest;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import com.reps.demogcloud.services.GuidanceService;
import com.reps.demogcloud.services.PunishmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.mail.MessagingException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(
        origins = {
                "http://localhost:3000/",
                "https://repsdiscipline.vercel.app",
                "https://repsdev.vercel.app"
        }
)
@RequestMapping("/guidance/v1")
public class GuidanceController {
        PunishmentService punishmentService;
        GuidanceService guidanceService;

        @GetMapping("/referrals")
        public ResponseEntity<List<GuidanceReferral>> getAll() {
                var message = guidanceService.findAll();
                return ResponseEntity
                        .accepted()
                        .body(message);
        }

        @GetMapping("/guidanceStatus/{status}")
        public ResponseEntity<List<GuidanceReferral>> getByStatus(@PathVariable String status) throws ResourceNotFoundException {
                var message = guidanceService.findByStatus(status);

                return ResponseEntity
                        .accepted()
                        .body(message);
        }

        @PostMapping("/guidance/new")
        public ResponseEntity<GuidanceResponse> createNewGuidance(@RequestBody GuidanceRequest guidanceRequests) {
                var message = guidanceService.createNewGuidanceFormSimple(guidanceRequests);

                return ResponseEntity
                        .accepted()
                        .body(message);
        }

        @PutMapping("/guidance/notes/{id}")
        public ResponseEntity<GuidanceReferral> updateGuidance(@PathVariable String id, @RequestBody ThreadEvent event) {

                var message = guidanceService.updateGuidance(id,event);

                return ResponseEntity
                        .accepted()
                        .body(message);
        }



        @PutMapping("/guidance/followup/{id}")
        public ResponseEntity<GuidanceReferral> updateGuidanceFollowUp(@PathVariable String id, @RequestBody Map<String, String> payload) {

                String scheduleFollowUp = payload.get("followUpDate");
                String newStatus = payload.get("status");

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

                LocalDate followUpDate;
                try {
                        followUpDate = LocalDate.parse(scheduleFollowUp, formatter);
                } catch (DateTimeParseException e) {
                        return ResponseEntity.badRequest().body(null);  // or handle the error as appropriate
                }
                GuidanceReferral updatedGuidance = guidanceService.updateGuidanceFollowUp(id, followUpDate,newStatus);

                return ResponseEntity.accepted().body(updatedGuidance);
        }

        @PutMapping("/guidance/status/{id}")
        public ResponseEntity<GuidanceReferral> updateGuidanceStatus(@PathVariable String id, @RequestBody Map<String, String> payload) {

                String newStatus = payload.get("status");
                GuidanceReferral updatedPunishment = guidanceService.updateGuidanceStatus(id, newStatus);

                return ResponseEntity.accepted().body(updatedPunishment);
        }

        @PutMapping("/guidance/resources/{id}")
        public ResponseEntity<GuidanceResponse> updateAndSendResources(@PathVariable String id, @RequestBody ResourceUpdateRequest request) throws MessagingException {
                var message = guidanceService.sendResourcesAndMakeNotes(id, request);

                return ResponseEntity
                        .accepted()
                        .body(message);
        }

        @DeleteMapping("/guidance/delete/{id}")
        public ResponseEntity<String> deleteGuidanceReferral (@PathVariable String id) throws ResourceNotFoundException {
                var delete = guidanceService.deleteGuidanceReferral(id);
                return ResponseEntity
                        .accepted()
                        .body(delete);
        }

}
