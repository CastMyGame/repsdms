package com.reps.demogcloud.models.punishment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;


import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClosePunishmentRequest {

    private String infractionName;
    private String studentEmail;
    private List<StudentAnswer> studentAnswer;
}
