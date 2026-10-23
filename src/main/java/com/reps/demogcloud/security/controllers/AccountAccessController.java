package com.reps.demogcloud.security.controllers;
import com.reps.demogcloud.security.services.AccountAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class AccountAccessController {
    private final AccountAccessService accounts;
    public record Setup(String username,String credential,String newPassword) { @Override public String toString(){return "Setup[redacted]";} }
    public record Handoff(boolean identityVerified,boolean privateEmailAllowed) { }
    @PostMapping("/account-access/complete")
    public ResponseEntity<Void> complete(@RequestBody Setup request) {
        accounts.complete(request.username(),request.credential(),request.newPassword());
        return ResponseEntity.noContent().header("Cache-Control","no-store").build();
    }
    @PostMapping("/accounts/v1/{id}/invitation")
    public ResponseEntity<Void> invite(@PathVariable String id) { accounts.inviteStaff(id);return ResponseEntity.accepted().header("Cache-Control","no-store").build(); }
    @GetMapping("/accounts/v1/students/{email}/access")
    public ResponseEntity<java.util.Map<String,Object>> status(@PathVariable String email) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.studentStatus(email));
    }
    @PostMapping("/accounts/v1/students/{email}/invitation")
    public ResponseEntity<Void> inviteStudent(@PathVariable String email,@RequestBody Handoff request) {
        accounts.inviteStudent(email,request.identityVerified(),request.privateEmailAllowed());
        return ResponseEntity.accepted().header("Cache-Control","no-store").build();
    }
    @PostMapping("/accounts/v1/students/{email}/setup")
    public ResponseEntity<AccountAccessService.Handoff> handoff(@PathVariable String email,@RequestBody Handoff request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.studentHandoff(email,request.identityVerified(),request.privateEmailAllowed()));
    }
}
