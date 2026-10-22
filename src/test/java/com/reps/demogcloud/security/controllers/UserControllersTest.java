package com.reps.demogcloud.security.controllers;

import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.services.UserAccountService;
import com.reps.demogcloud.services.UserAdministrationService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Controller response/delegation only. Real policies are tested in UserAdministrationSecurityTest. */
class UserControllersTest {
    UserAdministrationService administration=mock(UserAdministrationService.class);
    UserAccountService accounts=mock(UserAccountService.class);
    UserControllers controller=new UserControllers(accounts,administration);
    @Test void listUsesScopedAdministrationService() {
        var user=new UserModel();user.setRoles(Set.of(new RoleModel(null,"GUIDANCE")));
        when(administration.listUsers()).thenReturn(List.of(user));
        assertEquals(List.of(user),controller.getAllUsers().getBody());
        assertEquals(List.of(user),controller.getAllUsersByRole("GUIDANCE").getBody());
        assertTrue(controller.getAllUsersByRole("ADMIN").getBody().isEmpty());
    }
    @Test void rolesUseSharedSynchronizationPath() {
        var user=new UserModel();var roles=Set.of(new RoleModel(null,"TEACHER"));
        when(administration.updateUserRoles("id",roles)).thenReturn(user);
        assertSame(user,controller.updateUsersRole("id",roles).getBody());
        verify(administration).updateUserRoles("id",roles);
    }
    @Test void deletionAndActivationUseSafeAccountStateChanges() {
        assertEquals(200,controller.deleteUserById("id").getStatusCode().value());
        verify(administration).setEnabled("id",false);
        assertEquals(204,controller.setEnabled("id",new UserControllers.ActivationRequest(true)).getStatusCode().value());
        verify(administration).setEnabled("id",true);
        assertThrows(IllegalArgumentException.class,()->controller.setEnabled("id",new UserControllers.ActivationRequest(null)));
    }
    @Test void bulkProvisioningUsesGuardedAccountService() {
        when(accounts.createUsersForSchool("Testing")).thenReturn(List.of());
        assertTrue(controller.createNewUsers("Testing").getBody().isEmpty());
        verify(accounts).createUsersForSchool("Testing");
    }
}
