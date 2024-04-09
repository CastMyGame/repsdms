package com.reps.demogcloud.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TeacherOverviewDTO {
   private List<TeacherDTO> punishmentResponse;
   private List<TeacherDTO> writeUpResponse;
   private List<TeacherDTO> shoutOutsResponse;
}
