package com.reps.demogcloud.models.punishment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joda.time.DateTime;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CloseFailureToComplete {

    private String infractionName;
    private String studentEmail;
    private String teacherEmail;
    private LocalDateTime timeClosed;
}