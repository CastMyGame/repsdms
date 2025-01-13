package com.reps.demogcloud.models.email;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClassAnnouncementRequest {
    private String teacherEmail;
    private String className;
    private String subject;
    private String msg;
}
