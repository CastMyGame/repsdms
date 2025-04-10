package com.reps.demogcloud.models.dto;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.school.School;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AdminOverviewDTO {
   private List<TeacherDTO> punishmentResponse;
   private List<TeacherDTO> writeUpResponse;
   private List<TeacherDTO> shoutOutsResponse;
   private List<Employee> teachers;
   private List<OfficeReferral> officeReferrals;
   private Employee teacher;
   private School school;





}
