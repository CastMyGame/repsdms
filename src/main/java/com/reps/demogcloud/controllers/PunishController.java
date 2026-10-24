package com.reps.demogcloud.controllers;


import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.services.PunishmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.mail.MessagingException;
import java.util.List;

@Slf4j
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
    private final com.reps.demogcloud.services.WorkflowAccessService access;
    private final com.reps.demogcloud.services.UserContextService users;
    private List<Punishment> visible(List<Punishment> records) {
        return records.stream().filter(access::canReadCase).toList();
    }
    //-------------------------------------GET Controllers-------------------------------
    @GetMapping("/punishments")
    public ResponseEntity<List<Punishment>> getAll() {
        access.requireStaff();
        var message = punishmentService.findAll();
        return ResponseEntity.ok(visible(message));
    }

    @GetMapping("/archived")
    public ResponseEntity<List<Punishment>> getAllArchived() {
        access.requireStaff();
        List<Punishment> message = punishmentService.findAllPunishmentArchived(true);
        return ResponseEntity.ok(visible(message));
    }

    @GetMapping("/student/punishments/{studentEmail}")
    public ResponseEntity<List<Punishment>> getAllPunishmentByStudentEmail(@PathVariable String studentEmail) {
        access.requireStudentAccess(studentEmail);
        List<Punishment> message = punishmentService.getAllPunishmentByStudentEmail(studentEmail);

        return ResponseEntity.ok(visible(message));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Punishment> getByPunishId(@PathVariable String id) throws ResourceNotFoundException {
        access.requireCase(id);
        var message = punishmentService.findByPunishmentId(id);

        return ResponseEntity.ok(message);
    }

    @GetMapping("/punishStatus/{status}")
    public ResponseEntity<List<Punishment>> getByStatus(@PathVariable String status) throws ResourceNotFoundException {
        access.requireStaff();
        var message = punishmentService.findByStatus(status);

        return ResponseEntity.ok(visible(message));
    }

    @GetMapping("/student/{email}")
    public ResponseEntity<List<Punishment>> getByStudentEmailAndFailureToCompleteAssignments(@PathVariable String email) throws ResourceNotFoundException {
        access.requireStudentAccess(email);
        var message = punishmentService.findByStudentEmailAndInfraction(email,"Failure to Complete Work");

        return ResponseEntity.ok(visible(message));
    }

    @GetMapping("/openPunishments")
    public ResponseEntity<List<Punishment>> getOpenPunishments() {
        access.requireStaff();
        var message = punishmentService.getAllOpenAssignments();

        return ResponseEntity.ok(visible(message));
    }

    @GetMapping("/punishments/{studentEmail}")
    public ResponseEntity<List<Punishment>> getPunishmentForStudent(@PathVariable String studentEmail){
        access.requireStudentAccess(studentEmail);
        List<Punishment> response = punishmentService.getAllPunishmentForStudent(studentEmail);
        return ResponseEntity.ok(visible(response));
    }

    //-----------------------------POST Controllers---------------------------
    @PostMapping("/punishId/close")
    public ResponseEntity<PunishmentResponse> closePunishment(@RequestBody ClosePunishmentRequest closePunishmentRequest) throws ResourceNotFoundException, MessagingException {
        var message = punishmentService.closePunishment(closePunishmentRequest.getInfractionName(), closePunishmentRequest.getStudentEmail(), closePunishmentRequest.getStudentAnswer());

        return ResponseEntity.ok(message);
    }

    @Deprecated
    @PostMapping("/close/{id}")
    public ResponseEntity<PunishmentResponse> closeByPunishmentId(@PathVariable String id) throws ResourceNotFoundException, MessagingException {
        access.requireStaff(); access.requireCase(id);
        var message = punishmentService.closeByPunishmentId(id);

        return ResponseEntity.ok(message);
    }

@Deprecated
    @PostMapping("/startPunish/form")
    public ResponseEntity<PunishmentResponse> createNewFormPunish(@RequestBody PunishmentFormRequest punishmentFormRequest) throws MessagingException {
        access.requireStaff();
        punishmentFormRequest.setTeacherEmail(users.getCurrentStaffEmail());
        var message = punishmentService.createNewPunishForm(punishmentFormRequest);

        return ResponseEntity.ok(message);
    }

    @PostMapping("/startPunish/formList")
    public ResponseEntity<List<PunishmentResponse>> createNewFormPunishBulk(@RequestBody List<PunishmentFormRequest> punishmentListRequest) throws MessagingException {
        access.requireStaff();
        for(var form:punishmentListRequest) { form.setTeacherEmail(users.getCurrentStaffEmail()); }
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
        access.requireStudentAccess(studentEmail);
        List<Punishment> message = punishmentService.getAllPunishmentsForStudents(studentEmail);

        return ResponseEntity.ok(visible(message));
    }

    //--------------------------PUT Controllers--------------------------

    @PutMapping("/{id}/index/{index}")
    public ResponseEntity<Punishment> updateMapIndex(@PathVariable String id, @PathVariable int index) throws ResourceNotFoundException {
        var message = punishmentService.updateMapIndex(id,index);

        return ResponseEntity.ok(message);
    }

    @PutMapping("/archived/{userId}/{punishmentId}")
    public ResponseEntity<Punishment> archivedDeleted(@PathVariable String punishmentId, @PathVariable String userId, @RequestBody String explanation ) throws MessagingException {
        access.requireStaff(); access.requireCase(punishmentId);
        Punishment p = punishmentService.archiveRecord(punishmentId,userId,explanation);
        return ResponseEntity.ok(p);
    }

    @PutMapping("/archived/restore/{punishmentId}")
    public ResponseEntity<Punishment> restoreArchivedDeleted(@PathVariable String punishmentId) throws MessagingException {
        access.requireStaff(); access.requireCase(punishmentId);
        Punishment response = punishmentService.restoreRecord(punishmentId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/rejected/{punishmentId}")
    public ResponseEntity<Punishment> rejectLevelThree(@PathVariable String punishmentId) throws MessagingException {
        access.requireStaff(); access.requireCase(punishmentId);
        Punishment response = punishmentService.rejectLevelThree(punishmentId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/updates")
    public ResponseEntity<List<Punishment>> updateAllFix() {
        access.denyLegacyWrite();
        List<Punishment> response = punishmentService.updateTimeCreated();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/descriptions")
    public ResponseEntity<List<Punishment>> updateAllDescriptions() {
        access.denyLegacyWrite();
        List<Punishment> response = punishmentService.updateDescriptions();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/emails")
    public ResponseEntity<List<Punishment>> updateAllStudentEmails() {
        access.denyLegacyWrite();
        List<Punishment> response = punishmentService.updateStudentEmails();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/schoolName")
    public ResponseEntity<List<Punishment>> updateAllSchools() {
        access.denyLegacyWrite();
        List<Punishment> response = punishmentService.updateSchools();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/infractionName")
    public ResponseEntity<List<Punishment>> updateAllInfractionName() {
        access.denyLegacyWrite();
        List<Punishment> response = punishmentService.updateInfractionName();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/infractionLevel")
    public ResponseEntity<List<Punishment>> updateAllInfractionLevel() {
        access.denyLegacyWrite();
        List<Punishment> response = punishmentService.updateInfractionLevel();

        return ResponseEntity.ok(response);
    }


    //----------------------------DELETE Controllers------------------------------
    @DeleteMapping("/delete")
    public ResponseEntity<String> deletePunishment (@RequestBody Punishment punishment) throws ResourceNotFoundException {
        access.denyLegacyWrite();
        var delete = punishmentService.deletePunishment(punishment);
        return ResponseEntity.ok(delete);
    }
}
