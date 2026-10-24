package com.reps.demogcloud.controllers;

import com.reps.demogcloud.services.AssignmentLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/assignments/v1/cases")
public class AssignmentLifecycleController {
    private final AssignmentLifecycleService lifecycle;
    @GetMapping("/{id}") public AssignmentLifecycleService.View get(@PathVariable String id) { return lifecycle.view(id); }
    @PutMapping("/{id}/{operation}") public AssignmentLifecycleService.View change(@PathVariable String id,@PathVariable String operation,
            @RequestBody AssignmentLifecycleService.Command command) { return lifecycle.change(id,operation,command); }
}
