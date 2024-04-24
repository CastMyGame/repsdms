package com.reps.demogcloud.models.dto;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.school.School;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AdminOverviewDTO {
   private List<TeacherDTO> punishments;
   private List<TeacherDTO> writeUps;
   private List<TeacherDTO> shoutOuts;
   private List<Employee> teachers;
   private Employee teacher;
   private School school;





}
