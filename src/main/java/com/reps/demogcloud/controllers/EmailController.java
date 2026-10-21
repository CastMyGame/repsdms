package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.email.ClassAnnouncementRequest;
import com.reps.demogcloud.services.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.mail.MessagingException;
import java.util.List;

@CrossOrigin(
        origins = {
                "http://localhost:3000/",
                "https://repsdiscipline.vercel.app",
                "https://repsdev.vercel.app"
        }
)
@RestController
@RequestMapping("/email/v1")
@RequiredArgsConstructor
public class EmailController {
    private final EmailService emailService;
    private final com.reps.demogcloud.services.email.RequiredNotificationService requiredNotifications;

    @GetMapping("/notifications")
    public java.util.Map<String,Object> notificationStatus() { return requiredNotifications.listStatuses(); }

    @GetMapping("/notifications/{kind}/{caseId}")
    public java.util.Map<String,Object> notificationStatus(@PathVariable String kind,@PathVariable String caseId) {
        return requiredNotifications.statuses(kind,caseId);
    }

    @PostMapping("/notifications/{kind}/{caseId}/{notificationId}/retry")
    public java.util.Map<String,Object> retryNotification(@PathVariable String kind,@PathVariable String caseId,@PathVariable String notificationId,
            @RequestHeader("Authorization") String authorization) {
        requireBearer(authorization);
        requiredNotifications.retry(kind,caseId,notificationId); return requiredNotifications.statuses(kind,caseId);
    }

    @PostMapping("/notifications/{kind}/{caseId}/{notificationId}/resolve")
    public java.util.Map<String,Object> resolveNotification(@PathVariable String kind,@PathVariable String caseId,@PathVariable String notificationId,
            @RequestBody java.util.Map<String,String> body,@RequestHeader("Authorization") String authorization) {
        requireBearer(authorization);
        requiredNotifications.resolve(kind,caseId,notificationId,body.get("reason")); return requiredNotifications.statuses(kind,caseId);
    }

    private void requireBearer(String authorization) {
        if(authorization==null || !authorization.startsWith("Bearer "))
            throw new org.springframework.security.access.AccessDeniedException("Bearer authentication is required");
    }

    @PostMapping("/classAnnouncement")
    public void sendClassAnnouncement(@RequestBody List<ClassAnnouncementRequest> classAnnouncementRequestList) throws MessagingException {
        for (ClassAnnouncementRequest request : classAnnouncementRequestList) {
            emailService.sendClassAnnouncement(request);
        }
    }
}
