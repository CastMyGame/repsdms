package com.reps.demogcloud.models.punishment;

import com.reps.demogcloud.models.employee.Employee;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TeacherOverviewDTO {
   private List<Punishment> punishments;
   private List<PunishmentDTO> writeUps;
   private List<TeacherResponse> punishmentResponse;
   private List<TeacherResponse> writeUpResponse;






}
