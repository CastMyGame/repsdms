package com.reps.demogcloud.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.assignments.Assignment;
import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import com.reps.demogcloud.models.dto.AssignmentTemplateSummaryDTO;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.security.config.SecurityConfig;
import com.reps.demogcloud.security.services.JwtFilterRequest;
import com.reps.demogcloud.services.AssignmentService;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(
        controllers = AssignmentController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilterRequest.class)
        }
)
class AssignmentControllerTest {

    private final Assignment testAssignment = new Assignment();
    private final AssignmentTemplate testTemplate = new AssignmentTemplate();
    private final AssignmentTemplateSummaryDTO testSummary = new AssignmentTemplateSummaryDTO();
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AssignmentService assignmentService;
    @MockitoBean
    private PunishRepository punishRepository;
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
    void getAllAssignments_returns500_whenServiceFails() throws Exception {
        when(assignmentService.getAllAssignments()).thenThrow(new RuntimeException("Failed to fetch"));

        mockMvc.perform(get("/assignments/v1/"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getTemplateForPunishment_returnsOk_withTemplate() throws Exception {
        String punishmentId = "pun-1";

        when(assignmentService.buildAssignmentForPunishment(punishmentId)).thenReturn(testTemplate);

        mockMvc.perform(get("/assignments/v1/templates/for-punishment/{punishmentId}", punishmentId))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(testTemplate)));
    }

    @Test
    void getTemplateForPunishment_returns500_whenServiceFails() throws Exception {
        String punishmentId = "pun-1";

        when(assignmentService.buildAssignmentForPunishment(punishmentId))
                .thenThrow(new RuntimeException("Failed to build template"));

        mockMvc.perform(get("/assignments/v1/templates/for-punishment/{punishmentId}", punishmentId))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void searchTemplates_returnsOk_withResults() throws Exception {
        when(assignmentService.searchTemplates("Tardy", 2, "teacher@test.com", true, "reflection"))
                .thenReturn(List.of(testSummary));

        mockMvc.perform(get("/assignments/v1/templates/search")
                        .param("infractionName", "Tardy")
                        .param("level", "2")
                        .param("creatorEmail", "teacher@test.com")
                        .param("createdBySystem", "true")
                        .param("q", "reflection"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(List.of(testSummary))));
    }

    @Test
    void searchTemplates_returnsOk_withEmptyList_whenNoParamsProvided() throws Exception {
        when(assignmentService.searchTemplates(null, null, null, null, null))
                .thenReturn(List.of());

        mockMvc.perform(get("/assignments/v1/templates/search"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(List.of())));
    }

    @Test
    void getAllTemplates_returnsOk_withTemplates() throws Exception {
        when(assignmentService.getAllTemplates()).thenReturn(List.of(testTemplate));

        mockMvc.perform(get("/assignments/v1/templates"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(List.of(testTemplate))));
    }

    @Test
    void getAllTemplates_returnsOk_withEmptyList() throws Exception {
        when(assignmentService.getAllTemplates()).thenReturn(List.of());

        mockMvc.perform(get("/assignments/v1/templates"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(List.of())));
    }

    @Test
    void getTemplatesByInfractionAndLevel_returnsOk_withTemplates() throws Exception {
        when(assignmentService.getTemplatesByInfractionAndLevel("Tardy", 3))
                .thenReturn(List.of(testTemplate));

        mockMvc.perform(get("/assignments/v1/templates/by-infraction")
                        .param("infractionName", "Tardy")
                        .param("level", "3"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(List.of(testTemplate))));
    }

    @Test
    void getTemplatesByInfractionAndLevel_returnsOk_withEmptyList() throws Exception {
        when(assignmentService.getTemplatesByInfractionAndLevel("Tardy", 3))
                .thenReturn(List.of());

        mockMvc.perform(get("/assignments/v1/templates/by-infraction")
                        .param("infractionName", "Tardy")
                        .param("level", "3"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(List.of())));
    }

    @Test
    void getTemplateById_returnsOk_withTemplate() throws Exception {
        String templateId = "template-1";

        when(assignmentService.getTemplateById(templateId)).thenReturn(testTemplate);

        mockMvc.perform(get("/assignments/v1/templates/{id}", templateId))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(testTemplate)));
    }

    @Test
    void getTemplateById_returns500_whenServiceFails() throws Exception {
        String templateId = "template-1";

        when(assignmentService.getTemplateById(templateId))
                .thenThrow(new RuntimeException("Template lookup failed"));

        mockMvc.perform(get("/assignments/v1/templates/{id}", templateId))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getAssignmentTemplateForPunishment_returnsOk_whenPunishmentHasTemplateId() throws Exception {
        String punishmentId = "pun-1";
        String templateId = "template-1";

        Punishment punishment = new Punishment();
        punishment.setAssignmentTemplateId(templateId);

        when(punishRepository.findById(punishmentId)).thenReturn(Optional.of(punishment));
        when(assignmentService.getTemplateById(templateId)).thenReturn(testTemplate);

        mockMvc.perform(get("/assignments/v1/{id}/assignment-template", punishmentId))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(testTemplate)));
    }

    @Test
    void getAssignmentTemplateForPunishment_returns404_whenPunishmentHasNoTemplateId() throws Exception {
        String punishmentId = "pun-1";

        Punishment punishment = new Punishment();
        punishment.setAssignmentTemplateId(null);

        when(punishRepository.findById(punishmentId)).thenReturn(Optional.of(punishment));

        mockMvc.perform(get("/assignments/v1/{id}/assignment-template", punishmentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAssignmentTemplateForPunishment_returns500_whenPunishmentNotFound() throws Exception {
        String punishmentId = "missing-punishment";

        when(punishRepository.findById(punishmentId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/assignments/v1/{id}/assignment-template", punishmentId))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getAssignmentForPunishment_returnsOk_withAssignmentTemplate() throws Exception {
        String punishmentId = "pun-1";

        when(assignmentService.buildAssignmentForPunishment(punishmentId)).thenReturn(testTemplate);

        mockMvc.perform(get("/assignments/v1/by-punishment/{punishmentId}", punishmentId))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(testTemplate)));
    }

    @Test
    void getAssignmentForPunishment_returns500_whenServiceFails() throws Exception {
        String punishmentId = "pun-1";

        when(assignmentService.buildAssignmentForPunishment(punishmentId))
                .thenThrow(new RuntimeException("Failed to build assignment"));

        mockMvc.perform(get("/assignments/v1/by-punishment/{punishmentId}", punishmentId))
                .andExpect(status().isInternalServerError());
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
    void createNewAssignment_returns500_whenServiceFails() throws Exception {
        when(assignmentService.createNewAssignment(any(Assignment.class)))
                .thenThrow(new RuntimeException("Creation error"));

        mockMvc.perform(post("/assignments/v1/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testAssignment)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void migrateLegacyAssignments_returnsOk_withMessage() throws Exception {
        when(assignmentService.migrateLegacyAssignmentsToTemplates()).thenReturn(17);

        mockMvc.perform(post("/assignments/v1/migrate-legacy"))
                .andExpect(status().isOk())
                .andExpect(content().string("Migrated 17 legacy assignments to templates."));
    }

    @Test
    void createTemplate_returnsOk_withCreatedTemplate() throws Exception {
        when(assignmentService.createAssignmentTemplate(any(AssignmentTemplate.class)))
                .thenReturn(testTemplate);

        mockMvc.perform(post("/assignments/v1/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testTemplate)))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(testTemplate)));
    }

    @Test
    void createTemplate_returns500_whenServiceFails() throws Exception {
        when(assignmentService.createAssignmentTemplate(any(AssignmentTemplate.class)))
                .thenThrow(new RuntimeException("Template creation failed"));

        mockMvc.perform(post("/assignments/v1/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testTemplate)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void updateAssignment_returnsAccepted_withAssignment() throws Exception {
        String id = "abc123";

        when(assignmentService.updateNewAssignment(any(Assignment.class), eq(id)))
                .thenReturn(testAssignment);

        mockMvc.perform(put("/assignments/v1/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testAssignment)))
                .andExpect(status().isAccepted())
                .andExpect(content().json(objectMapper.writeValueAsString(testAssignment)));
    }

    @Test
    void updateAssignment_returns500_whenServiceFails() throws Exception {
        String id = "invalid-id";

        when(assignmentService.updateNewAssignment(any(Assignment.class), eq(id)))
                .thenThrow(new RuntimeException("Update failed"));

        mockMvc.perform(put("/assignments/v1/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testAssignment)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void updateTemplate_returnsOk_withUpdatedTemplate() throws Exception {
        String templateId = "template-1";

        when(assignmentService.updateAssignmentTemplate(eq(templateId), any(AssignmentTemplate.class)))
                .thenReturn(testTemplate);

        mockMvc.perform(put("/assignments/v1/templates/{id}", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testTemplate)))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(testTemplate)));
    }

    @Test
    void updateTemplate_returns500_whenServiceFails() throws Exception {
        String templateId = "template-1";

        when(assignmentService.updateAssignmentTemplate(eq(templateId), any(AssignmentTemplate.class)))
                .thenThrow(new RuntimeException("Template update failed"));

        mockMvc.perform(put("/assignments/v1/templates/{id}", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testTemplate)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void deleteAssignment_returnsAccepted_withAssignment() throws Exception {
        String assignmentName = "SampleAssignment";

        when(assignmentService.deleteAssignment(assignmentName)).thenReturn(testAssignment);

        mockMvc.perform(delete("/assignments/v1/delete/{assignmentName}", assignmentName))
                .andExpect(status().isAccepted())
                .andExpect(content().json(objectMapper.writeValueAsString(testAssignment)));
    }

    @Test
    void deleteAssignment_returns500_whenServiceFails() throws Exception {
        String assignmentName = "MissingAssignment";

        when(assignmentService.deleteAssignment(assignmentName))
                .thenThrow(new RuntimeException("Delete failed"));

        mockMvc.perform(delete("/assignments/v1/delete/{assignmentName}", assignmentName))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void deleteTemplate_returnsNoContent() throws Exception {
        String templateId = "template-1";

        doNothing().when(assignmentService).deleteAssignmentTemplate(templateId);

        mockMvc.perform(delete("/assignments/v1/templates/{id}", templateId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteTemplate_returns500_whenServiceFails() throws Exception {
        String templateId = "template-1";

        doThrow(new RuntimeException("Template delete failed"))
                .when(assignmentService).deleteAssignmentTemplate(templateId);

        mockMvc.perform(delete("/assignments/v1/templates/{id}", templateId))
                .andExpect(status().isInternalServerError());
    }
}