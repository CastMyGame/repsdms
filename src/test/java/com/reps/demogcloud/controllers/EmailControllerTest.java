package com.reps.demogcloud.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.email.ClassAnnouncementRequest;
import com.reps.demogcloud.security.config.SecurityConfig;
import com.reps.demogcloud.security.services.UserService;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import javax.mail.MessagingException;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@WebMvcTest(EmailController.class)
@WithMockUser(username = "testuser", roles = {"GUIDANCE"})
class EmailControllerTest {

    private final ClassAnnouncementRequest sampleRequest = ClassAnnouncementRequest.builder()
            .teacherEmail("teacher@example.com")
            .subject("Test Subject")
            .msg("This is a test announcement")
            .className("Math")
            .build();
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private EmailService emailService;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private UserService userService;
    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void sendClassAnnouncement_returnsOk() throws Exception {
        doNothing().when(emailService).sendClassAnnouncement(sampleRequest);

        mockMvc.perform(post("/email/v1/classAnnouncement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(sampleRequest))))
                .andExpect(status().isOk());
    }

    @Test
    void sendClassAnnouncement_whenMessagingExceptionThrown_returnsInternalServerError() throws Exception {
        ClassAnnouncementRequest request = new ClassAnnouncementRequest("teacher@example.com", "Math", "Test Subject", "This is a test announcement");
        List<ClassAnnouncementRequest> requestList = List.of(request);

        doThrow(new MessagingException("SMTP error"))
                .when(emailService)
                .sendClassAnnouncement(any(ClassAnnouncementRequest.class));

        mockMvc.perform(post("/email/v1/classAnnouncement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestList)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Failed to send class announcement")));
    }
}