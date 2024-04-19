package com.reps.demogcloud.models.dto;

import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class StudentOverviewDTO {
   private List<Punishment> punishments;
   private School school;
   private Student student;





}
