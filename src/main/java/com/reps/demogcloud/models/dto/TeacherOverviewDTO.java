package com.reps.demogcloud.models.dto;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TeacherOverviewDTO {
   private List<TeacherDTO> punishmentResponse;
   private List<TeacherDTO> writeUpResponse;
   private List<TeacherDTO> shoutOutsResponse;
   private Employee teacher;
   private School school;
   private List<Student> studentPopulation;
}
