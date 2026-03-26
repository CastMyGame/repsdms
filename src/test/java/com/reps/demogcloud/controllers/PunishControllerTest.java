package com.reps.demogcloud.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.security.config.SecurityConfig;
import com.reps.demogcloud.security.services.JwtFilterRequest;
import com.reps.demogcloud.services.PunishmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(
        controllers = PunishController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilterRequest.class)
        }
)
@TestPropertySource(properties = {
        "spring.jackson.serialization.FAIL_ON_EMPTY_BEANS=false"
})
public class PunishControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PunishmentService punishmentService;

    @Autowired
    private ObjectMapper objectMapper;

    private Punishment punishment;
    private List<Punishment> punishmentList;

    @BeforeEach
    void setUp() {
        punishment = new Punishment();
        punishment.setPunishmentId("123");
        punishment.setStudentEmail("test@student.com");
        punishment.setInfractionName("Failure to Complete Work");
        punishment.setStatus("OPEN");

        punishmentList = List.of(punishment);
    }

    // ------------------------------------ GET TESTS -------------------------------//
    @Test
    void testGetAllPunishments() throws Exception {
        when(punishmentService.findAll()).thenReturn(punishmentList);

        mockMvc.perform(get("/punish/v1/punishments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentEmail").value("test@student.com"));
    }

    @Test
    void getByPunishId_validId_returnsPunishment() throws Exception {
        when(punishmentService.findByPunishmentId("123")).thenReturn(punishment);

        mockMvc.perform(get("/punish/v1/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.punishmentId").value("123"))
                .andExpect(jsonPath("$.studentEmail").value("test@student.com"));
    }

    @Test
    void getByPunishId_invalidId_returnsNotFound() throws Exception {
        when(punishmentService.findByPunishmentId("invalid")).thenThrow(new ResourceNotFoundException("Not found"));

        mockMvc.perform(get("/punish/v1/invalid"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByStatus_validStatus_returnsPunishments() throws Exception {
        when(punishmentService.findByStatus("OPEN")).thenReturn(punishmentList);

        mockMvc.perform(get("/punish/v1/punishStatus/OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void getByStatus_invalidStatus_returnsNotFound() throws Exception {
        when(punishmentService.findByStatus("BAD")).thenThrow(new ResourceNotFoundException("Status not found"));

        mockMvc.perform(get("/punish/v1/punishStatus/BAD"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByStudentEmailAndFailureToCompleteAssignments_validEmail_returnsList() throws Exception {
        when(punishmentService.findByStudentEmailAndInfraction("test@student.com", "Failure to Complete Work"))
                .thenReturn(punishmentList);

        mockMvc.perform(get("/punish/v1/student/test@student.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentEmail").value("test@student.com"));
    }

    @Test
    void getByStudentEmailAndFailureToCompleteAssignments_invalidEmail_returnsNotFound() throws Exception {
        when(punishmentService.findByStudentEmailAndInfraction("bad@example.com", "Failure to Complete Work"))
                .thenThrow(new ResourceNotFoundException("Student not found"));

        mockMvc.perform(get("/punish/v1/student/bad@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOpenPunishments_returnsOpenPunishments() throws Exception {
        when(punishmentService.getAllOpenAssignments()).thenReturn(punishmentList);

        mockMvc.perform(get("/punish/v1/openPunishments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void getAllArchived_returnsArchivedPunishments() throws Exception {
        when(punishmentService.findAllPunishmentArchived(true)).thenReturn(punishmentList);

        mockMvc.perform(get("/punish/v1/archived"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentEmail").value("test@student.com"));
    }

    @Test
    void getPunishmentForStudent_returnsList() throws Exception {
        when(punishmentService.getAllPunishmentForStudent("test@student.com")).thenReturn(punishmentList);

        mockMvc.perform(get("/punish/v1/punishments/test@student.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentEmail").value("test@student.com"));
    }

    @Test
    void getAllPunishmentByStudentEmail_returnsList() throws Exception {
        when(punishmentService.getAllPunishmentByStudentEmail("test@student.com")).thenReturn(punishmentList);

        mockMvc.perform(get("/punish/v1/student/punishments/test@student.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentEmail").value("test@student.com"));
    }

    // ----------------------------------------------- POST TESTS ---------------------------------------------//

    @Test
    void closePunishment_returnsSuccess() throws Exception {
        // Build student answers
        StudentAnswer answer = new StudentAnswer();
        answer.setQuestion("What did you learn?");
        answer.setAnswer("To follow the rules.");

        List<StudentAnswer> answers = List.of(answer);

        // Build request
        ClosePunishmentRequest request = new ClosePunishmentRequest();
        request.setInfractionName("Disruptive Behavior");
        request.setStudentEmail("student@example.com");
        request.setStudentAnswer(answers);

        // Mock response from service
        PunishmentResponse mockedResponse = new PunishmentResponse();
        mockedResponse.setMessage("Punishment closed successfully");

        when(punishmentService.closePunishment(
                eq("Disruptive Behavior"),
                eq("student@example.com"),
                eq(answers)
        )).thenReturn(mockedResponse);

        mockMvc.perform(post("/punish/v1/punishId/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Punishment closed successfully"));
    }

    @Test
    void closeByPunishmentId_returnsResponse() throws Exception {
        String punishmentId = "12345";

        PunishmentResponse mockResponse = PunishmentResponse.builder()
                .message("Punishment closed")
                .build();

        when(punishmentService.closeByPunishmentId(punishmentId)).thenReturn(mockResponse);

        mockMvc.perform(post("/punish/v1/close/{id}", punishmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Punishment closed"));
    }

    @Test
    void createNewFormPunish_returnsResponse() throws Exception {
        PunishmentFormRequest formRequest = PunishmentFormRequest.builder()
                .studentEmail("student1@example.com")
                .infractionName("Disruption")
                .build();

        PunishmentResponse mockResponse = PunishmentResponse.builder()
                .message("Form punishment created")
                .build();

        when(punishmentService.createNewPunishForm(formRequest)).thenReturn(mockResponse);

        mockMvc.perform(post("/punish/v1/startPunish/form")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Form punishment created"));
    }


    @Test
    void createNewFormPunishBulk_returnsListResponse() throws Exception {
        List<PunishmentFormRequest> bulkRequests = List.of(
                PunishmentFormRequest.builder()
                        .studentEmail("student1@example.com")
                        .infractionName("Disruption")
                        .build(),
                PunishmentFormRequest.builder()
                        .studentEmail("student2@example.com")
                        .infractionName("Defiance")
                        .build()
        );

        List<PunishmentResponse> mockResponses = List.of(
                PunishmentResponse.builder().message("Punishment 1").build(),
                PunishmentResponse.builder().message("Punishment 2").build()
        );

        when(punishmentService.createNewPunishFormBulk(bulkRequests)).thenReturn(mockResponses);

        mockMvc.perform(post("/punish/v1/startPunish/formList")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkRequests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].message").value("Punishment 1"))
                .andExpect(jsonPath("$[1].message").value("Punishment 2"));
    }


    @Test
    void getAllPunishmentsForStudents_returnsList() throws Exception {
        String email = "student@test.com";

        List<Punishment> mockList = List.of(
                Punishment.builder()
                        .studentEmail(email)
                        .infractionName("Dress Code Violation")
                        .build()
        );

        when(punishmentService.getAllPunishmentsForStudents(email)).thenReturn(mockList);

        mockMvc.perform(post("/punish/v1/studentsReport/{studentEmail}", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentEmail").value(email));
    }

    // ------------------------------- PUT TESTS ----------------------------------------//

    @Test
    void updateMapIndex_returnsUpdatedPunishment() throws Exception {
        String punishmentId = "abc123";
        int index = 5;

        Punishment updated = Punishment.builder().punishmentId(punishmentId).mapIndex(index).build();

        when(punishmentService.updateMapIndex(punishmentId, index)).thenReturn(updated);

        mockMvc.perform(put("/punish/v1/{id}/index/{index}", punishmentId, index))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.punishmentId").value(punishmentId))
                .andExpect(jsonPath("$.mapIndex").value(index));
    }

    @Test
    void archivedDeleted_returnsArchivedPunishment() throws Exception {
        String punishmentId = "pun123";
        String userId = "user456";
        String explanation = "Test reason";

        Punishment archived = Punishment.builder().punishmentId(punishmentId).archived(true).build();

        when(punishmentService.archiveRecord(punishmentId ,userId , explanation)).thenReturn(archived);

        mockMvc.perform(put("/punish/v1/archived/{userId}/{punishmentId}", userId, punishmentId)
                        .contentType(MediaType.TEXT_PLAIN)
                        .characterEncoding("UTF-8")
                        .content(explanation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.punishmentId").value(punishmentId))
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void restoreArchivedDeleted_returnsRestoredPunishment() throws Exception {
        String punishmentId = "pun999";

        Punishment restored = Punishment.builder().punishmentId(punishmentId).archived(false).build();

        when(punishmentService.restoreRecord(punishmentId)).thenReturn(restored);

        mockMvc.perform(put("/punish/v1/archived/restore/{punishmentId}", punishmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.punishmentId").value(punishmentId))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void rejectLevelThree_returnsRejectedPunishment() throws Exception {
        String punishmentId = "punReject";

        Punishment rejected = Punishment.builder().punishmentId(punishmentId).status("REJECTED").build();

        when(punishmentService.rejectLevelThree(punishmentId)).thenReturn(rejected);

        mockMvc.perform(put("/punish/v1/rejected/{punishmentId}", punishmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.punishmentId").value(punishmentId))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }


    @Test
    void updateAllDescriptions_returnsList() throws Exception {
        List<Punishment> updated = List.of(
                Punishment.builder().punishmentId("desc1").infractionDescription(new ArrayList<>(List.of("Desc 1"))).build(),
                Punishment.builder().punishmentId("desc2").infractionDescription(new ArrayList<>(List.of("Desc 2"))).build()
        );

        when(punishmentService.updateDescriptions()).thenReturn(updated);

        mockMvc.perform(put("/punish/v1/descriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].punishmentId").value("desc1"))
                .andExpect(jsonPath("$[0].infractionDescription").value("Desc 1"))
                .andExpect(jsonPath("$[1].punishmentId").value("desc2"))
                .andExpect(jsonPath("$[1].infractionDescription").value("Desc 2"));
    }

    @Test
    void updateAllStudentEmails_returnsList() throws Exception {
        List<Punishment> updated = List.of(
                Punishment.builder().punishmentId("email1").studentEmail("student1@example.com").build(),
                Punishment.builder().punishmentId("email2").studentEmail("student2@example.com").build()
        );

        when(punishmentService.updateStudentEmails()).thenReturn(updated);

        mockMvc.perform(put("/punish/v1/emails"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].punishmentId").value("email1"))
                .andExpect(jsonPath("$[0].studentEmail").value("student1@example.com"))
                .andExpect(jsonPath("$[1].punishmentId").value("email2"))
                .andExpect(jsonPath("$[1].studentEmail").value("student2@example.com"));
    }

    @Test
    void updateAllSchools_returnsList() throws Exception {
        List<Punishment> updated = List.of(
                Punishment.builder().punishmentId("school1").school("High School A").build(),
                Punishment.builder().punishmentId("school2").school("High School B").build()
        );

        when(punishmentService.updateSchools()).thenReturn(updated);

        mockMvc.perform(put("/punish/v1/schoolName"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].punishmentId").value("school1"))
                .andExpect(jsonPath("$[0].school").value("High School A"))
                .andExpect(jsonPath("$[1].punishmentId").value("school2"))
                .andExpect(jsonPath("$[1].school").value("High School B"));
    }

    @Test
    void updateAllInfractionName_returnsList() throws Exception {
        List<Punishment> updated = List.of(
                Punishment.builder().punishmentId("infraction1").infractionName("Tardiness").build(),
                Punishment.builder().punishmentId("infraction2").infractionName("Disruptive Behavior").build()
        );

        when(punishmentService.updateInfractionName()).thenReturn(updated);

        mockMvc.perform(put("/punish/v1/infractionName"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].punishmentId").value("infraction1"))
                .andExpect(jsonPath("$[0].infractionName").value("Tardiness"))
                .andExpect(jsonPath("$[1].punishmentId").value("infraction2"))
                .andExpect(jsonPath("$[1].infractionName").value("Disruptive Behavior"));
    }

    @Test
    void updateAllInfractionLevel_returnsList() throws Exception {
        List<Punishment> updated = List.of(
                Punishment.builder().punishmentId("level1").infractionLevel("1").build(),
                Punishment.builder().punishmentId("level2").infractionLevel("3").build()
        );

        when(punishmentService.updateInfractionLevel()).thenReturn(updated);

        mockMvc.perform(put("/punish/v1/infractionLevel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].punishmentId").value("level1"))
                .andExpect(jsonPath("$[0].infractionLevel").value("1"))
                .andExpect(jsonPath("$[1].punishmentId").value("level2"))
                .andExpect(jsonPath("$[1].infractionLevel").value("3"));
    }

    @Test
    void updateAllFix_returnsList() throws Exception {
        List<Punishment> updated = List.of(
                Punishment.builder().punishmentId("fix1").timeCreated(LocalDate.now().minusDays(1)).build(),
                Punishment.builder().punishmentId("fix2").timeCreated(LocalDate.now()).build()
        );

        when(punishmentService.updateTimeCreated()).thenReturn(updated);

        mockMvc.perform(put("/punish/v1/updates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].punishmentId").value("fix1"))
                .andExpect(jsonPath("$[1].punishmentId").value("fix2"));
    }


    @Test
    void deletePunishment_returnsOkMessage() throws Exception {
        Punishment punishment = Punishment.builder().punishmentId("del123").build();
        String expectedResponse = "Deleted successfully";

        when(punishmentService.deletePunishment(punishment)).thenReturn(expectedResponse);

        mockMvc.perform(delete("/punish/v1/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(punishment)))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedResponse));
    }
}
