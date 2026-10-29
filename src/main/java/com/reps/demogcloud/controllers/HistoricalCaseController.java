package com.reps.demogcloud.controllers;

import com.reps.demogcloud.services.tenancy.HistoricalCaseAccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Authenticated read-only snapshots. No writable provenance or live case fields are exposed. */
@RestController @RequestMapping("/history/v1/cases")
public class HistoricalCaseController {
    @Autowired(required=false) private HistoricalCaseAccess historical;
    @GetMapping public ResponseEntity<?> list(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(historical==null?Map.of("available",false,"cases",List.of()):Map.of("available",true,"cases",historical.list()));}
    @GetMapping("/{kind}/{id}") public ResponseEntity<?> get(@PathVariable String kind,@PathVariable String id){
        if(historical==null)return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("available",false));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(historical.get(kind,id));
    }
}
