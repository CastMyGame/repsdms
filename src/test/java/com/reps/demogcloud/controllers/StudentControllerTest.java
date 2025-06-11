package com.reps.demogcloud.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import com.reps.demogcloud.models.student.*;
import com.reps.demogcloud.security.config.SecurityConfig;
import com.reps.demogcloud.security.services.UserService;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.mail.MessagingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@WebMvcTest(controllers = StudentController.class)
public class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentService studentService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    private Student student;
    private StudentResponse studentResponse;

    @BeforeEach
    void setup() {
        student = new Student();
        student.setStudentEmail("student@example.com");
        student.setStudentIdNumber("123");
        student.setLastName("Smith");

        studentResponse = new StudentResponse();
        studentResponse.setStudent(student);
    }

    // --- GET /student/v1/ ---
    @Test
    void getHome_returns200() throws Exception {
        mockMvc.perform(get("/student/v1/"))
                .andExpect(status().isOk());
    }

    // --- GET /student/v1/studentid/{studentId} ---
    @Test
    void getStudentByIdNumber_happyPath_returnsStudent() throws Exception {
        when(studentService.findByStudentId("123")).thenReturn(student);

        mockMvc.perform(get("/student/v1/studentid/123"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.studentEmail").value("student@example.com"));
    }

    @Test
    void getStudentByIdNumber_sadPath_notFound() throws Exception {
        when(studentService.findByStudentId("999")).thenThrow(new RuntimeException("Not found"));

        mockMvc.perform(get("/student/v1/studentid/999"))
                .andExpect(status().is5xxServerError());  // or customize if you have specific exception handler
    }

    // --- GET /student/v1/allStudents ---
    @Test
    void findAllStudents_returnsList() throws Exception {
        List<Student> list = List.of(student);
        when(studentService.getAllStudents(false)).thenReturn(list);

        mockMvc.perform(get("/student/v1/allStudents"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].studentEmail").value("student@example.com"));
    }

    // --- GET /student/v1/archived ---
    @Test
    void getAllArchived_returnsArchivedStudents() throws Exception {
        List<Student> list = List.of(student);
        when(studentService.getAllStudents(true)).thenReturn(list);

        mockMvc.perform(get("/student/v1/archived"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].studentEmail").value("student@example.com"));
    }

    // --- GET /student/v1/lastname/{lastName} ---
    @Test
    void getStudentByLastName_happyPath() throws Exception {
        List<Student> list = List.of(student);
        when(studentService.findByStudentLastName("Smith")).thenReturn(list);

        mockMvc.perform(get("/student/v1/lastname/Smith"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].lastName").value("Smith"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"TEACHER"})
    void getStudentByLastName_sadPath_throwsException() throws Exception {
        when(studentService.findByStudentLastName("NoName"))
                .thenThrow(new ResourceNotFoundException("That student does not exist"));

        mockMvc.perform(get("/student/v1/lastname/NoName"))
                .andExpect(status().isNotFound());
    }


    // --- GET /student/v1/email/{email} ---
    @Test
    void getStudentByEmail_happyPath() throws Exception {
        when(studentService.findByStudentEmail("student@example.com")).thenReturn(student);

        mockMvc.perform(get("/student/v1/email/student@example.com"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.studentEmail").value("student@example.com"));
    }

    @Test
    void getStudentByEmail_sadPath_throwsException() throws Exception {
        when(studentService.findByStudentEmail("bad@example.com")).thenThrow(new Exception("Not found"));

        mockMvc.perform(get("/student/v1/email/bad@example.com"))
                .andExpect(status().is5xxServerError());
    }

    // --- POST /student/v1/newStudent ---
    @Test
    void createStudent_success_created() throws Exception {
        when(studentService.createNewStudent(any(Student.class))).thenReturn(studentResponse);

        mockMvc.perform(post("/student/v1/newStudent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(student)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Success"));
    }

    @Test
    void createStudent_failure_badRequest() throws Exception {
        StudentResponse badResponse = new StudentResponse();
        badResponse.setStudent(null);
        badResponse.setError("Failed");

        when(studentService.createNewStudent(any(Student.class))).thenReturn(badResponse);

        mockMvc.perform(post("/student/v1/newStudent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(student)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Failed"));
    }

    // --- POST /student/v1/addStudents ---
    @Test
    void addAllStudents_returnsAccepted() throws Exception {
        List<Student> students = List.of(student);
        List<StudentResponse> responses = List.of(studentResponse);

        when(studentService.createNewStudent(any(Student.class))).thenReturn(studentResponse);

        mockMvc.perform(post("/student/v1/addStudents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(students)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].message").value("Success"));
    }

    // --- POST /student/v1/points/add ---
    @Test
    void addPoints_success() throws Exception {
        when(studentService.addPoints(eq("student@example.com"), eq(10))).thenReturn(student);

        mockMvc.perform(post("/student/v1/points/add")
                        .param("studentEmail", "student@example.com")
                        .param("points", "10"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.studentEmail").value("student@example.com"));
    }

    // --- POST /student/v1/points/delete ---
    @Test
    void deletePoints_success() throws Exception {
        when(studentService.deletePoints(eq("student@example.com"), eq(5))).thenReturn(student);

        mockMvc.perform(post("/student/v1/points/delete")
                        .param("studentEmail", "student@example.com")
                        .param("points", "5"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.studentEmail").value("student@example.com"));
    }

    // --- POST /student/v1/points/transfer ---
    @Test
    void transferPoints_success() throws Exception {
        List<Student> transferredStudents = List.of(student, student);
        when(studentService.transferPoints(eq("giver@example.com"), eq("receiver@example.com"), eq(3))).thenReturn(transferredStudents);

        mockMvc.perform(post("/student/v1/points/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"giver@example.com\"") // body is givingStudentEmail as string JSON
                        .param("receivingStudentEmail", "receiver@example.com")
                        .param("pointsTransferred", "3"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // --- POST /student/v1/getByEmailList ---
    @Test
    void getStudentByEmailList_success() throws Exception {
        List<String> emails = List.of("student@example.com");
        List<Student> students = List.of(student);

        when(studentService.findByStudentEmailList(emails)).thenReturn(students);

        mockMvc.perform(post("/student/v1/getByEmailList")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emails)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].studentEmail").value("student@example.com"));
    }

    // --- POST /student/v1/{studentEmail}/add-time ---
    @Test
    void addTimeToStudent_success() throws Exception {
        when(studentService.addTimeToStudent(eq("student@example.com"), eq(1), eq(30))).thenReturn(student);

        mockMvc.perform(post("/student/v1/student@example.com/add-time")
                        .param("hours", "1")
                        .param("minutes", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentEmail").value("student@example.com"));
    }

    // --- PUT /student/v1/assignSchool ---
    @Test
    void massAssignSchool_success() throws Exception {
        List<Student> students = List.of(student);
        when(studentService.massAssignForSchool()).thenReturn(students);

        mockMvc.perform(put("/student/v1/assignSchool"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].studentEmail").value("student@example.com"));
    }

    // --- PUT /student/v1/updateStudents ---
    @Test
    void updateStudents_success() throws Exception {
        List<Student> students = List.of(student);
        when(studentService.updateStudents(anyList())).thenReturn(students);

        mockMvc.perform(put("/student/v1/updateStudents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(students)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].studentEmail").value("student@example.com"));
    }

    // --- PUT /student/v1/archived/{studentId} ---
    @Test
    void archivedDeleted_success() throws Exception {
        when(studentService.archiveRecord("123")).thenReturn(student);

        mockMvc.perform(put("/student/v1/archived/123"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.studentEmail").value("student@example.com"));
    }

    // --- PUT /student/v1/notes/{id} ---
    @Test
    void updateGuidance_success() throws Exception {
        ThreadEvent event = new ThreadEvent();
        when(studentService.updateStudentNotes(eq("123"), any(ThreadEvent.class))).thenReturn(student);

        mockMvc.perform(put("/student/v1/notes/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.studentEmail").value("student@example.com"));
    }

    // --- PUT /student/v1/addAsSpotter ---
    @Test
    void addAsSpotter_success() throws Exception {
        UpdateSpottersRequest req = new UpdateSpottersRequest();
        List<Student> students = List.of(student);
        when(studentService.addAsSpotter(any(UpdateSpottersRequest.class))).thenReturn(students);

        mockMvc.perform(put("/student/v1/addAsSpotter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].studentEmail").value("student@example.com"));
    }

    // --- PUT /student/v1/removeAsSpotter ---
    @Test
    void deleteSpotters_success() throws Exception {
        UpdateSpottersRequest req = new UpdateSpottersRequest();
        List<Student> students = List.of(student);
        when(studentService.deleteSpotters(any(UpdateSpottersRequest.class))).thenReturn(students);

        mockMvc.perform(put("/student/v1/removeAsSpotter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].studentEmail").value("student@example.com"));
    }

    // --- DELETE /student/v1/delete ---
    @Test
    void deleteStudent_success() throws Exception {
        StudentRequest request = new StudentRequest();
        request.setStudent(student);

        when(studentService.deleteStudent(any(StudentRequest.class))).thenReturn("Deleted successfully");

        mockMvc.perform(delete("/student/v1/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Deleted successfully"));
    }

    // --- PUT /student/v1/remove-spotter/{email} ---
    @Test
    void removeSpotterByEmail_success() throws Exception {
        when(studentService.removeSpotterByEmail(eq("student@example.com"), any(Student.class))).thenReturn(student);

        mockMvc.perform(put("/student/v1/remove-spotter/student@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(student)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.studentEmail").value("student@example.com"));
    }

}

