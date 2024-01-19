package com.reps.demogcloud.models.employee;

import com.reps.demogcloud.models.student.Student;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeRequest {

    private Employee employee;
    private String error;
}
