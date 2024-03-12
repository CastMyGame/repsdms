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

import java.util.List;


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
    @Autowired
    PunishmentService punishmentService;

    //-------------------------------------GET Controllers-------------------------------
    @GetMapping("/punishments")
    public ResponseEntity<List<Punishment>> getAll() {
        var message = punishmentService.findAll();
        return ResponseEntity
                .accepted()
                .body(message);
    }
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

    @GetMapping("/writeUps")
    public ResponseEntity<List<Punishment>> getPunishmentWriteUps() {
        List<Punishment> response = punishmentService.getAllReferrals();
        return ResponseEntity
                .accepted()
                .body(response);
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

    @PostMapping("/startPunish/form")
    public ResponseEntity<PunishmentResponse> createNewFormPunish(@RequestBody PunishmentFormRequest punishmentFormRequest) {
        var message = punishmentService.createNewPunishForm(punishmentFormRequest);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PostMapping("/startPunish/formList")
    public ResponseEntity<List<PunishmentResponse>> createNewFormPunishBulk(@RequestBody List<PunishmentFormRequest> punishmentListRequest) {
        var message = punishmentService.createNewPunishFormBulk(punishmentListRequest);

        return ResponseEntity
                .accepted()
                .body(message);
    }

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

    //----------------------------DELETE Controllers------------------------------
    @DeleteMapping("/delete")
    public ResponseEntity<String> deletePunishment (@RequestBody Punishment punishment) throws ResourceNotFoundException {
        var delete = punishmentService.deletePunishment(punishment);
        return ResponseEntity
                .accepted()
                .body(delete);
    }
}
