package com.reps.demogcloud.controllers;


import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.services.PunishmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.mail.MessagingException;
import java.io.IOException;
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
                "https://repsdiscipline.vercel.app"
        }
)
@RequestMapping("/punish/v1")
public class PunishController {
    PunishmentService punishmentService;

    @Autowired
    public PunishController(PunishmentService punishmentService) {
        this.punishmentService = punishmentService;
    }

    //-------------------------------------GET Controllers-------------------------------
    @GetMapping("/punishments")
    public ResponseEntity<List<Punishment>> getAll() {
        var message = punishmentService.findAll();
        return ResponseEntity
                .accepted()
                .body(message);
    }
    @GetMapping("/{id}")
    public ResponseEntity<Punishment> getByPunishId(@PathVariable String id) throws ResourceNotFoundException {
        var message = punishmentService.findByPunishmentId(id);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/punishStatus/{status}")
    public ResponseEntity<List<Punishment>> getByStatus(@PathVariable String status) throws ResourceNotFoundException {
        var message = punishmentService.findByStatus(status);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/student/{email}")
    public ResponseEntity<List<Punishment>> getByStudentEmailAndFailureToCompleteAssignments(@PathVariable String email) throws ResourceNotFoundException {
        var message = punishmentService.findByStudentEmailAndInfraction(email,"Failure to Complete Work");

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/openPunishments")
    public ResponseEntity<List<Punishment>> getOpenPunishments() {
        var message = punishmentService.getAllOpenAssignments();

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/archived")
    public ResponseEntity<List<Punishment>> getAllArchived() {
        List<Punishment> message = punishmentService.findAllPunishmentIsArchived(true);
        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/punishments/{studentEmail}")
    public ResponseEntity<List<Punishment>> getPunishmentForStudent(@PathVariable String studentEmail){
        List<Punishment> response = punishmentService.getAllPunishmentForStudent(studentEmail);
        return ResponseEntity
                .accepted()
                .body(response);
    }

    @GetMapping("/student/punishments/{studentEmail}")
    public ResponseEntity<List<Punishment>> getAllPunishmentByStudentEmail(@PathVariable String studentEmail) {
        List<Punishment> message = punishmentService.getAllPunishmentByStudentEmail(studentEmail);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    //-----------------------------POST Controllers---------------------------
    @PostMapping("/punishId/close")
    public ResponseEntity<PunishmentResponse> closePunishment(@RequestBody ClosePunishmentRequest closePunishmentRequest) throws ResourceNotFoundException, MessagingException {
        System.out.println(closePunishmentRequest);
        var message = punishmentService.closePunishment(closePunishmentRequest.getInfractionName(), closePunishmentRequest.getStudentEmail(), closePunishmentRequest.getStudentAnswer());

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PostMapping("/close/{id}")
    public ResponseEntity<PunishmentResponse> closeByPunishmentId(@PathVariable String id) throws ResourceNotFoundException, MessagingException {
        var message = punishmentService.closeByPunishmentId(id);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PostMapping("/startPunish/form")
    public ResponseEntity<PunishmentResponse> createNewFormPunish(@RequestBody PunishmentFormRequest punishmentFormRequest) throws MessagingException, IOException, InterruptedException {
        var message = punishmentService.createNewPunishForm(punishmentFormRequest);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PostMapping("/startPunish/formList")
    public ResponseEntity<List<PunishmentResponse>> createNewFormPunishBulk(@RequestBody List<PunishmentFormRequest> punishmentListRequest) throws MessagingException, IOException, InterruptedException {
        var message = punishmentService.createNewPunishFormBulk(punishmentListRequest);

        return ResponseEntity
                .accepted()
                .body(message);
    }

//    @PostMapping("/guidance/formList")
//    public ResponseEntity<List<PunishmentResponse>> startGuidence(@RequestBody List<PunishmentFormRequest> punishmentListRequest) throws MessagingException, IOException, InterruptedException {
//        var message = punishmentService.createGuidance(punishmentListRequest);
//
//        return ResponseEntity
//                .accepted()
//                .body(message);
//    }

    @PutMapping("/guidance/notes/{id}")
    public ResponseEntity<Punishment> updateGuidance(@PathVariable String id,@RequestBody ThreadEvent event) throws MessagingException, IOException, InterruptedException {
        var message = punishmentService.updateGuidance(id,event);

        return ResponseEntity
                .accepted()
                .body(message);
    }



    @PutMapping("/guidance/followup/{id}")
    public ResponseEntity<Punishment> updateGuidanceFollowUp(@PathVariable String id, @RequestBody Map<String, String> payload) throws MessagingException, IOException, InterruptedException {
        String scheduleFollowUp = payload.get("followUpDate");
        String statusChange = payload.get("guidanceStatus");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        LocalDate followUpDate;
        try {
            followUpDate = LocalDate.parse(scheduleFollowUp, formatter);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(null);  // or handle the error as appropriate
        }
        Punishment updatedPunishment = punishmentService.updateGuidanceFollowUp(id, followUpDate,statusChange);

        return ResponseEntity.accepted().body(updatedPunishment);
    }

    @PutMapping("/guidance/status/{id}")
    public ResponseEntity<Punishment> updateGuidanceStatus (@PathVariable String id, @RequestBody Map<String, String> payload) throws MessagingException, IOException, InterruptedException {
        String newStatus = payload.get("status");
        Punishment updatedPunishment = punishmentService.updateGuidanceStatus(id, newStatus);

        return ResponseEntity.accepted().body(updatedPunishment);
    }

    @GetMapping("/guidance/{status}")
    public ResponseEntity<List<Punishment>> getAllGuidances(@PathVariable String status) throws MessagingException, IOException, InterruptedException {
        List<Punishment> message = punishmentService.getAllGuidanceReferrals(status);

        return ResponseEntity
                .accepted()
                .body(message);
    }

//    @PostMapping("/startPunish/adminReferral")
//    public ResponseEntity<List<PunishmentResponse>> createNewAdminReferralBulk(@RequestBody List<PunishmentFormRequest> adminReferralListRequest) throws MessagingException, IOException, InterruptedException {
//        var message = punishmentService.createNewAdminReferralBulk(adminReferralListRequest);
//
//        return ResponseEntity
//                .accepted()
//                .body(message);
//    }

    @PostMapping("/studentsReport/{studentEmail}")
    public ResponseEntity<List<Punishment>> getAllPunishmentsForStudents(@PathVariable String studentEmail) {
        List<Punishment> message = punishmentService.getAllPunishmentsForStudents(studentEmail);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    //--------------------------PUT Controllers--------------------------

    @PutMapping("/{id}/index/{index}")
    public ResponseEntity<Punishment> updateMapIndex(@PathVariable String id, @PathVariable int index) throws ResourceNotFoundException {
        var message = punishmentService.updateMapIndex(id,index);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PutMapping("/archived/{userId}/{punishmentId}")
    public ResponseEntity<Punishment> archivedDeleted(@PathVariable String punishmentId, @PathVariable String userId, @RequestBody String explanation ) throws MessagingException {
        Punishment response = punishmentService.archiveRecord(punishmentId,userId,explanation);
        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PutMapping("/archived/restore/{punishmentId}")
    public ResponseEntity<Punishment> restoreArchivedDeleted(@PathVariable String punishmentId) throws MessagingException {
        Punishment response = punishmentService.restoreRecord(punishmentId);
        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PutMapping("/rejected/{punishmentId}")
    public ResponseEntity<Punishment> rejectLevelThree(@PathVariable String punishmentId,
                                                       @RequestBody String description) throws MessagingException {
        Punishment response = punishmentService.rejectLevelThree(punishmentId, description);
        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PutMapping("/updates")
    public ResponseEntity<List<Punishment>> updateAllFix() {
        List<Punishment> response = punishmentService.updateTimeCreated();

        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PutMapping("/descriptions")
    public ResponseEntity<List<Punishment>> updateAllDescriptions() {
        List<Punishment> response = punishmentService.updateDescriptions();

        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PutMapping("/emails")
    public ResponseEntity<List<Punishment>> updateAllStudentEmails() {
        List<Punishment> response = punishmentService.updateStudentEmails();

        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PutMapping("/schoolName")
    public ResponseEntity<List<Punishment>> updateAllSchools() {
        List<Punishment> response = punishmentService.updateSchools();

        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PutMapping("/infractionName")
    public ResponseEntity<List<Punishment>> updateAllInfractionName() {
        List<Punishment> response = punishmentService.updateInfractionName();

        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PutMapping("/infractionLevel")
    public ResponseEntity<List<Punishment>> updateAllInfractionLevel() {
        List<Punishment> response = punishmentService.updateInfractionLevel();

        return ResponseEntity
                .accepted()
                .body(response);
    }

    //----------------------------DELETE Controllers------------------------------
    @DeleteMapping("/delete")
    public ResponseEntity<String> deletePunishment (@RequestBody Punishment punishment) throws ResourceNotFoundException {
        var delete = punishmentService.deletePunishment(punishment);
        return ResponseEntity
                .accepted()
                .body(delete);
    }
}
