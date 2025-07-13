package com.reps.demogcloud.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.security.config.SecurityConfig;
import com.reps.demogcloud.security.services.UserService;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.InfractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@WebMvcTest(InfractionController.class)
@WithMockUser(username = "testuser", roles = {"TEACHER"})
class InfractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InfractionService infractionService;

    @Autowired
    private ObjectMapper objectMapper;

    private Infraction sampleInfraction;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        sampleInfraction = new Infraction();
        sampleInfraction.setInfractionId("123");
        sampleInfraction.setInfractionName("Test Infraction");
    }

    @Test
    void testFindAllInfractions_returnsAcceptedWithList() throws Exception {
        List<Infraction> infractions = List.of(sampleInfraction);
        Mockito.when(infractionService.findAllInfractions()).thenReturn(infractions);

        mockMvc.perform(get("/infraction/v1/all"))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].infractionId").value("123"))
                .andExpect(jsonPath("$[0].infractionName").value("Test Infraction"));
    }

    @Test
    void testGetInfractionById_found_returnsAccepted() throws Exception {
        Mockito.when(infractionService.findByInfractionId("123")).thenReturn(sampleInfraction);

        mockMvc.perform(get("/infraction/v1/infractionId/123"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.infractionId").value("123"))
                .andExpect(jsonPath("$.infractionName").value("Test Infraction"));
    }

    @Test
    void testGetInfractionById_notFound_returns404() throws Exception {
        Mockito.when(infractionService.findByInfractionId("999"))
                .thenThrow(new ResourceNotFoundException("Not found"));

        mockMvc.perform(get("/infraction/v1/infractionId/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetInfractionByName_found_returnsAccepted() throws Exception {
        Mockito.when(infractionService.findInfractionByInfractionName("Test Infraction"))
                .thenReturn(sampleInfraction);

        mockMvc.perform(get("/infraction/v1/infractionName/Test Infraction"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.infractionId").value("123"))
                .andExpect(jsonPath("$.infractionName").value("Test Infraction"));
    }

    @Test
    void testCreateNewInfraction_returnsAcceptedWithCreatedInfraction() throws Exception {
        Mockito.when(infractionService.createNewInfraction(any(Infraction.class))).thenReturn(sampleInfraction);

        String json = objectMapper.writeValueAsString(sampleInfraction);

        mockMvc.perform(post("/infraction/v1/createInfraction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.infractionId").value("123"))
                .andExpect(jsonPath("$.infractionName").value("Test Infraction"));
    }

    @Test
    void testDeleteInfraction_returnsAcceptedWithMessage() throws Exception {
        Mockito.when(infractionService.deleteInfraction(any(Infraction.class))).thenReturn("Deleted");

        String json = objectMapper.writeValueAsString(sampleInfraction);

        mockMvc.perform(delete("/infraction/v1/delete/infraction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Deleted"));
    }

    @Test
    void testGetInfractionById_unexpectedException_returns500() throws Exception {
        Mockito.when(infractionService.findByInfractionId("123"))
                .thenThrow(new RuntimeException("Something went wrong"));

        mockMvc.perform(get("/infraction/v1/infractionId/123"))
                .andExpect(status().isInternalServerError());
    }

}
