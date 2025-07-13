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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@WebMvcTest(StudentController.class)
@WithMockUser(username = "testuser", roles = {"TEACHER"})
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
                .andExpect(jsonPath("$.student.studentEmail").value("student@example.com"))
                .andExpect(jsonPath("$.student.studentIdNumber").value("123"))
                .andExpect(jsonPath("$.student.lastName").value("Smith"));
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
                .andExpect(jsonPath("$[0].error").doesNotExist())
                .andExpect(jsonPath("$[0].student.studentIdNumber").value("123"));
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

        when(studentService.transferPoints("giver@example.com", "receiver@example.com", 3))
                .thenReturn(transferredStudents);

        String requestBody = """
        {
            "givingStudentEmail": "giver@example.com",
            "receivingStudentEmail": "receiver@example.com",
            "pointsTransferred": 3
        }
        """;

        mockMvc.perform(post("/student/v1/points/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
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

    @Test
    void transferPoints_failure_returnsError() throws Exception {
        String requestBody = """
        {
            "givingStudentEmail": "giver@example.com",
            "receivingStudentEmail": "receiver@example.com",
            "pointsTransferred": 3
        }
        """;

        when(studentService.transferPoints(anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("Transfer failed"));

        mockMvc.perform(post("/student/v1/points/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void addTimeToStudent_failure_returnsError() throws Exception {
        when(studentService.addTimeToStudent(anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Time addition failed"));

        mockMvc.perform(post("/student/v1/student@example.com/add-time")
                        .param("hours", "1")
                        .param("minutes", "30"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void updateStudents_emptyList_returnsEmpty() throws Exception {
        when(studentService.updateStudents(anyList())).thenReturn(List.of());

        mockMvc.perform(put("/student/v1/updateStudents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isAccepted())
                .andExpect(content().string("[]"));
    }

    @Test
    void massAssignSchool_failure_returnsServerError() throws Exception {
        when(studentService.massAssignForSchool()).thenThrow(new RuntimeException("DB failure"));

        mockMvc.perform(put("/student/v1/assignSchool"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void getStudentByParentEmail_success_returnsList() throws Exception {
        List<Student> students = List.of(student);

        when(studentService.findStudentByParentEmail("parent@example.com")).thenReturn(students);

        mockMvc.perform(get("/student/v1/parentEmail/parent@example.com"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].studentEmail").value("student@example.com"));
    }

    @Test
    void getStudentByParentEmail_failure_throwsException() throws Exception {
        when(studentService.findStudentByParentEmail("bad@example.com"))
                .thenThrow(new ResourceNotFoundException("Parent not found"));

        mockMvc.perform(get("/student/v1/parentEmail/bad@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDetentionList_success_returnsList() throws Exception {
        PunishmentDTO dto = new PunishmentDTO();
        dto.setStudentEmail("student@example.com");
        List<PunishmentDTO> list = List.of(dto);

        when(studentService.getDetentionList("ExampleSchool")).thenReturn(list);

        mockMvc.perform(get("/student/v1/detentionList/ExampleSchool"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].studentEmail").value("student@example.com"));
    }

    @Test
    void getIssList_success_returnsList() throws Exception {
        PunishmentDTO dto = new PunishmentDTO();
        dto.setStudentEmail("student@example.com");
        List<PunishmentDTO> list = List.of(dto);

        when(studentService.getIssList("ExampleSchool")).thenReturn(list);

        mockMvc.perform(get("/student/v1/issList/ExampleSchool"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].studentEmail").value("student@example.com"));
    }

    @Test
    void getBySpotter_success_returnsList() throws Exception {
        List<Student> students = List.of(student);

        when(studentService.findBySpotter("spotter@example.com")).thenReturn(students);

        mockMvc.perform(get("/student/v1/findBySpotter/spotter@example.com"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].studentEmail").value("student@example.com"));
    }

    @Test
    void getStudentByParentEmail_returnsAccepted() throws Exception {
        List<Student> mockStudents = List.of(new Student());
        Mockito.when(studentService.findStudentByParentEmail("test@parent.com"))
                .thenReturn(mockStudents);

        mockMvc.perform(get("/student/v1/parentEmail/test@parent.com"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getDetentionList_returnsAccepted() throws Exception {
        List<PunishmentDTO> mockList = List.of(new PunishmentDTO());
        Mockito.when(studentService.getDetentionList("TestSchool"))
                .thenReturn(mockList);

        mockMvc.perform(get("/student/v1/detentionList/TestSchool"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getIssList_returnsAccepted() throws Exception {
        List<PunishmentDTO> mockList = List.of(new PunishmentDTO());
        Mockito.when(studentService.getIssList("TestSchool"))
                .thenReturn(mockList);

        mockMvc.perform(get("/student/v1/issList/TestSchool"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getBySpotter_returnsAccepted() throws Exception {
        List<Student> mockList = List.of(new Student());
        Mockito.when(studentService.findBySpotter("spotter@test.com"))
                .thenReturn(mockList);

        mockMvc.perform(get("/student/v1/findBySpotter/spotter@test.com"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.length()").value(1));
    }
}

