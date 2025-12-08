package com.reps.demogcloud.models.dto;

import lombok.Data;

@Data
public class ClearTeacherDefaultBindingRequest {
    private String teacherEmail;
    private String infractionName;
    private int level;
}

