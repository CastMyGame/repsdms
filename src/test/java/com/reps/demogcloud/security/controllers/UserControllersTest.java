package com.reps.demogcloud.security.controllers;

import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import com.reps.demogcloud.security.services.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllersTest {

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private UserRepository userRepository;

    private UserControllers controller;

    @BeforeEach
    void setUp() {
        controller = new UserControllers(userAccountService, userRepository);
    }

    private RoleModel role(String roleName) {
        RoleModel roleModel = new RoleModel();
        roleModel.setRole(roleName);
        return roleModel;
    }

    private UserModel userWithRoles(String... roleNames) {
        UserModel user = new UserModel();
        Set<RoleModel> roles = new HashSet<>();
        for (String roleName : roleNames) {
            roles.add(role(roleName));
        }
        user.setRoles(roles);
        return user;
    }

    @Test
    void getAllUsers_shouldReturnAllUsers() {
        UserModel user1 = new UserModel();
        UserModel user2 = new UserModel();

        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        ResponseEntity<List<UserModel>> response = controller.getAllUsers();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        assertEquals(List.of(user1, user2), response.getBody());
        verify(userRepository).findAll();
    }

    @Test
    void getAllUsersByRole_shouldReturnMatchingUsers() {
        UserModel adminUser = userWithRoles("ADMIN");
        UserModel teacherUser = userWithRoles("TEACHER");
        UserModel dualRoleUser = userWithRoles("TEACHER", "ADMIN");

        when(userRepository.findAll()).thenReturn(List.of(adminUser, teacherUser, dualRoleUser));

        ResponseEntity<List<UserModel>> response = controller.getAllUsersByRole("ADMIN");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        assertEquals(List.of(adminUser, dualRoleUser), response.getBody());
        verify(userRepository).findAll();
    }

    @Test
    void getAllUsersByRole_shouldReturnEmptyList_whenNoUsersMatch() {
        UserModel teacherUser = userWithRoles("TEACHER");
        UserModel studentUser = userWithRoles("STUDENT");

        when(userRepository.findAll()).thenReturn(List.of(teacherUser, studentUser));

        ResponseEntity<List<UserModel>> response = controller.getAllUsersByRole("ADMIN");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().size());
        verify(userRepository).findAll();
    }

    @Test
    void getAllUsersByRole_shouldIgnoreUsersWithNullRoles() {
        UserModel nullRoleUser = new UserModel();
        nullRoleUser.setRoles(null);

        UserModel adminUser = userWithRoles("ADMIN");

        when(userRepository.findAll()).thenReturn(List.of(nullRoleUser, adminUser));

        ResponseEntity<List<UserModel>> response = controller.getAllUsersByRole("ADMIN");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(adminUser, response.getBody().get(0));
        verify(userRepository).findAll();
    }

    @Test
    void createNewUsers_shouldReturnCreatedUsers() {
        UserModel user1 = new UserModel();
        UserModel user2 = new UserModel();

        when(userAccountService.createUsersForSchool("TestSchool")).thenReturn(List.of(user1, user2));

        ResponseEntity<List<UserModel>> response = controller.createNewUsers("TestSchool");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(user1, user2), response.getBody());
        verify(userAccountService).createUsersForSchool("TestSchool");
    }

    @Test
    void lowercaseThemAll_shouldReturnUpdatedUsers() {
        UserModel user1 = new UserModel();
        UserModel user2 = new UserModel();

        when(userAccountService.lowerCaseThemAll("TestSchool")).thenReturn(List.of(user1, user2));

        ResponseEntity<List<UserModel>> response = controller.lowercaseThemAll("TestSchool");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(user1, user2), response.getBody());
        verify(userAccountService).lowerCaseThemAll("TestSchool");
    }

    @Test
    void updateUsersRole_shouldReturnUpdatedUser_whenUserExists() {
        String id = "123";
        UserModel existingUser = new UserModel();

        Set<RoleModel> newRoles = Set.of(role("ADMIN"), role("TEACHER"));
        existingUser.setRoles(Set.of(role("STUDENT")));

        when(userRepository.findById(id)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        ResponseEntity<UserModel> response = controller.updateUsersRole(id, newRoles);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(existingUser, response.getBody());
        assertEquals(newRoles, existingUser.getRoles());

        verify(userRepository).findById(id);
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateUsersRole_shouldReturnNotFound_whenUserDoesNotExist() {
        String id = "123";
        Set<RoleModel> newRoles = Set.of(role("ADMIN"));

        when(userRepository.findById(id)).thenReturn(Optional.empty());

        ResponseEntity<UserModel> response = controller.updateUsersRole(id, newRoles);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        verify(userRepository).findById(id);
        verify(userRepository, never()).save(any(UserModel.class));
    }

    @Test
    void deleteUserById_shouldDeleteAndReturnOk_whenUserExists() {
        String id = "123";
        UserModel user = new UserModel();

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        ResponseEntity<String> response = controller.deleteUserById(id);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("User with ID 123 has been deleted.", response.getBody());

        verify(userRepository).findById(id);
        verify(userRepository).deleteById(id);
    }

    @Test
    void deleteUserById_shouldReturnNotFound_whenUserDoesNotExist() {
        String id = "123";

        when(userRepository.findById(id)).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.deleteUserById(id);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("User with ID 123 not found.", response.getBody());

        verify(userRepository).findById(id);
        verify(userRepository, never()).deleteById(anyString());
    }
}