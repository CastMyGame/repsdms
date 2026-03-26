package com.reps.demogcloud.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.guidance.GuidanceReferral;
import com.reps.demogcloud.models.guidance.GuidanceRequest;
import com.reps.demogcloud.models.guidance.GuidanceResponse;
import com.reps.demogcloud.models.punishment.ResourceUpdateRequest;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import com.reps.demogcloud.security.config.SecurityConfig;
import com.reps.demogcloud.security.services.JwtFilterRequest;
import com.reps.demogcloud.services.GuidanceService;
import com.reps.demogcloud.services.PunishmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(
        controllers = GuidanceController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilterRequest.class)
        }
)
class GuidanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GuidanceService guidanceService;

    @MockitoBean
    private PunishmentService punishmentService;

    private GuidanceReferral referral;
    private GuidanceResponse response;

    @BeforeEach
    void setUp() {
        referral = new GuidanceReferral();
        response = new GuidanceResponse();
    }

    @Test
    void getAll_returnsAccepted() throws Exception {
        when(guidanceService.findAll()).thenReturn(List.of(referral));

        mockMvc.perform(get("/guidance/v1/referrals"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getByStatus_returnsAccepted() throws Exception {
        when(guidanceService.findByStatus("open")).thenReturn(List.of(referral));

        mockMvc.perform(get("/guidance/v1/guidanceStatus/open"))
                .andExpect(status().isAccepted());
    }

    @Test
    void createNewGuidance_returnsAccepted() throws Exception {
        GuidanceRequest request = new GuidanceRequest();
        when(guidanceService.createNewGuidanceFormSimple(any())).thenReturn(response);

        mockMvc.perform(post("/guidance/v1/guidance/new")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    void updateGuidance_returnsAccepted() throws Exception {
        ThreadEvent event = new ThreadEvent();
        when(guidanceService.updateGuidance(eq("123"), any())).thenReturn(referral);

        mockMvc.perform(put("/guidance/v1/guidance/notes/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isAccepted());
    }

    @Test
    void updateGuidanceFollowUp_validDate_returnsAccepted() throws Exception {
        when(guidanceService.updateGuidanceFollowUp(eq("123"), any(LocalDate.class), eq("follow-up")))
                .thenReturn(referral);

        mockMvc.perform(put("/guidance/v1/guidance/followup/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "followUpDate", "20250101",
                                "status", "follow-up"
                        ))))
                .andExpect(status().isAccepted());
    }

    @Test
    void updateGuidanceFollowUp_invalidDate_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/guidance/v1/guidance/followup/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "followUpDate", "invalid-date",
                                "status", "follow-up"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateGuidanceStatus_returnsAccepted() throws Exception {
        when(guidanceService.updateGuidanceStatus(eq("123"), eq("closed"))).thenReturn(referral);

        mockMvc.perform(put("/guidance/v1/guidance/status/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "closed"))))
                .andExpect(status().isAccepted());
    }

    @Test
    void updateAndSendResources_returnsAccepted() throws Exception {
        ResourceUpdateRequest request = new ResourceUpdateRequest();
        when(guidanceService.sendResourcesAndMakeNotes(eq("123"), any())).thenReturn(response);

        mockMvc.perform(put("/guidance/v1/guidance/resources/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    void deleteGuidanceReferral_returnsAccepted() throws Exception {
        when(guidanceService.deleteGuidanceReferral("123")).thenReturn("Deleted");

        mockMvc.perform(delete("/guidance/v1/guidance/delete/123"))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Deleted"));
    }
}