package com.reps.demogcloud.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.assignments.Assignment;
import com.reps.demogcloud.security.config.SecurityConfig;
import com.reps.demogcloud.security.services.UserService;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.AssignmentService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(assignmentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@WithMockUser(username = "admin", roles = {"ADMIN"})
class AssignmentControllerTest {

    private final Assignment testAssignment = new Assignment();
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AssignmentService assignmentService;
    @MockBean
    private UserService userService;
    @MockBean
    private JwtUtils jwtUtils;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllAssignments_returnsAccepted_withList() throws Exception {
        when(assignmentService.getAllAssignments()).thenReturn(List.of(testAssignment));

        mockMvc.perform(get("/assignments/v1/"))
                .andExpect(status().isAccepted())
                .andExpect(content().json(objectMapper.writeValueAsString(List.of(testAssignment))));
    }

    @Test
    void createNewAssignment_returnsAccepted_withAssignment() throws Exception {
        when(assignmentService.createNewAssignment(any(Assignment.class))).thenReturn(testAssignment);

        mockMvc.perform(post("/assignments/v1/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testAssignment)))
                .andExpect(status().isAccepted())
                .andExpect(content().json(objectMapper.writeValueAsString(testAssignment)));
    }

    @Test
    void updateAssignment_returnsAccepted_withAssignment() throws Exception {
        String id = "abc123";
        when(assignmentService.updateNewAssignment(any(Assignment.class), any(String.class)))
                .thenReturn(testAssignment);

        mockMvc.perform(put("/assignments/v1/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testAssignment)))
                .andExpect(status().isAccepted())
                .andExpect(content().json(objectMapper.writeValueAsString(testAssignment)));
    }

    @Test
    void deleteAssignment_returnsAccepted_withAssignment() throws Exception {
        String assignmentName = "SampleAssignment";
        when(assignmentService.deleteAssignment(assignmentName)).thenReturn(testAssignment);

        mockMvc.perform(delete("/assignments/v1/delete/" + assignmentName))
                .andExpect(status().isAccepted())
                .andExpect(content().json(objectMapper.writeValueAsString(testAssignment)));
    }
}
