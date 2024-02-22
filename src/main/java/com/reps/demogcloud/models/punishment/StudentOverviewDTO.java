package com.reps.demogcloud.models.punishment;

import com.reps.demogcloud.models.student.Student;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class StudentOverviewDTO {
   private List<Punishment> punishments;

   private Student student;





}
