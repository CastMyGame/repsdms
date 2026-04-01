package com.reps.demogcloud.models.dto;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.school.School;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TeacherOverviewDTO {
   private List<TeacherDTO> punishmentResponse;
   private List<TeacherDTO> writeUpResponse;
   private List<TeacherDTO> shoutOutsResponse;
   private List<TeacherDTO> officeReferrals;
   private Employee teacher;
   private School school;
}
