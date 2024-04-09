package com.reps.demogcloud.models.dto;

import com.reps.demogcloud.models.employee.Employee;
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





}
