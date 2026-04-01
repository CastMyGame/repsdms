package com.reps.demogcloud.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.employee.ClassRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.employee.EmployeeResponse;
import com.reps.demogcloud.models.student.CurrencySpendRequest;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.config.SecurityConfig;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.services.JwtFilterRequest;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(
        controllers = EmployeeController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilterRequest.class)
        }
)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @MockitoBean
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    private Employee employee;
    private RoleModel role;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setEmployeeId("123");
        employee.setEmail("teacher@example.com");

        role = new RoleModel();
        role.setRole("ROLE_TEACHER");
    }

    // ---------- GET TESTS ----------

    @Test
    void getAllUsers_returnsListOfEmployees() throws Exception {
        when(employeeService.findAll()).thenReturn(List.of(employee));

        mockMvc.perform(get("/employees/v1/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("teacher@example.com"));

        verify(employeeService).findAll();
    }

    @Test
    void getUserById_returnsEmployee() throws Exception {
        when(employeeService.findByUserName("teacher@example.com")).thenReturn(employee);

        mockMvc.perform(get("/employees/v1/employees/email/teacher@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("teacher@example.com"));

        verify(employeeService).findByUserName("teacher@example.com");
    }

    @Test
    void getAllEmployeesByRole_returnsEmployees() throws Exception {
        when(employeeService.findAllByRole("TEACHER"))
                .thenReturn(Optional.of(List.of(employee)));

        mockMvc.perform(get("/employees/v1/employees/TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("teacher@example.com"));

        verify(employeeService).findAllByRole("TEACHER");
    }

    @Test
    void getAllEmployeesByRole_returnsNotFound_whenEmptyOrMissing() throws Exception {
        when(employeeService.findAllByRole("TEACHER"))
                .thenReturn(Optional.of(List.of()));

        mockMvc.perform(get("/employees/v1/employees/TEACHER"))
                .andExpect(status().isNotFound());

        when(employeeService.findAllByRole("TEACHER"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/employees/v1/employees/TEACHER"))
                .andExpect(status().isNotFound());
    }

    // ---------- POST TESTS ----------

    @Test
    void createEmployee_returnsEmployeeResponse() throws Exception {
        EmployeeResponse response = new EmployeeResponse();
        response.setEmployee(employee);

        when(employeeService.createNewEmployee(any())).thenReturn(response);

        mockMvc.perform(post("/employees/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.email").value("teacher@example.com"));

        verify(employeeService).createNewEmployee(any());
    }

    @Test
    void createEmployeeList_returnsEmployeeResponses() throws Exception {
        EmployeeResponse response = new EmployeeResponse();
        response.setEmployee(employee);

        when(employeeService.createNewEmployeeList(any()))
                .thenReturn(List.of(response));

        mockMvc.perform(post("/employees/v1/employees/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(employee))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employee.email").value("teacher@example.com"));

        verify(employeeService).createNewEmployeeList(any());
    }

    @Test
    void deleteClassRoster_returnsEmployee() throws Exception {
        ClassRequest request = new ClassRequest();

        when(employeeService.removeClassFromEmployee(any(), any()))
                .thenReturn(employee);

        mockMvc.perform(post("/employees/v1/deleteClass/teacher@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("teacher@example.com"));
    }

    @Test
    void deleteClassRoster_returnsBadRequest_whenNull() throws Exception {
        ClassRequest request = new ClassRequest();

        when(employeeService.removeClassFromEmployee(any(), any()))
                .thenReturn(null);

        mockMvc.perform(post("/employees/v1/deleteClass/teacher@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---------- PUT TESTS ----------

    @Test
    void updateEmployeesRole_success() throws Exception {
        Set<RoleModel> roles = Set.of(role);
        employee.setRoles(roles);

        when(employeeRepository.findById("123")).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any())).thenReturn(employee);

        mockMvc.perform(put("/employees/v1/employees/123/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roles)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0].role").value("ROLE_TEACHER"));
    }

    @Test
    void updateEmployeesRole_notFound() throws Exception {
        when(employeeRepository.findById("123")).thenReturn(Optional.empty());

        mockMvc.perform(put("/employees/v1/employees/123/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Set.of(role))))
                .andExpect(status().isNotFound());
    }

    @Test
    void spendCurrency_returnsAccepted() throws Exception {
        when(employeeService.spendCurrency(any()))
                .thenReturn(List.of(new Student()));

        mockMvc.perform(put("/employees/v1/currency/spend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new CurrencySpendRequest()))))
                .andExpect(status().isAccepted());

        verify(employeeService).spendCurrency(any());
    }

    @Test
    void editSchool_success_and_failure() throws Exception {
        when(employeeService.editSchool("MySchool")).thenReturn(List.of(employee));

        mockMvc.perform(put("/employees/v1/MySchool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("teacher@example.com"));

        when(employeeService.editSchool("MySchool")).thenReturn(null);

        mockMvc.perform(put("/employees/v1/MySchool"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateClassRoster_success_and_failure() throws Exception {
        ClassRequest request = new ClassRequest();

        when(employeeService.addOrUpdateClassToEmployee(any(), any()))
                .thenReturn(employee);

        mockMvc.perform(put("/employees/v1/updateClass/teacher@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        when(employeeService.addOrUpdateClassToEmployee(any(), any()))
                .thenReturn(null);

        mockMvc.perform(put("/employees/v1/updateClass/teacher@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAllEmployees_success_and_failure() throws Exception {
        when(employeeService.updateAllEmployees()).thenReturn(List.of(employee));

        mockMvc.perform(put("/employees/v1/updateAll"))
                .andExpect(status().isOk());

        when(employeeService.updateAllEmployees()).thenReturn(null);

        mockMvc.perform(put("/employees/v1/updateAll"))
                .andExpect(status().isBadRequest());
    }

    // ---------- DELETE TESTS ----------

    @Test
    void deleteEmployee_success_and_notFound() throws Exception {
        doNothing().when(employeeService).deleteEmployee("123");

        mockMvc.perform(delete("/employees/v1/employees/123"))
                .andExpect(status().isOk())
                .andExpect(content().string("Employee with ID 123 has been deleted."));

        doThrow(new RuntimeException("not found"))
                .when(employeeService).deleteEmployee("123");

        mockMvc.perform(delete("/employees/v1/employees/123"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Employee with ID 123 not found."));
    }
}