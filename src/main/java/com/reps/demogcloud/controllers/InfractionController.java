package com.reps.demogcloud.controllers;

import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.services.InfractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("infraction/v1")
@RequiredArgsConstructor
public class InfractionController {
    private final InfractionService infractionService;

    //---------------------------GET Controllers------------------------------
    @GetMapping("/all")
    public ResponseEntity<List<Infraction>> findAllInfractions() {
        var message = infractionService.findAllInfractions();
        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/infractionId/{infractionId}")
    public ResponseEntity<Infraction> getInfractionById(@PathVariable String infractionId) {
        try {
            var findMe = infractionService.findByInfractionId(infractionId);
            return ResponseEntity.accepted().body(findMe);
        } catch (ResourceNotFoundException ex) {
            // Handle the ResourceNotFoundException and return an error response
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Infraction()); // or null or an appropriate error response
        } catch (Exception ex) {
            // Handle other exceptions and return an appropriate error response
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new Infraction()); // or null or an appropriate error response
        }
    }

    @GetMapping("/infractionName/{infractionName}")
    public ResponseEntity<Infraction> getInfractionByName (@PathVariable String infractionName) throws ResourceNotFoundException {
        var findMe = infractionService.findInfractionByInfractionName(infractionName);

        return ResponseEntity
                .accepted()
                .body(findMe);
    }
    /** Fail closed for unsupported bulk, update and legacy mutation paths too. */
    @RequestMapping(value={"", "/**"}, method={RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public void catalogEditingUnavailable() {
        throw com.reps.demogcloud.services.infraction.InfractionMutationService.editingUnavailable();
    }
}
