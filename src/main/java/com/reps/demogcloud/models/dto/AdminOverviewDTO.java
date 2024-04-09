package com.reps.demogcloud.models.dto;

import com.reps.demogcloud.models.employee.Employee;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AdminOverviewDTO {
   private List<PunishmentDTO> punishments;
   private  List<PunishmentDTO> writeUps;
   private List<Employee> teachers;





}
