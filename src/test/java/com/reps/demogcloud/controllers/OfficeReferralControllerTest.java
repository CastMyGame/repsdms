package com.reps.demogcloud.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.officeReferral.*;
import com.reps.demogcloud.security.config.SecurityConfig;
import com.reps.demogcloud.security.services.JwtFilterRequest;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.OfficeReferralService;
import com.reps.demogcloud.services.UserContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(
        controllers = OfficeReferralController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilterRequest.class)
        }
)
class OfficeReferralControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OfficeReferralService officeReferralService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserContextService userContextService;

    private OfficeReferral referral;
    private OfficeReferralResponse response;

    @BeforeEach
    void setUp() {
        when(userContextService.getCurrentUserEmail()).thenReturn("teacher@test.com");
        when(userContextService.getCurrentUserSchool()).thenReturn("Test School");
        referral = new OfficeReferral();
        referral.setSchool("Test School");
        referral.setStudentEmail("student@example.com");
        when(officeReferralService.findByReferralId(any())).thenReturn(referral);
        referral.setOfficeReferralId("123");
        referral.setAdminEmail("admin@example.com");
        referral.setArchived(false);
        response = new OfficeReferralResponse(); // Populate fields if needed
    }

    @Test
    void getAll_returnsAccepted() throws Exception {
        when(officeReferralService.findAll()).thenReturn(List.of(referral));

        mockMvc.perform(get("/officeReferral/v1/punishments"))
                .andExpect(status().isAccepted());
    }

    @Test
    void getByReferralId_returnsReferral() throws Exception {
        when(officeReferralService.findByReferralId("123")).thenReturn(referral);

        mockMvc.perform(get("/officeReferral/v1/id/123"))
                .andExpect(status().isAccepted());
    }

    @Test
    void getByAdminEmail_returnsList() throws Exception {
        when(officeReferralService.findByAdminEmail("admin@example.com")).thenReturn(List.of(referral));

        mockMvc.perform(get("/officeReferral/v1/admin/{email}", "admin@example.com"))
                .andExpect(status().isAccepted());
    }

    @Test
    void createNewAdminReferralBulk_returnsList() throws Exception {
        List<OfficeReferralRequest> requests = List.of(new OfficeReferralRequest());
        when(officeReferralService.createNewAdminReferralBulk(any())).thenReturn(List.of(referral));

        mockMvc.perform(post("/officeReferral/v1/startPunish/adminReferral")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isAccepted());
    }

    @Test
    void closeByReferralId_returnsResponse() throws Exception {
        OfficeReferralCloseRequest request = new OfficeReferralCloseRequest();
        request.setId("123");
        when(officeReferralService.closeByReferralId(any())).thenReturn(response);

        mockMvc.perform(post("/officeReferral/v1/closeId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    void submitByReferralId_returnsResponse() throws Exception {
        when(officeReferralService.submitByReferralId("123")).thenReturn(response);

        mockMvc.perform(post("/officeReferral/v1/submit/123"))
                .andExpect(status().isAccepted());
    }

    @Test
    void updateMapIndex_returnsReferral() throws Exception {
        when(officeReferralService.updateMapIndex("123", 1)).thenReturn(referral);

        mockMvc.perform(put("/officeReferral/v1/123/index/1"))
                .andExpect(status().isAccepted());
    }

    @Test
    void rejectAnswers_returnsReferral() throws Exception {
        when(officeReferralService.rejectAnswers("456")).thenReturn(referral);

        mockMvc.perform(put("/officeReferral/v1/rejected/456"))
                .andExpect(status().isAccepted());
    }

    @Test
    void updateAllDescriptions_returnsList() throws Exception {
        when(officeReferralService.updateDescriptions()).thenReturn(List.of(referral));

        mockMvc.perform(put("/officeReferral/v1/descriptions"))
                .andExpect(status().isAccepted());
    }
}
