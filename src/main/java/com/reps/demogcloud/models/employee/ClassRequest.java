package com.reps.demogcloud.models.employee;

import com.reps.demogcloud.models.student.Student;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClassRequest {
    private Employee.ClassRoster classToUpdate;
}
