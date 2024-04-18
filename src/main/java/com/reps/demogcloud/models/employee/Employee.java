package com.reps.demogcloud.models.employee;

import com.reps.demogcloud.security.models.RoleModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

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
    private Set<RoleModel> roles;
    private String school;
    //Set initial value to true
    private boolean isArchived = false;
    private String archivedBy;
    private String archivedExplanation;
    private LocalDate archivedOn;
    private Integer currency;

}
