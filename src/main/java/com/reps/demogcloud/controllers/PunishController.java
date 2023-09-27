package com.reps.demogcloud.controllers;


import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.services.PunishmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/punishId/close")
    public ResponseEntity<PunishmentResponse> closePunishment(@RequestBody ClosePunishmentRequest closePunishmentRequest) throws ResourceNotFoundException {
        var message = punishmentService.closePunishment(closePunishmentRequest.getInfractionName(), closePunishmentRequest.getStudentEmail());

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

//    @GetMapping("/student")
//    public ResponseEntity<List<Punishment>> getByStudent(@RequestBody PunishmentRequest punishmentRequest) throws ResourceNotFoundException {
//        var message = punishmentService.findByStudent(punishmentRequest);
//
//        return ResponseEntity
//                .accepted()
//                .body(message);
//    }

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
    @PostMapping("/startPunish/form")
    public ResponseEntity<PunishmentResponse> createNewFormPunish(@RequestBody PunishmentFormRequest punishmentFormRequest) {
        var message = punishmentService.createNewPunishForm(punishmentFormRequest);

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
        var message = punishmentService.getAllOpenForADay();

        return ResponseEntity
                .accepted()
                .body(message);
    }
}
