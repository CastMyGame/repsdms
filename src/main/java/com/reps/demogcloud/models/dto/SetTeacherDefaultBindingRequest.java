package com.reps.demogcloud.models.dto;

import lombok.Data;

@Data
public class SetTeacherDefaultBindingRequest {
    private String teacherEmail;        // later you can drop this & use SecurityContext
    private String schoolId;            // optional
    private String infractionName;
    private int level;
    private String assignmentTemplateId;
}

