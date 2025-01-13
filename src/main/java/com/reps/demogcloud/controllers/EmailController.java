package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.email.ClassAnnouncementRequest;
import com.reps.demogcloud.services.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.mail.MessagingException;
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

    @PostMapping("/classAnnouncement")
    public void sendClassAnnouncement(@RequestBody List<ClassAnnouncementRequest> classAnnouncementRequestList) throws MessagingException {
        for (ClassAnnouncementRequest request : classAnnouncementRequestList) {
            emailService.sendClassAnnouncement(request);
        }
    }
}
