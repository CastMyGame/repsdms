package com.reps.demogcloud.controllers;

;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.services.InfractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@CrossOrigin
@RequestMapping("infraction/v1")
public class InfractionController {
    private final InfractionService infractionService;

    public InfractionController(InfractionService infractionService) {
        this.infractionService = infractionService;
    }


    @GetMapping("/all")
    public ResponseEntity<List<Infraction>> findAllInfractions() {
        var message = infractionService.findAllInfractions();

        return ResponseEntity
                .accepted()
                .body(message);
    }
    @PostMapping("/createInfraction")
    public ResponseEntity<Infraction> createNewInfraction(@RequestBody Infraction infraction) {
        var message = infractionService.createNewInfraction(infraction);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/infractionId/{infractionId}")
    public ResponseEntity<Infraction> getInfractionById (@PathVariable String infractionId) throws ResourceNotFoundException {
        var findMe = infractionService.findByInfractionId(infractionId);

        return ResponseEntity
                .accepted()
                .body(findMe);
    }

    @GetMapping("/infractionName/{infractionName}")
    public ResponseEntity<Infraction> getInfractionByName (@PathVariable String infractionName) throws ResourceNotFoundException {
        var findMe = infractionService.findInfractionByInfractionName(infractionName);

        return ResponseEntity
                .accepted()
                .body(findMe);
    }

    @DeleteMapping("/delete/infraction")
    public ResponseEntity<String> deleteInfraction (@RequestBody Infraction infraction) throws ResourceNotFoundException {
        var delete = infractionService.deleteInfraction(infraction);
        return ResponseEntity
                .accepted()
                .body(delete);
    }

//    @PutMapping("/infraction/edit")
//    public ResponseEntity<Infraction> editInfraction (@RequestBody Infraction infraction) {
//        var edit = infractionService.createNewInfraction(infraction);
//        return ResponseEntity
//                .accepted()
//                .body(edit);
//    }
}
