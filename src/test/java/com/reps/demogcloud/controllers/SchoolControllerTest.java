package com.reps.demogcloud.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.school.SchoolResponse;
import com.reps.demogcloud.security.config.SecurityConfig;
import com.reps.demogcloud.security.services.JwtFilterRequest;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.SchoolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(
        controllers = SchoolController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilterRequest.class)
        }
)
class SchoolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SchoolService schoolService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtUtils jwtUtils;

    @Test
    void getAllSchools_returnsOk() throws Exception {
        School school = new School();
        school.setSchoolName("Test School");

        when(schoolService.getAllSchools()).thenReturn(List.of(school));

        mockMvc.perform(get("/school/v1/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].schoolName").value("Test School"));
    }

    @Test
    void searchSchools_returnsOk() throws Exception {
        School school = new School();
        school.setSchoolName("Charleston High");

        when(schoolService.getSchoolsByCityState("Charleston", "SC"))
                .thenReturn(List.of(school));

        mockMvc.perform(get("/school/v1/search")
                        .param("city", "Charleston")
                        .param("state", "SC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].schoolName").value("Charleston High"));
    }

    @Test
    void createSchool_returnsCreated() throws Exception {
        School school = new School();
        school.setSchoolName("Test School");
        school.setSchoolIdNumber("123");
        school.setCurrency("USD");

        SchoolResponse response = new SchoolResponse();
        response.setSchool(school);

        when(schoolService.createNewSchool(any(School.class))).thenReturn(response);

        mockMvc.perform(post("/school/v1/newSchool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(school)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.school.schoolName").value("Test School"))
                .andExpect(jsonPath("$.school.schoolIdNumber").value("123"));
    }

    @Test
    void createSchool_returnsBadRequest_whenSchoolIsNull() throws Exception {
        SchoolResponse response = new SchoolResponse();
        response.setSchool(null);
        response.setError("School already exists");

        when(schoolService.createNewSchool(any(School.class))).thenReturn(response);

        mockMvc.perform(post("/school/v1/newSchool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new School())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.school").doesNotExist())
                .andExpect(jsonPath("$.error").value("School already exists"));
    }

    @Test
    void editSchool_returnsOk_whenUpdateSucceeds() throws Exception {
        String schoolName = "Test School";

        School updatedSchool = new School();
        updatedSchool.setSchoolName(schoolName);

        SchoolResponse response = new SchoolResponse();
        response.setSchool(updatedSchool);

        Map<String, String> updateMap = new HashMap<>();
        updateMap.put("currency", "points");
        updateMap.put("city", "Charleston");

        when(schoolService.editSchool(eq(schoolName), any(Map.class))).thenReturn(response);

        mockMvc.perform(put("/school/v1/{schoolName}", schoolName)
                        .param("currency", "points")
                        .param("city", "Charleston"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.school.schoolName").value("Test School"));
    }

    @Test
    void editSchool_returnsBadRequest_whenUpdateFails() throws Exception {
        String schoolName = "Missing School";

        SchoolResponse response = new SchoolResponse();
        response.setSchool(null);
        response.setError("School not found");

        when(schoolService.editSchool(eq(schoolName), any(Map.class))).thenReturn(response);

        mockMvc.perform(put("/school/v1/{schoolName}", schoolName)
                        .param("currency", "points"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.school").doesNotExist())
                .andExpect(jsonPath("$.error").value("School not found"));
    }
}