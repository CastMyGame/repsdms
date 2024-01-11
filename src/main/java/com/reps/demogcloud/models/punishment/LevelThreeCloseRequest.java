package com.reps.demogcloud.models.punishment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LevelThreeCloseRequest {

    private String infractionName;
    private String studentEmail;
    private String infractionLevel;
    private LocalDateTime timeClosed;
    private List<StudentAnswer> studentAnswer;
}

