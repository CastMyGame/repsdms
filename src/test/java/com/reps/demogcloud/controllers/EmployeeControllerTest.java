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
import com.reps.demogcloud.security.services.UserService;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
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
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeService employeeService;

    @MockBean
    private EmployeeRepository employeeRepository;

    @MockBean
    private UserService userService;

    @MockBean
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

    @Test
    void getAllUsers_returnsListOfEmployees() throws Exception {
        List<Employee> employees = List.of(employee);
        when(employeeService.findAll()).thenReturn(employees);

        mockMvc.perform(get("/employees/v1/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("teacher@example.com"));
    }

    @Test
    void updateEmployeesRole_whenEmployeeExists_returnsUpdatedEmployee() throws Exception {
        Set<RoleModel> roles = Set.of(role);
        employee.setRoles(roles);

        when(employeeRepository.findById("123")).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

        mockMvc.perform(put("/employees/v1/employees/123/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roles)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0].role").value("ROLE_TEACHER"));
    }

    @Test
    void deleteEmployeeById_whenEmployeeExists_returnsOk() throws Exception {
        doNothing().when(employeeService).deleteEmployee("123");

        mockMvc.perform(delete("/employees/v1/employees/123"))
                .andExpect(status().isOk())
                .andExpect(content().string("Employee with ID 123 has been deleted."));
    }

    @Test
    void createEmployee_returnsEmployeeResponse() throws Exception {
        EmployeeResponse response = new EmployeeResponse();
        response.setEmployee(employee);

        when(employeeService.createNewEmployee(any(Employee.class))).thenReturn(response);

        mockMvc.perform(post("/employees/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.email").value("teacher@example.com"));
    }

    @Test
    void getUserById_returnsEmployee() throws Exception {
        when(employeeService.findByUserName("teacher@example.com")).thenReturn(employee);

        mockMvc.perform(get("/employees/v1/employees/email/teacher@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("teacher@example.com"));
    }

    @Test
    void getAllEmployeesByRole_returnsEmployees() throws Exception {
        when(employeeService.findAllByRole("TEACHER")).thenReturn(Optional.of(List.of(employee)));

        mockMvc.perform(get("/employees/v1/employees/TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("teacher@example.com"));
    }

    @Test
    void getAllEmployeesByRole_whenEmptyList_returnsNotFound() throws Exception {
        when(employeeService.findAllByRole("TEACHER")).thenReturn(Optional.of(List.of()));

        mockMvc.perform(get("/employees/v1/employees/TEACHER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllEmployeesByRole_whenOptionalEmpty_returnsNotFound() throws Exception {
        when(employeeService.findAllByRole("TEACHER")).thenReturn(Optional.empty());

        mockMvc.perform(get("/employees/v1/employees/TEACHER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createEmployeeList_returnsEmployeeResponses() throws Exception {
        EmployeeResponse response = new EmployeeResponse();
        response.setEmployee(employee);

        when(employeeService.createNewEmployeeList(any())).thenReturn(List.of(response));

        mockMvc.perform(post("/employees/v1/employees/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(employee))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employee.email").value("teacher@example.com"));
    }

    @Test
    void spendCurrency_returnsAcceptedResponse() throws Exception {
        CurrencySpendRequest request = new CurrencySpendRequest();
        Student student = new Student();

        when(employeeService.spendCurrency(any())).thenReturn(List.of(student));

        mockMvc.perform(put("/employees/v1/currency/spend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(request))))
                .andExpect(status().isAccepted());
    }

    @Test
    void editSchool_returnsUpdatedEmployees() throws Exception {
        when(employeeService.editSchool("MySchool")).thenReturn(List.of(employee));

        mockMvc.perform(put("/employees/v1/MySchool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("teacher@example.com"));
    }

    @Test
    void editSchool_returnsBadRequest_whenNull() throws Exception {
        when(employeeService.editSchool("MySchool")).thenReturn(null);

        mockMvc.perform(put("/employees/v1/MySchool"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateClassRoster_returnsUpdatedEmployee() throws Exception {
        ClassRequest request = new ClassRequest(); // fill as needed

        when(employeeService.addOrUpdateClassToEmployee(any(), any())).thenReturn(employee);

        mockMvc.perform(put("/employees/v1/updateClass/teacher@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("teacher@example.com"));
    }

    @Test
    void updateClassRoster_returnsBadRequest_whenNull() throws Exception {
        ClassRequest request = new ClassRequest();

        when(employeeService.addOrUpdateClassToEmployee(any(), any())).thenReturn(null);

        mockMvc.perform(put("/employees/v1/updateClass/teacher@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAllEmployees_returnsUpdatedList() throws Exception {
        when(employeeService.updateAllEmployees()).thenReturn(List.of(employee));

        mockMvc.perform(put("/employees/v1/updateAll"))
                .andExpect(status().isOk());
    }

    @Test
    void updateAllEmployees_returnsBadRequest_whenNull() throws Exception {
        when(employeeService.updateAllEmployees()).thenReturn(null);

        mockMvc.perform(put("/employees/v1/updateAll"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteClassRoster_returnsUpdatedEmployee() throws Exception {
        ClassRequest request = new ClassRequest();

        when(employeeService.removeClassFromEmployee(any(), any())).thenReturn(employee);

        mockMvc.perform(post("/employees/v1/deleteClass/teacher@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("teacher@example.com"));
    }

    @Test
    void deleteClassRoster_returnsBadRequest_whenNull() throws Exception {
        ClassRequest request = new ClassRequest();

        when(employeeService.removeClassFromEmployee(any(), any())).thenReturn(null);

        mockMvc.perform(post("/employees/v1/deleteClass/teacher@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateEmployeesRole_whenEmployeeNotFound_returnsNotFound() throws Exception {
        when(employeeRepository.findById("123")).thenReturn(Optional.empty());

        mockMvc.perform(put("/employees/v1/employees/123/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Set.of(role))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteEmployeeById_whenExceptionThrown_returnsNotFound() throws Exception {
        doThrow(new RuntimeException("Employee not found"))
                .when(employeeService).deleteEmployee("123");

        mockMvc.perform(delete("/employees/v1/employees/123"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Employee with ID 123 not found."));
    }

}

