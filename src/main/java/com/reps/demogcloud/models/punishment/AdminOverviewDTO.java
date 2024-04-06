package com.reps.demogcloud.models.punishment;

import com.reps.demogcloud.models.employee.Employee;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.util.List;

@Data
@AllArgsConstructor
public class AdminOverviewDTO {
   private List<PunishmentDTO> punishments;
   private  List<PunishmentDTO> writeUps;
   private List<Employee> teachers;





}
