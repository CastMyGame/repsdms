package com.reps.demogcloud.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.school.SchoolResponse;
import com.reps.demogcloud.security.config.SecurityConfig;
import com.reps.demogcloud.security.services.UserService;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.SchoolService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@WebMvcTest(SchoolController.class)
@WithMockUser(username = "testuser", roles = {"TEACHER"})
class SchoolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SchoolService schoolService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void createSchool_returnsCreated() throws Exception {
        School school = new School();
        school.setSchoolName("Test School");

        SchoolResponse response = new SchoolResponse();
        response.setSchool(school);

        Mockito.when(schoolService.createNewSchool(any(School.class))).thenReturn(response);

        mockMvc.perform(post("/school/v1/newSchool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(school)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.school.schoolName").value("Test School"));
    }

    @Test
    void createSchool_returnsBadRequest_whenSchoolIsNull() throws Exception {
        SchoolResponse response = new SchoolResponse();
        response.setSchool(null); // Simulate failed creation

        Mockito.when(schoolService.createNewSchool(any(School.class))).thenReturn(response);

        mockMvc.perform(post("/school/v1/newSchool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new School())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editSchool_returnsOk() throws Exception {
        String schoolName = "Test School";
        String updateValue = "Updated Value";

        School school = new School();
        school.setSchoolName(schoolName);

        SchoolResponse response = new SchoolResponse();
        response.setSchool(school);

        Mockito.when(schoolService.editSchool(eq(schoolName), eq(updateValue))).thenReturn(response);

        mockMvc.perform(put("/school/v1/{schoolName}", schoolName)
                        .param("update", updateValue))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.school.schoolName").value("Test School"));
    }

    @Test
    void editSchool_returnsBadRequest_whenUpdateFails() throws Exception {
        String schoolName = "Nonexistent School";

        SchoolResponse response = new SchoolResponse();
        response.setSchool(null); // Simulate failed update

        Mockito.when(schoolService.editSchool(eq(schoolName), eq("someUpdate"))).thenReturn(response);

        mockMvc.perform(put("/school/v1/{schoolName}", schoolName)
                        .param("update", "someUpdate"))
                .andExpect(status().isBadRequest());
    }
}
