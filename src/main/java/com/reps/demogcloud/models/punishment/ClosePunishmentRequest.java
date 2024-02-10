package com.reps.demogcloud.models.punishment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joda.time.LocalDate;


import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClosePunishmentRequest {

    private String infractionName;
    private String studentEmail;
    private LocalDate timeClosed;
    private List<StudentAnswer> studentAnswer;
}
