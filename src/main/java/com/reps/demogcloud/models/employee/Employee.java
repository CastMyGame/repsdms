package com.reps.demogcloud.models.employee;

import com.reps.demogcloud.security.models.RoleModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Document(collection = "employee")
public class Employee {
    @Id
    private String employeeId;
    private String firstName;
    private String lastName;
    private String email;
    private String address;
    private RoleModel role;
    //Set initial value to true
    private boolean isArchived = false;
    private String archivedBy;
    private String archivedExplanation;
    private LocalDateTime archivedOn;

}