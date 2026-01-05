package com.reps.demogcloud.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.dto.AdminOverviewDTO;
import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.dto.StudentOverviewDTO;
import com.reps.demogcloud.models.dto.TeacherOverviewDTO;
import com.reps.demogcloud.security.config.SecurityConfig;
import com.reps.demogcloud.security.services.JwtFilterRequest;
import com.reps.demogcloud.security.services.UserService;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.DTOService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(
        controllers = DTOController.class,
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
class DTOControllerTest {

    private final AdminOverviewDTO adminOverviewDTO = new AdminOverviewDTO();
    private final TeacherOverviewDTO teacherOverviewDTO = new TeacherOverviewDTO(null, null, null, null, null, null);
    private final StudentOverviewDTO studentOverviewDTO = new StudentOverviewDTO();
    private final List<PunishmentDTO> punishmentDTOs = List.of(new PunishmentDTO());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DTOService dtoService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void getAll_returnsAccepted_withAdminOverviewDTO() throws Exception {
        when(dtoService.getAdminOverData()).thenReturn(adminOverviewDTO);

        mockMvc.perform(get("/DTO/v1/AdminOverviewData"))
                .andExpect(status().isAccepted())
                .andExpect(content().json(objectMapper.writeValueAsString(adminOverviewDTO)));
    }

    @Test
    void getAllTeacherOverview_returnsAccepted_withTeacherOverviewDTO() throws Exception {
        when(dtoService.getTeacherOverData()).thenReturn(teacherOverviewDTO);

        mockMvc.perform(get("/DTO/v1/TeacherOverviewData"))
                .andExpect(status().isAccepted())
                .andExpect(content().json(objectMapper.writeValueAsString(teacherOverviewDTO)));
    }

    @Test
    void getAllTeacherOverview_returns500_whenServiceFails() throws Exception {
        when(dtoService.getTeacherOverData()).thenThrow(new RuntimeException("Simulated failure"));

        mockMvc.perform(get("/DTO/v1/TeacherOverviewData"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getAllPunishmentDTO_returnsAccepted_withListOfPunishmentDTO() throws Exception {
        when(dtoService.getDTOPunishments()).thenReturn(punishmentDTOs);

        mockMvc.perform(get("/DTO/v1/punishmentsDTO"))
                .andExpect(status().isAccepted())
                .andExpect(content().json(objectMapper.writeValueAsString(punishmentDTOs)));
    }

    @Test
    void getAllStudentOverview_returnsAccepted_withStudentOverviewDTO() throws Exception {
        when(dtoService.getLoggedInStudentOverData()).thenReturn(studentOverviewDTO);

        mockMvc.perform(get("/DTO/v1/StudentOverviewData"))
                .andExpect(status().isAccepted())
                .andExpect(content().json(objectMapper.writeValueAsString(studentOverviewDTO)));
    }

    @Test
    void getAllStudentOverview_withEmail_returnsAccepted_withStudentOverviewDTO() throws Exception {
        String email = "student@example.com";
        when(dtoService.getStudentOverData(email)).thenReturn(studentOverviewDTO);

        mockMvc.perform(get("/DTO/v1/StudentOverviewData/" + email))
                .andExpect(status().isAccepted())
                .andExpect(content().json(objectMapper.writeValueAsString(studentOverviewDTO)));
    }
}
