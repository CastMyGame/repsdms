package com.reps.demogcloud.models.punishment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CloseFailureToComplete {

    private String infractionName;
    private String studentEmail;
    private String teacherEmail;
    private LocalDate timeClosed;
}