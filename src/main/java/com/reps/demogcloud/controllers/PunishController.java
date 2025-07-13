package com.reps.demogcloud.controllers;


import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.services.PunishmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.mail.MessagingException;
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
@RequestMapping("/punish/v1")
public class PunishController {
    private final PunishmentService punishmentService;
    //-------------------------------------GET Controllers-------------------------------
    @GetMapping("/punishments")
    public ResponseEntity<List<Punishment>> getAll() {
        var message = punishmentService.findAll();
        return ResponseEntity.ok(message);
    }
    @GetMapping("/{id}")
    public ResponseEntity<Punishment> getByPunishId(@PathVariable String id) throws ResourceNotFoundException {
        var message = punishmentService.findByPunishmentId(id);

        return ResponseEntity.ok(message);
    }

    @GetMapping("/punishStatus/{status}")
    public ResponseEntity<List<Punishment>> getByStatus(@PathVariable String status) throws ResourceNotFoundException {
        var message = punishmentService.findByStatus(status);

        return ResponseEntity.ok(message);
    }

    @GetMapping("/student/{email}")
    public ResponseEntity<List<Punishment>> getByStudentEmailAndFailureToCompleteAssignments(@PathVariable String email) throws ResourceNotFoundException {
        var message = punishmentService.findByStudentEmailAndInfraction(email,"Failure to Complete Work");

        return ResponseEntity.ok(message);
    }

    @GetMapping("/openPunishments")
    public ResponseEntity<List<Punishment>> getOpenPunishments() {
        var message = punishmentService.getAllOpenAssignments();

        return ResponseEntity.ok(message);
    }

    @GetMapping("/archived")
    public ResponseEntity<List<Punishment>> getAllArchived() {
        List<Punishment> message = punishmentService.findAllPunishmentIsArchived(true);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/punishments/{studentEmail}")
    public ResponseEntity<List<Punishment>> getPunishmentForStudent(@PathVariable String studentEmail){
        List<Punishment> response = punishmentService.getAllPunishmentForStudent(studentEmail);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/student/punishments/{studentEmail}")
    public ResponseEntity<List<Punishment>> getAllPunishmentByStudentEmail(@PathVariable String studentEmail) {
        List<Punishment> message = punishmentService.getAllPunishmentByStudentEmail(studentEmail);

        return ResponseEntity.ok(message);
    }

    //-----------------------------POST Controllers---------------------------
    @PostMapping("/punishId/close")
    public ResponseEntity<PunishmentResponse> closePunishment(@RequestBody ClosePunishmentRequest closePunishmentRequest) throws ResourceNotFoundException, MessagingException {
        System.out.println(closePunishmentRequest);
        var message = punishmentService.closePunishment(closePunishmentRequest.getInfractionName(), closePunishmentRequest.getStudentEmail(), closePunishmentRequest.getStudentAnswer());

        return ResponseEntity.ok(message);
    }

    @PostMapping("/close/{id}")
    public ResponseEntity<PunishmentResponse> closeByPunishmentId(@PathVariable String id) throws ResourceNotFoundException, MessagingException {
        var message = punishmentService.closeByPunishmentId(id);

        return ResponseEntity.ok(message);
    }

    @PostMapping("/startPunish/form")
    public ResponseEntity<PunishmentResponse> createNewFormPunish(@RequestBody PunishmentFormRequest punishmentFormRequest) throws MessagingException {
        var message = punishmentService.createNewPunishForm(punishmentFormRequest);

        return ResponseEntity.ok(message);
    }

    @PostMapping("/startPunish/formList")
    public ResponseEntity<List<PunishmentResponse>> createNewFormPunishBulk(@RequestBody List<PunishmentFormRequest> punishmentListRequest) throws MessagingException {
        var message = punishmentService.createNewPunishFormBulk(punishmentListRequest);

        return ResponseEntity.ok(message);
    }

//    @GetMapping("/guidance/{status}/{userFilter}")
//    public ResponseEntity<List<Guidance>> getAllGuidances(@PathVariable String status,@PathVariable  boolean userFilter) {
//
//        List<Guidance> message = punishmentService.getAllGuidanceReferrals(status,userFilter);
//
//        return ResponseEntity
//                .accepted()
//                .body(message);
//    }

    @PostMapping("/studentsReport/{studentEmail}")
    public ResponseEntity<List<Punishment>> getAllPunishmentsForStudents(@PathVariable String studentEmail) {
        List<Punishment> message = punishmentService.getAllPunishmentsForStudents(studentEmail);

        return ResponseEntity.ok(message);
    }

    //--------------------------PUT Controllers--------------------------

    @PutMapping("/{id}/index/{index}")
    public ResponseEntity<Punishment> updateMapIndex(@PathVariable String id, @PathVariable int index) throws ResourceNotFoundException {
        var message = punishmentService.updateMapIndex(id,index);

        return ResponseEntity.ok(message);
    }

    @PutMapping("/archived/{userId}/{punishmentId}")
    public ResponseEntity<Punishment> archivedDeleted(@PathVariable String punishmentId, @PathVariable String userId, @RequestBody String explanation ) throws MessagingException {
        Punishment p = punishmentService.archiveRecord(punishmentId,userId,explanation);
        System.out.println("Returned Punishment: " + p); // <-- Add this
        return ResponseEntity.ok(p);
    }

    @PutMapping("/archived/restore/{punishmentId}")
    public ResponseEntity<Punishment> restoreArchivedDeleted(@PathVariable String punishmentId) throws MessagingException {
        Punishment response = punishmentService.restoreRecord(punishmentId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/rejected/{punishmentId}")
    public ResponseEntity<Punishment> rejectLevelThree(@PathVariable String punishmentId) throws MessagingException {
        Punishment response = punishmentService.rejectLevelThree(punishmentId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/updates")
    public ResponseEntity<List<Punishment>> updateAllFix() {
        List<Punishment> response = punishmentService.updateTimeCreated();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/descriptions")
    public ResponseEntity<List<Punishment>> updateAllDescriptions() {
        List<Punishment> response = punishmentService.updateDescriptions();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/emails")
    public ResponseEntity<List<Punishment>> updateAllStudentEmails() {
        List<Punishment> response = punishmentService.updateStudentEmails();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/schoolName")
    public ResponseEntity<List<Punishment>> updateAllSchools() {
        List<Punishment> response = punishmentService.updateSchools();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/infractionName")
    public ResponseEntity<List<Punishment>> updateAllInfractionName() {
        List<Punishment> response = punishmentService.updateInfractionName();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/infractionLevel")
    public ResponseEntity<List<Punishment>> updateAllInfractionLevel() {
        List<Punishment> response = punishmentService.updateInfractionLevel();

        return ResponseEntity.ok(response);
    }


    //----------------------------DELETE Controllers------------------------------
    @DeleteMapping("/delete")
    public ResponseEntity<String> deletePunishment (@RequestBody Punishment punishment) throws ResourceNotFoundException {
        var delete = punishmentService.deletePunishment(punishment);
        return ResponseEntity.ok(delete);
    }
}
