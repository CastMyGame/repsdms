package com.reps.demogcloud.models.punishment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LevelThreeCloseRequest {

    private String infractionName;
    private String studentEmail;
    private String infractionLevel;
    private LocalDate timeClosed;
    private List<StudentAnswer> studentAnswer;
}

