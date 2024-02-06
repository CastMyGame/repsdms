package com.reps.demogcloud.controllers;


import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.services.PunishmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@CrossOrigin
@RequestMapping("/punish/v1")
public class PunishController {
    @Autowired
    PunishmentService punishmentService;

    @GetMapping("/punishId")
    public ResponseEntity<Punishment> getByPunishId(@RequestBody Punishment punishment) throws ResourceNotFoundException {
        var message = punishmentService.findByPunishmentId(punishment);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/{infractionName}")
    public ResponseEntity<List<Punishment>> getByInfraction(@PathVariable String infractionName) throws ResourceNotFoundException {
        var message = punishmentService.findByInfractionName(infractionName);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/punishId/close")
    public ResponseEntity<PunishmentResponse> closePunishment(@RequestBody ClosePunishmentRequest closePunishmentRequest) throws ResourceNotFoundException {
        System.out.println(closePunishmentRequest);
        var message = punishmentService.closePunishment(closePunishmentRequest.getInfractionName(), closePunishmentRequest.getStudentEmail(), closePunishmentRequest.getStudentAnswer());

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PostMapping("/close/{id}")
    public ResponseEntity<PunishmentResponse> closeByPunishmentId(@PathVariable String id) throws ResourceNotFoundException {
        var message = punishmentService.closeByPunishmentId(id);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PostMapping("/ftc-close")
    public ResponseEntity<PunishmentResponse> closeFailureToComplete(@RequestBody CloseFailureToComplete closeFailureToComplete) throws ResourceNotFoundException {
        var message = punishmentService.closeFailureToComplete(closeFailureToComplete.getInfractionName(), closeFailureToComplete.getStudentEmail(), closeFailureToComplete.getTeacherEmail());
        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/punishments")
    public ResponseEntity<List<Punishment>> getAll() {
        var message = punishmentService.findAll();
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

    @PostMapping("/startPunish")
    public ResponseEntity<PunishmentResponse> createNewPunish(@RequestBody PunishmentRequest punishmentRequest) {
        var message = punishmentService.createNewPunish(punishmentRequest);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deletePunishment (@RequestBody Punishment punishment) throws ResourceNotFoundException {
        var delete = punishmentService.deletePunishment(punishment);
        return ResponseEntity
                .accepted()
                .body(delete);
    }

//    @PutMapping("/edit")
//    public ResponseEntity<PunishRequestCommand> editInfraction (@RequestBody PunishRequestCommand requestCommand) {
//        var edit = punishmentService.createNewPunish(requestCommand);
//        return ResponseEntity
//                .accepted()
//                .body(edit);
//    }

    @CrossOrigin
    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping("/startPunish/form")
    public ResponseEntity<PunishmentResponse> createNewFormPunish(@RequestBody PunishmentFormRequest punishmentFormRequest) {
        var message = punishmentService.createNewPunishForm(punishmentFormRequest);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @CrossOrigin
    @PostMapping("/startPunish/formList")
    public ResponseEntity<List<PunishmentResponse>> createNewFormPunishBulk(@RequestBody List<PunishmentFormRequest> punishmentListRequest) {
        var message = punishmentService.createNewPunishFormBulk(punishmentListRequest);

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

    @GetMapping("/compare")
    public ResponseEntity<List<Punishment>> getOpenForADay() {
        List<Punishment> message = punishmentService.getAllOpenForADay();

        return ResponseEntity
                .accepted()
                .body(message);
    }

//    @PostMapping("/updateLevelThree")
//    public ResponseEntity<Punishment> updateLevelThree(@RequestBody LevelThreeCloseRequest levelThreeCloseRequest) throws ResourceNotFoundException {
//        Punishment updated = punishmentService.updateLevelThreeCloseRequest(levelThreeCloseRequest);
//
//        return ResponseEntity
//                .accepted()
//                .body(updated);
//    }

    @PostMapping("/studentsReport/{studentEmail}")
    public ResponseEntity<List<Punishment>> getAllPunishmentsForStudents(@PathVariable String studentEmail) {
        List<Punishment> message = punishmentService.getAllPunishmentsForStudents(studentEmail);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/student/punishments/{studentEmail}")
    public ResponseEntity<List<Punishment>> getAllPunishmentByStudentEmail(@PathVariable String studentEmail) {
        List<Punishment> message = punishmentService.getAllPunishmentByStudentEmail(studentEmail);

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

    @PutMapping("/archived/{userId}/{punishmentId}")
    public ResponseEntity<Punishment> archivedDeleted(@PathVariable String punishmentId, @PathVariable String userId, @RequestBody String explaination ) {
        Punishment response = punishmentService.archiveRecord(punishmentId,userId,explaination);
        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PutMapping("/archived/restore/{punishmentId}")
    public ResponseEntity<Punishment> restoreArchivedDeleted(@PathVariable String punishmentId) {
        Punishment response = punishmentService.restoreRecord(punishmentId);
        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PutMapping("/rejected/{punishmentId}")
    public ResponseEntity<Punishment> rejectLevelThree(@PathVariable String punishmentId,
                                                       @RequestBody String description) {
        Punishment response = punishmentService.rejectLevelThree(punishmentId, description);
        return ResponseEntity
                .accepted()
                .body(response);
    }

    @GetMapping("/writeUps")
    public ResponseEntity<List<Punishment>> getPunishmentWriteUps() {
        List<Punishment> response = punishmentService.getPunishmentWriteUps();
        return ResponseEntity
                .accepted()
                .body(response);
    }
}
